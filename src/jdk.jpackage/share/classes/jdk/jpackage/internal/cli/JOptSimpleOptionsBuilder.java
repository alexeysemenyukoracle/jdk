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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import jdk.jpackage.internal.cli.Validator.ParsedValue;


/**
 * Builds instanced of {@link Options} interface backed with jopt simple command line parser.
 */
final class JOptSimpleOptionsBuilder {

    Function<String[], Result<OptionsBuilder>> create() {
        return JOptSimpleParser.create(options)::parse;
    }

    JOptSimpleOptionsBuilder options(Collection<Option> v) {
        options = v;
        return this;
    }


    record Result<T>(Optional<T> value, Optional<Collection<? extends Exception>> errors) {
        Result {
            if (value.isEmpty() == errors.isEmpty()) {
                throw new IllegalArgumentException();
            }
            errors.ifPresent(e -> {
                if (e.isEmpty()) {
                    throw new IllegalArgumentException("Errorm colection must be non-empty");
                }
            });
        }

        T orElseThrow() {
            firstError().ifPresent(ex -> {
                throw RuntimeException.class.cast(ex);
            });
            return value.orElseThrow();
        }

        boolean hasValue() {
            return value.isPresent();
        }

        <U> Result<U> map(Function<T, U> conv) {
            return new Result<>(value.map(conv), errors);
        }

        Optional<? extends Exception> firstError() {
            return errors.map(Collection::stream).flatMap(Stream::findFirst);
        }

        static <T> Result<T> ofValue(T value) {
            return new Result<>(Optional.of(value), Optional.empty());
        }

        static <T> Result<T> ofErrors(Collection<? extends Exception> errors) {
            return new Result<>(Optional.empty(), Optional.of(errors));
        }

        static <T> Result<T> ofError(Exception error) {
            return ofErrors(List.of(error));
        }
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


    private record JOptSimpleParser(OptionParser parser, Map<Option, List<? extends OptionSpec<?>>> optionMap) {
        private JOptSimpleParser {
            Objects.requireNonNull(parser);
            Objects.requireNonNull(optionMap);
        }

        Result<OptionsBuilder> parse(String... args) {
            try {
                return Result.ofValue(new OptionsBuilder(new UntypedOptions(parser.parse(args), optionMap)));
            } catch (RuntimeException ex) {
                return Result.ofError(ex);
            }
        }

