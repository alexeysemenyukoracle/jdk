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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
import jdk.jpackage.internal.cli.Validator.ParsedValue;
import jdk.jpackage.internal.util.Result;


/**
 * Builds instanced of {@link Options} interface backed with joptsimple command line parser.
 */
final class JOptSimpleOptionsBuilder {

    Function<String[], Result<OptionsBuilder>> create() {
        return JOptSimpleParser.create(Optional.ofNullable(options).orElseGet(List::of),
                Optional.ofNullable(unrecognizedOptionHandler))::parse;
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


    final static class ValidatedOptionsBuilder {

        private ValidatedOptionsBuilder(ValidatedOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        Options create() {
            return Optional.ofNullable(excludes).map(options::excludeOptions).orElse(options);
        }

        ValidatedOptionsBuilder excludes(Collection<Option> v) {
            excludes = v;
            return this;
        }

        private Collection<Option> excludes;
        private final ValidatedOptions options;
    }


    final static class ConvertedOptionsBuilder {

        private ConvertedOptionsBuilder(TypedOptions options) {
            this.options = Objects.requireNonNull(options);
        }

        Options create() {
            return Optional.ofNullable(excludes).map(options::excludeOptions).orElse(options);
        }

        Result<ValidatedOptionsBuilder> validatedOptions() {
            return options.toValidatedOptions().map(ValidatedOptionsBuilder::new);
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

        static JOptSimpleParser create(Iterable<Option> options, Optional<Function<String, ? extends Exception>> unrecognizedOptionHandler) {
            final var parser = createOptionParser();

            // Create joptsimple option specs for distinct option names,
            // i.e., individual joptsimple option spec for every name of jpackage option spec.
            // This is needed to accurately detect whan options names was passed.
            final var optionSpecApplier = new OptionSpecApplier().generateForEveryName(true);

            final Map<Option, List<? extends OptionSpec<?>>> optionMap = StreamSupport.stream(options.spliterator(), false).collect(toMap(x -> x, option -> {
                return optionSpecApplier.applyToParser(parser, option.getSpec());
            }));

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
                optionSpecs = spec.generateForEveryName();
            } else {
                optionSpecs = Stream.of(spec);
            }
            return optionSpecs.peek(v -> {
                final var specBuilder = parser.acceptsAll(v.names().stream().map(OptionName::name).toList());
                if (v.hasValue()) {
                    specBuilder.withRequiredArg();
                }
            }).toList();
        }

        OptionSpecApplier generateForEveryName(boolean v) {
            generateForEveryName = v;
            return this;
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

        Result<TypedOptions> toTypedOptions() {
            final List<Exception> errors = new ArrayList<>();
            final Map<Option, List<? extends OptionWithValue<?>>> value = new HashMap<>();

            for (final var e : optionMap.entrySet()) {
                final var option = e.getKey();
                final var mainSpec = option.getSpec();
                if (mainSpec.hasValue()) {
                    final var result = convertValues(mainSpec, optionSet, getValues(mainSpec).orElseThrow());
                    result.value().ifPresent(v -> {
                        value.put(option, v);
                    });
                    errors.addAll(result.errors());
                } else {
                    value.put(option, List.of());
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
                return getValues(((Option)id).getSpec());
            } else {
                return Optional.empty();
            }
        }

        private Optional<List<String>> getValues(OptionSpec<?> mainSpec) {
            Objects.requireNonNull(mainSpec);

            final var values = optionValues(mergerOptionSet, mainSpec.name());

            if (!values.isEmpty()) {
                return Optional.of(getOptionValue(values, mainSpec.mergePolicy()));
            } else if (mainSpec.names().stream().anyMatch(this::contains)) {
                // "id" references an option without a value and it was recognized on the command line.
                return Optional.of(List.of());
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private static <T> Result<List<OptionWithValue<T>>> convertValues(OptionSpec<T> optionSpec,
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

            final var converter = optionSpec.valueConverter().orElseThrow();
            final var arrConverter = optionSpec.arrayValueConverter().orElse(null);

            return orderedOptionValues.map(indexedValue -> {
                final var optionName = indexedValue.optionName();

                final var conversionResult = applyConverter(converter, optionName, indexedValue.optionValue());

                if (conversionResult.hasErrors()) {
                    if (arrConverter != null && optionSpec.mergePolicy() != MergePolicy.CONCATENATE) {
                        // Maybe recoverable array conversion error
                        final var tokens = arrConverter.tokenize(indexedValue.optionValue());
                        final String str = getOptionValue(List.of(tokens), optionSpec.mergePolicy()).getFirst();
                        final String[] token = arrConverter.tokenize(str);
                        if (token.length == 1 && str.equals(token[0])) {
                            final var singleTokenConversionResult = applyConverter(converter, optionName, str);
                            if (singleTokenConversionResult.hasValue()) {
                                final OptionWithValue<T> optionWithValue = new ArrayOptionWithValue<>(
                                        optionName, singleTokenConversionResult.orElseThrow(), indexedValue.optionValue(), token);
                                return Result.ofValue(optionWithValue);
                            }
                        }
                    }
                    return conversionResult.<OptionWithValue<T>>mapErrors();
                }

                final OptionWithValue<T> optionWithValue;

                if (arrConverter != null) {
                    final var tokens = arrConverter.tokenize(indexedValue.optionValue());
                    optionWithValue = new ArrayOptionWithValue<>(optionName, conversionResult.orElseThrow(),
                            indexedValue.optionValue(), tokens);
                } else {
                    optionWithValue = new ScalarOptionWithValue<>(optionName, conversionResult.orElseThrow(),
                            StringToken.of(indexedValue.optionValue()));
                }
                return Result.ofValue(optionWithValue);
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

        private static <T> Result<T> applyConverter(OptionValueConverter<T> converter, OptionName optionName, String str) {
            try {
                return Result.ofValue(converter.convert(optionName, StringToken.of(str)));
            } catch (OptionValueConverter.ConverterException ex) {
                // Converter internal error, bail out
                throw ex;
            } catch (RuntimeException ex) {
                return Result.ofError(ex);
            }
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


    private interface OptionWithValue<T> {
        OptionName name();
        T value();
        List<? extends Exception> validate(Validator<T, ? extends Exception> validator);

        @SuppressWarnings("unchecked")
        default List<? extends Exception> validateCastUnchecked(Validator<?, ? extends Exception> validator) {
            return validate((Validator<T, ? extends Exception>)validator);
        }
    }


    private record ScalarOptionWithValue<T>(OptionName name, T value,
            StringToken sourceToken) implements OptionWithValue<T>, ParsedValue<T> {
        ScalarOptionWithValue {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            Objects.requireNonNull(sourceToken);
        }

        @Override
        public List<? extends Exception> validate(Validator<T, ? extends Exception> validator) {
            return validator.validate(name, this).stream().toList();
        }
    }


    private record ArrayOptionWithValue<T>(OptionName name, T value, String tokenizedString,
            String[] tokens) implements OptionWithValue<T> {
        ArrayOptionWithValue {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            Objects.requireNonNull(tokenizedString);
            Objects.requireNonNull(tokens);
        }

        @Override
        public List<? extends Exception> validate(Validator<T, ? extends Exception> validator) {
            Objects.requireNonNull(validator);

            @SuppressWarnings("unchecked")
            final var buf = (T)Array.newInstance(Array.get(value, 0).getClass(), 1);

            return IntStream.range(0, Array.getLength(value)).mapToObj(i -> {
                Array.set(buf, 0, Array.get(value, i));
                return ParsedValue.create(buf, StringToken.of(tokenizedString, tokens[i]));
            }).map(parsedValue -> {
                return validator.validate(name, parsedValue);
            }).flatMap(Collection::stream).toList();
        }
    }


    private final static class TypedOptions implements Options {

        TypedOptions(Map<Option, List<? extends OptionWithValue<?>>> values, Set<OptionName> optionNames) {
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

        Result<ValidatedOptions> toValidatedOptions() {
            final List<Exception> errors = new ArrayList<>();

            for (final var e : values.entrySet()) {
                final var option = e.getKey();
                final var mainSpec = option.getSpec();
                mainSpec.valueValidator().ifPresent(validator -> {
                    e.getValue().stream().map(optionWithValue -> {
                        return optionWithValue.validateCastUnchecked(validator);
                    }).forEach(errors::addAll);
                });
            }

            if (errors.isEmpty()) {
                return Result.ofValue(new ValidatedOptions(values.keySet().stream().collect(toMap(x -> x, option -> {
                    return find(option).orElseThrow();
                })), optionNames));
            } else {
                return Result.ofErrors(errors);
            }
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return Optional.ofNullable(values.get(id)).map(value -> {
                final var option = (Option)id;
                final var optionSpec = option.getSpec();
                if (!optionSpec.hasValue()) {
                    return Boolean.TRUE;
                } else if (optionSpec.arrayValueConverter().isPresent()) {
                    switch (optionSpec.mergePolicy()) {
                        case USE_FIRST -> {
                            // Find the first non-empty array, get its first element and wrap it into one-element array.
                            return value.stream().map(OptionWithValue::value).filter(arr -> {
                                return Array.getLength(arr) > 0;
                            }).findFirst().map(arr -> {
                                return asArray(Array.get(arr, 0));
                            }).orElseGet(value.getFirst()::value);
                        }
                        case USE_LAST -> {
                            // Find the last non-empty array, get its last element and wrap it into one-element array.
                            return value.reversed().stream().map(OptionWithValue::value).filter(arr -> {
                                return Array.getLength(arr) > 0;
                            }).findFirst().map(arr -> {
                                return asArray(Array.get(arr, Array.getLength(arr) - 1));
                            }).orElseGet(value.getFirst()::value);
                        }
                        case CONCATENATE -> {
                            return value.stream().map(OptionWithValue::value).filter(arr -> {
                                return Array.getLength(arr) > 0;
                            }).map(Object.class::cast).reduce((a, b) -> {
                                final var al = Array.getLength(a);
                                final var bl = Array.getLength(b);
                                final var arr = Array.newInstance(a.getClass().componentType(), al + bl);
                                System.arraycopy(a, 0, arr, 0, al);
                                System.arraycopy(b, 0, arr, al, bl);
                                return arr;
                            }).orElseGet(value.getFirst()::value);
                        }
                        default -> {
                            throw new UnsupportedOperationException();
                        }
                    }
                } else {
                    return getOptionValue(value.stream().map(OptionWithValue::value).toList(), optionSpec.mergePolicy()).getFirst();
                }
            });
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private static Object asArray(Object v) {
            final var arr = Array.newInstance(v.getClass(), 1);
            Array.set(arr, 0, v);
            return arr;
        }

        private final Map<Option, List<? extends OptionWithValue<?>>> values;
        private final Set<OptionName> optionNames;
    }


    private final static class ValidatedOptions implements Options {

        ValidatedOptions(Map<Option, Object> values, Set<OptionName> optionNames) {
            this.values = Objects.requireNonNull(values);
            this.optionNames = Objects.requireNonNull(optionNames);
            assertNoUnexpectedOptionNames(values, optionNames);
        }

        ValidatedOptions(ValidatedOptions other, Collection<Option> excludes) {
            this(other.values.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)), applyExcludes(other.optionNames, excludes));
        }

        ValidatedOptions excludeOptions(Collection<Option> excludes) {
            return new ValidatedOptions(this, excludes);
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
    private Function<String, ? extends Exception> unrecognizedOptionHandler;
}
