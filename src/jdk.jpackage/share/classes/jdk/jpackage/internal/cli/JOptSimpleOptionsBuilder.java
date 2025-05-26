/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jpackage.internal.cli;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;

import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
import jdk.jpackage.internal.util.Result;


/**
 * Builds instanced of {@link Options} interface backed with joptsimple command
 * line parser.
 *
 * Two types of command line argument processing are supported:
 * <ol>
 * <li>Parse command line. Parsed data is stored as a map of strings.
 * <li>Convert strings to objects. Parsed data is stored as a map of objects.
 * </ol>
 */
final class JOptSimpleOptionsBuilder {

    Function<String[], Result<OptionsBuilder>> create() {
        return createJOptSimpleParser()::parse;
    }

    void printHelp(Writer sink) {
        toRunnable(() -> createJOptSimpleParser().parser().printHelpOn(sink)).run();
    }

    JOptSimpleOptionsBuilder helpOption(Option v) {
        helpOption = v;
        return this;
    }

    JOptSimpleOptionsBuilder helpOption(OptionValue<?> v) {
        return helpOption(v.asOption().orElseThrow());
    }

    JOptSimpleOptionsBuilder options(Collection<Option> v) {
        options = v;
        return this;
    }

    JOptSimpleOptionsBuilder options(Option... v) {
        return options(List.of(v));
    }

    JOptSimpleOptionsBuilder optionValues(Collection<OptionValue<?>> v) {
        return options(Optional.ofNullable(v).map(x -> {
            return x.stream().map(OptionValue::asOption).map(Optional::orElseThrow).toList();
        }).orElse((List<Option>)null));
    }

    JOptSimpleOptionsBuilder optionValues(OptionValue<?>... v) {
        return optionValues(List.of(v));
    }

    JOptSimpleOptionsBuilder unrecognizedOptionHandler(Function<String, ? extends Exception> v) {
        unrecognizedOptionHandler = v;
        return this;
    }

    private JOptSimpleParser createJOptSimpleParser() {
        return JOptSimpleParser.create(options(), Optional.ofNullable(helpOption),
                Optional.ofNullable(unrecognizedOptionHandler));
    }

    private Collection<Option> options() {
        return Optional.ofNullable(options).orElseGet(List::of);
    }


    final static class ConvertedOptionsBuilder {

        private ConvertedOptionsBuilder(TypedOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        Options create() {
            return Optional.ofNullable(excludes).map(options::excludeOptions).orElse(options);
        }

        ConvertedOptionsBuilder excludes(Collection<Option> v) {
            excludes = v;
            return this;
        }

        private Collection<Option> excludes;
        private final TypedOptions options;
    }


    final static class OptionsBuilder {

        private OptionsBuilder(UntypedOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        Options create() {
            return Optional.ofNullable(excludes).map(options::excludeOptions).orElse(options);
        }

        Set<Option> knownOptions() {
            return options.knownOptions();
        }

        Result<ConvertedOptionsBuilder> convertedOptions() {
            return options.toTypedOptions().map(ConvertedOptionsBuilder::new);
        }

        OptionsBuilder excludes(Collection<Option> v) {
            excludes = v;
            return this;
        }

        private Collection<Option> excludes;
        private final UntypedOptions options;
    }