        static JOptSimpleParser create(Iterable<Option> options) {
            // No abbreviations!
            // Otherwise for the configured option "foo" it will recognize "f" as its abbreviation.
            final var parser = new OptionParser(false);

            final var optionSpecApplier = new OptionSpecApplier().generateForEveryName(true);

            final Map<Option, List<? extends OptionSpec<?>>> optionMap = StreamSupport.stream(options.spliterator(), false).collect(toMap(x -> x, option -> {
                return optionSpecApplier.applyToParser(parser, option.getSpec());
            }));

            return new JOptSimpleParser(parser, optionMap);
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

        UntypedOptions(OptionSet optionSet, Map<Option, List<? extends OptionSpec<?>>> optionMap) {
            this.optionSet = Objects.requireNonNull(optionSet);
            optionNames = optionMap.keySet().stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).filter(optionName -> {
                return optionSet.has(optionName.name());
            }).collect(toSet());
            this.optionMap = optionMap.entrySet().stream().filter(e -> {
                return !Collections.disjoint(optionNames, e.getKey().getSpec().names());
            }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            assertNoUnexpectedOptionNames(optionMap, optionNames);
        }

        UntypedOptions(UntypedOptions other, Collection<Option> excludes) {
            this(other.optionSet, other.optionMap.entrySet().stream().filter(e -> {
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
                    final var result = convertValues(mainSpec, optionSet);
                    result.value().ifPresent(v -> {
                        value.put(option, v);
                    });
                    result.errors().ifPresent(errors::addAll);
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
            final var optionSpecs = optionMap.get(id);
            if (optionSpecs == null) {
                return Optional.empty();
            }

            final var mainSpec = ((Option)id).getSpec();

            final var values = mainSpec.names().stream().map(OptionName::name).map(optionSet::valuesOf).filter(Predicate.not(List::isEmpty)).map(v -> {
                return v.stream().map(String.class::cast);
            }).flatMap(x -> x).toList();

            if (!values.isEmpty()) {
                return Optional.of(getOptionValue(values, mainSpec.mergePolicy()));
            } else if (mainSpec.names().stream().anyMatch(this::contains)) {
                // "id" references an option without a value and it was recognized on the command line.
                return Optional.of(List.of());
            } else {
                return Optional.empty();
            }
        }

        private static <T> Result<List<OptionWithValue<T>>> convertValues(OptionSpec<T> optionSpec, OptionSet optionSet) {
            final var converter = optionSpec.valueConverter().orElseThrow();
            return optionSpec.names().stream().map(optioName -> {
                @SuppressWarnings("unchecked")
                final var values = (List<String>)optionSet.valuesOf(optioName.name());
                return values.stream().map(value -> {
                    try {
                        return Result.ofValue(List.of(new OptionWithValue<>(optioName, converter.convert(optioName, value), value)));
                    } catch (RuntimeException ex) {
                        return Result.<List<OptionWithValue<T>>>ofError(ex);
                    }
                });
            }).flatMap(x -> x).reduce((a, b) -> {
                if (a.hasValue() && b.hasValue()) {
                    // Merge values as they both present.
                    return Result.ofValue(Stream.of(a, b).map(Result::orElseThrow).flatMap(List::stream).toList());
                } else {
                    // Merge errors, ignore a value if any.
                    return Result.ofErrors(Stream.of(a, b).map(Result::errors).map(Optional::orElseThrow).flatMap(Collection::stream).toList());
                }
            }).orElseThrow();
        }

        private final OptionSet optionSet;
        private final Map<Option, List<? extends OptionSpec<?>>> optionMap;
        private final Set<OptionName> optionNames;
    }


    private record OptionWithValue<T>(OptionName name, T value, String sourceString) implements ParsedValue<T> {
        OptionWithValue {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            Objects.requireNonNull(sourceString);
        }

        <U extends Exception> List<U> validate(Validator<T, U> validator) {
            return validator.validate(name, this);
        }

        @SuppressWarnings("unchecked")
        <U extends Exception> List<U> validateCastUnchecked(Validator<?, U> validator) {
            return validate((Validator<T, U>)validator);
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
                } else if (optionSpec.valueType().isArray()) {
                    switch (optionSpec.mergePolicy()) {
                        case USE_LAST -> {
                            return value.getFirst().value();
                        }
                        case USE_FIRST -> {
                            return value.getLast().value();
                        }
                        case CONCATENATE -> {
                            return value.stream().map(OptionWithValue::value).map(Object.class::cast).reduce((a, b) -> {
                                final var al = Array.getLength(a);
                                final var bl = Array.getLength(b);
                                final var arr = Array.newInstance(a.getClass().componentType(), al + bl);
                                System.arraycopy(a, 0, arr, 0, al);
                                System.arraycopy(b, 0, arr, al, bl);
                                return arr;
                            }).orElseThrow();
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
        final var allowedOptionNames = optionMap.keySet().stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).toList();
        if (!allowedOptionNames.containsAll(optionName)) {
            final var diff = new HashSet<>(optionName);
            diff.removeAll(allowedOptionNames);
            throw new AssertionError(String.format("Unexpected option names: %s", diff.stream().map(OptionName::name).sorted().toList()));
        }
    }

    private static <T> List<T> getOptionValue(List<T> values, OptionSpec.MergePolicy mergePolicy) {
        Objects.requireNonNull(mergePolicy);
        if(values.isEmpty()) {
            throw new IllegalArgumentException();
        } else if (values.size() == 1) {
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

    private Collection<Option> options;
}