    private record JOptSimpleParser(OptionParser parser, Map<Option, List<? extends OptionSpec<?>>> optionMap,
            Optional<Function<String, ? extends Exception>> unrecognizedOptionHandler) {
        private JOptSimpleParser {
            Objects.requireNonNull(parser);
            Objects.requireNonNull(optionMap);
            Objects.requireNonNull(unrecognizedOptionHandler);
        }

        Result<OptionsBuilder> parse(String... args) {
            return applyParser(parser, args).map(optionSet -> {
                final OptionSet mergerOptionSet;
                if (optionMap.values().stream().allMatch(list -> list.size() == 1)) {
                    // No specs with multiple names, merger not needed.
                    mergerOptionSet = optionSet;
                } else {
                    final var parser = createOptionParser();
                    final var optionSpecApplier = new OptionSpecApplier();
                    for (final var option : optionMap.keySet()) {
                        optionSpecApplier.applyToParser(parser, option.getSpec());
                    }

                    mergerOptionSet = parser.parse(args);
                }
                return new OptionsBuilder(new UntypedOptions(optionSet, mergerOptionSet, optionMap));
            });
        }

        static JOptSimpleParser create(Iterable<Option> options, Optional<Option> helpOption,
                Optional<Function<String, ? extends Exception>> unrecognizedOptionHandler) {
            final var parser = createOptionParser();

            // Create joptsimple option specs for distinct option names,
            // i.e., individual joptsimple option spec for every name of jpackage option spec.
            // This is needed to accurately detect whan options names was passed.
            final var optionSpecApplier = new OptionSpecApplier().generateForEveryName(true);

            final Map<Option, List<? extends OptionSpec<?>>> optionMap = StreamSupport.stream(options.spliterator(), false)
                    .filter(o -> {
                        // Filter out help option if any, it will be applied separately.
                        return helpOption.map(o::equals).orElse(true);
                    }).collect(toMap(x -> x, option -> {
                        return optionSpecApplier.applyToParser(parser, option.getSpec());
                    }));

            helpOption.ifPresent(ho -> {
                parser.acceptsAll(ho.getSpec().names().stream().map(OptionName::name).toList()).forHelp();
            });

            return new JOptSimpleParser(parser, optionMap, unrecognizedOptionHandler);
        }

        private Result<OptionSet> applyParser(OptionParser parser, String[] args) {
            try {
                return Result.ofValue(parser.parse(args));
            } catch (jdk.internal.joptsimple.OptionException ex) {
                if (isUnrecognizedOptionException(ex)) {
                    final var unrecognizedOptionName = ex.options().getFirst();
                    return Result.ofError(unrecognizedOptionHandler.map(h -> {
                        return (Exception)h.apply(unrecognizedOptionName);
                    }).orElse(ex));
                } else {
                    return Result.ofError(ex);
                }
            }
        }

        private static OptionParser createOptionParser() {
            // No abbreviations!
            // Otherwise for the configured option "foo" it will recognize "f" as its abbreviation.
            return new OptionParser(false);
        }
    }


    private final static class OptionSpecApplier {

        <T> List<OptionSpec<T>> applyToParser(OptionParser parser, OptionSpec<T> spec) {
            final Stream<OptionSpec<T>> optionSpecs;
            if (generateForEveryName) {
                optionSpecs = spec.copyForEveryName();
            } else {
                optionSpecs = Stream.of(spec);
            }
            return optionSpecs.peek(v -> {
                final var specBuilder = parser.acceptsAll(v.names().stream().map(OptionName::name).toList());
                if (v.hasValue()) {
                    final var builder = specBuilder.withRequiredArg();
                    spec.valuePattern().map(ValuePatternAdapter::new).map(builder::withValuesConvertedBy);
                }
            }).toList();
        }

        OptionSpecApplier generateForEveryName(boolean v) {
            generateForEveryName = v;
            return this;
        }


        private record ValuePatternAdapter(String valuePattern) implements jdk.internal.joptsimple.ValueConverter<String> {

            @Override
            public String convert(String value) {
                return value;
            }

            @Override
            public Class<? extends String> valueType() {
                return String.class;
            }
        }


        private boolean generateForEveryName;
    }


    private final static class UntypedOptions implements Options {

        UntypedOptions(OptionSet optionSet, OptionSet mergerOptionSet, Map<Option, List<? extends OptionSpec<?>>> optionMap) {
            this.optionSet = Objects.requireNonNull(optionSet);
            this.mergerOptionSet = Objects.requireNonNull(mergerOptionSet);
            optionNames = optionMap.keySet().stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).filter(optionName -> {
                return optionSet.has(optionName.name());
            }).collect(toSet());
            this.optionMap = optionMap.entrySet().stream().filter(e -> {
                return !Collections.disjoint(optionNames, e.getKey().getSpec().names());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            assertNoUnexpectedOptionNames(optionMap, optionNames);
        }

        UntypedOptions(UntypedOptions other, Collection<Option> excludes) {
            this(other.optionSet, other.mergerOptionSet, other.optionMap.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        UntypedOptions excludeOptions(Collection<Option> excludes) {
            return new UntypedOptions(this, excludes);
        }

        Set<Option> knownOptions() {
            return optionMap.keySet();
        }

        Result<TypedOptions> toTypedOptions() {
            final List<Exception> errors = new ArrayList<>();
            final Map<Option, Object> value = new HashMap<>();

            for (final var e : optionMap.entrySet()) {
                final var option = e.getKey();
                final var mainSpec = option.getSpec();
                if (mainSpec.hasValue()) {
                    final var result = convertValues(mainSpec, optionSet, getValues(mainSpec));
                    result.value().ifPresent(v -> {
                        value.put(option, mergeValues(mainSpec, v));
                    });
                    errors.addAll(result.errors());
                } else {
                    value.put(option, Boolean.TRUE);
                }
            }

            if (errors.isEmpty()) {
                return Result.ofValue(new TypedOptions(value, optionNames));
            } else {
                return Result.ofErrors(errors);
            }
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return findValues(id).map(value -> {
                final var option = (Option)id;
                final var optionSpec = option.getSpec();
                if (optionSpec.hasValue()) {
                    return value.stream().collect(joining("\0"));
                } else {
                    return Boolean.TRUE.toString();
                }
            });
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private Optional<List<String>> findValues(OptionIdentifier id) {
            Objects.requireNonNull(id);
            if (optionMap.containsKey(id)) {
                return Optional.of(getValues(((Option)id).getSpec()));
            } else {
                return Optional.empty();
            }
        }

        private List<String> getValues(OptionSpec<?> mainSpec) {
            Objects.requireNonNull(mainSpec);

            final var values = optionValues(mergerOptionSet, mainSpec.name());

            if (!values.isEmpty()) {
                return getOptionValue(values, mainSpec.mergePolicy());
            } else if (mainSpec.names().stream().anyMatch(this::contains)) {
                // "id" references an option without a value and it was recognized on the command line.
                return List.of();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private static <T> Result<List<T>> convertValues(OptionSpec<T> optionSpec,
                OptionSet optionSet, List<String> orderedStringValues) {
            Objects.requireNonNull(optionSet);
            Objects.requireNonNull(orderedStringValues);

            final var orderedOptionValues = optionSpec.names().stream().map(optionName -> {
                final var values = optionValues(optionSet, optionName);
                return values.stream().map(value -> {
                    return new IndexedStringOptionValue(optionName, value, orderedStringValues.indexOf(value));
                });
            }).flatMap(x -> x).filter(indexedValue -> {
                return indexedValue.index() >= 0;
            }).sorted(Comparator.comparingInt(IndexedStringOptionValue::index));

            final var converter = optionSpec.converter().orElseThrow();
            final var arrConverter = optionSpec.arrayValueConverter().orElse(null);

            return orderedOptionValues.map(indexedValue -> {
                final var optionName = indexedValue.optionName();

                final Result<T> conversionResult = converter.convert(optionName, StringToken.of(indexedValue.optionValue()));

                if (conversionResult.hasErrors()) {
                    if (arrConverter != null && optionSpec.mergePolicy() != MergePolicy.CONCATENATE) {
                        // Maybe recoverable array conversion error
                        final var tokens = arrConverter.tokenize(indexedValue.optionValue());
                        final String str = getOptionValue(List.of(tokens), optionSpec.mergePolicy()).getFirst();
                        final String[] token = arrConverter.tokenize(str);
                        if (token.length == 1 && str.equals(token[0])) {
                            final var singleTokenConversionResult = converter.convert(optionName, StringToken.of(str));
                            if (singleTokenConversionResult.hasValue()) {
                                return singleTokenConversionResult;
                            }
                        }
                    }
                }

                return conversionResult;
            }).map(r -> r.map(List::of)).reduce((a, b) -> {
                if (a.hasValue() && b.hasValue()) {
                    // Merge values as they both present.
                    return Result.ofValue(Stream.of(a, b).map(Result::orElseThrow).flatMap(List::stream).toList());
                } else {
                    // Merge errors, ignore the value if any.
                    return Result.ofErrors(Stream.of(a, b).map(Result::errors).flatMap(Collection::stream).toList());
                }
            }).orElseThrow();
        }

        private static Object mergeValues(OptionSpec<?> optionSpec, List<?> value) {
            if (optionSpec.arrayValueConverter().isEmpty()) {
                return getOptionValue(value, optionSpec.mergePolicy()).getFirst();
            } else {
                switch (optionSpec.mergePolicy()) {
                    case USE_FIRST -> {
                        // Find the first non-empty array, get its first element and wrap it into one-element array.
                        return value.stream().filter(arr -> {
                            return Array.getLength(arr) > 0;
                        }).findFirst().map(arr -> {
                            return asArray(Array.get(arr, 0));
                        }).orElseGet(value::getFirst);
                    }
                    case USE_LAST -> {
                        // Find the last non-empty array, get its last element and wrap it into one-element array.
                        return value.reversed().stream().filter(arr -> {
                            return Array.getLength(arr) > 0;
                        }).findFirst().map(arr -> {
                            return asArray(Array.get(arr, Array.getLength(arr) - 1));
                        }).orElseGet(value::getFirst);
                    }
                    case CONCATENATE -> {
                        return value.stream().filter(arr -> {
                            return Array.getLength(arr) > 0;
                        }).map(Object.class::cast).reduce((a, b) -> {
                            final var al = Array.getLength(a);
                            final var bl = Array.getLength(b);
                            final var arr = Array.newInstance(a.getClass().componentType(), al + bl);
                            System.arraycopy(a, 0, arr, 0, al);
                            System.arraycopy(b, 0, arr, al, bl);
                            return arr;
                        }).orElseGet(value::getFirst);
                    }
                    default -> {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        }

        private static Object asArray(Object v) {
            final var arr = Array.newInstance(v.getClass(), 1);
            Array.set(arr, 0, v);
            return arr;
        }

        @SuppressWarnings("unchecked")
        private static List<String> optionValues(OptionSet optionSet, OptionName optionName) {
            return (List<String>)optionSet.valuesOf(optionName.name());
        }

        private final OptionSet optionSet;
        private final OptionSet mergerOptionSet;
        private final Map<Option, List<? extends OptionSpec<?>>> optionMap;
        private final Set<OptionName> optionNames;
    }


    private record IndexedStringOptionValue(OptionName optionName, String optionValue, int index) {
        IndexedStringOptionValue {
            Objects.requireNonNull(optionName);
            Objects.requireNonNull(optionValue);
        }
    }


    private final static class TypedOptions implements Options {

        TypedOptions(Map<Option, Object> values, Set<OptionName> optionNames) {
            this.values = Objects.requireNonNull(values);
            this.optionNames = Objects.requireNonNull(optionNames);
            assertNoUnexpectedOptionNames(values, optionNames);
        }

        TypedOptions(TypedOptions other, Collection<Option> excludes) {
            this(other.values.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)), applyExcludes(other.optionNames, excludes));
        }

        TypedOptions excludeOptions(Collection<Option> excludes) {
            return new TypedOptions(this, excludes);
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private final Map<Option, Object> values;
        private final Set<OptionName> optionNames;
    }


    private static Set<OptionName> applyExcludes(Collection<OptionName> optionNames, Collection<Option> excludes) {
        final Set<OptionName> newOptionNames = new HashSet<>(optionNames);
        excludes.stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).forEach(newOptionNames::remove);
        return newOptionNames;
    }

    private static void assertNoUnexpectedOptionNames(Map<Option, ?> optionMap, Collection<OptionName> optionName) {
        final var allowedOptionNames = optionMap.keySet().stream()
                .map(Option::getSpec)
                .map(OptionSpec::names)
                .flatMap(Collection::stream)
                .toList();
        if (!allowedOptionNames.containsAll(optionName)) {
            final var diff = new HashSet<>(optionName);
            diff.removeAll(allowedOptionNames);
            throw new AssertionError(String.format("Unexpected option names: %s", diff.stream().map(OptionName::name).sorted().toList()));
        }
    }

    private static <T> List<T> getOptionValue(List<T> values, OptionSpec.MergePolicy mergePolicy) {
        Objects.requireNonNull(mergePolicy);
        if (values.size() == 1) {
            return values;
        } else {
            switch (mergePolicy) {
                case USE_LAST -> {
                    return List.of(values.getLast());
                }
                case USE_FIRST -> {
                    return List.of(values.getFirst());
                }
                case CONCATENATE -> {
                    return values;
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }
        }
    }

    private static boolean isUnrecognizedOptionException(jdk.internal.joptsimple.OptionException ex) {
        return JOptSimpleUnrecognizedOptionExceptionType.VALUE.isInstance(ex);
    }

    private static final class JOptSimpleUnrecognizedOptionExceptionType {
        final static Class<? extends jdk.internal.joptsimple.OptionException> VALUE;
        static {
            // joptsimple throws exceptions of package private type jdk.internal.joptsimple.UnrecognizedOptionException
            // when encounters unrecognized options.
            // Store the type of the exception it throws in this situation.
            try {
                new OptionParser(false).parse("--foo");
                throw new IllegalStateException();
            } catch (jdk.internal.joptsimple.OptionException ex) {
                VALUE = ex.getClass();
            }
        }
    }

    private Collection<Option> options;
    private Option helpOption;
    private Function<String, ? extends Exception> unrecognizedOptionHandler;
}
