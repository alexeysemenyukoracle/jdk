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

import static java.util.stream.Collectors.toMap;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.OptionParser;

/**
 * Builds jopt simple command line parser.
 */
public final class JOptSimpleBuilder {

    public static Function<List<String>, Options> createParser() {
        return createParser(StandardOptionValue.options());
    }

    static Function<List<String>, Options> createParser(Iterable<Option> options) {
        // No abbreviations!
        // Otherwise for the configured option "foo" it will recognize "f" as its abbreviation.
        final var parser = new OptionParser(false);
        final var optionMap = initParser(parser, StreamSupport.stream(options.spliterator(), false));

        return args -> {
            final var optionSet = parser.parse(args.toArray(String[]::new));

            return new Options() {
                @Override
                public Optional<Object> find(OptionIdentifier id) {
                    final var joptOptionSpecs = optionMap.get(id);
                    if (joptOptionSpecs == null) {
                        return Optional.empty();
                    }

                    final var cliOptionSpec = ((Option)id).getSpec();

                    final List<Object> values = new ArrayList<>();
                    for (final var joptOptionSpec : joptOptionSpecs) {
                        values.addAll(optionSet.valuesOf(joptOptionSpec));
                    }

                    if (!values.isEmpty()) {
                        return getOptionValue(values, cliOptionSpec);
                    } else {
                        for (final var joptOptionSpec : joptOptionSpecs) {
                            if (optionSet.has(joptOptionSpec)) {
                                return Optional.of(OPTON_PRESENT);
                            }
                        }
                        return Optional.empty();
                    }
                }

                @Override
                public boolean contains(String optionName) {
                    return optionSet.has(optionName);
                }
            };
        };
    }

    private static Map<Option, List<? extends jdk.internal.joptsimple.OptionSpec<?>>> initParser(OptionParser parser, Stream<Option> options) {
        Objects.requireNonNull(parser);
        final var optionSpecApplier = new OptionSpecApplier().optionSpecForShortName(true);
        return options.collect(toMap(x -> x, option -> {
            return optionSpecApplier.applyToParser(parser, option.getSpec());
        }));
    }

    private final static class OptionSpecApplier {

        @SuppressWarnings("unchecked")
        <T> List<jdk.internal.joptsimple.OptionSpec<T>> applyToParser(OptionParser parser, OptionSpec<T> spec) {
            if (optionSpecForShortName) {
                return spec.generateForEveryName().map(individualSpec -> {
                    return (jdk.internal.joptsimple.OptionSpec<T>)applyToParserInternal(parser, individualSpec);
                }).toList();
            } else {
                return List.of((jdk.internal.joptsimple.OptionSpec<T>)applyToParserInternal(parser, spec));
            }
        }

        private jdk.internal.joptsimple.OptionSpec<?> applyToParserInternal(OptionParser parser, OptionSpec<?> spec) {
            final var specBuilder = parser.acceptsAll(spec.names());
            if (spec.withValue()) {
                final var argBuilder = specBuilder.withRequiredArg();
                final var joptOptionSpec = spec.valueConverterAndValidator().map(JOptSimpleBuilder::conv);
                if (joptOptionSpec.isPresent()) {
                    return argBuilder.withValuesConvertedBy(joptOptionSpec.orElseThrow());
                } else {
                    return argBuilder;
                }
            } else {
                return specBuilder;
            }
        }

        OptionSpecApplier optionSpecForShortName(boolean v) {
            optionSpecForShortName = v;
            return this;
        }

        private boolean optionSpecForShortName;
    }

    private static <T> jdk.internal.joptsimple.ValueConverter<T> conv(ValueConverter<T> from) {
        return new jdk.internal.joptsimple.ValueConverter<>() {
            @Override
            public T convert(String value) {
                return from.convert(value);
            }

            @Override
            public Class<? extends T> valueType() {
                return from.valueType();
            }

            @Override
            public String valuePattern() {
                return null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> getOptionValue(List<?> values, OptionSpec<?> spec) {
        Objects.requireNonNull(spec);
        if(values.isEmpty()) {
            return Optional.empty();
        } else if (values.size() == 1) {
            return Optional.of(values.get(0));
        } else {
            switch (spec.mergePolicy()) {
                case USE_LAST -> {
                    return Optional.of(values.getLast());
                }
                case USE_FIRST -> {
                    return Optional.of(values.getFirst());
                }
                case CONCATENATE -> {
                    return Optional.of(concatArrays((List<Object[]>)values));
                }
            }
            throw new IllegalStateException();
        }
    }

    private static Object concatArrays(List<Object[]> arrays) {
        int length = 0;
        for (final var arr : arrays) {
            length += arr.length;
        }

        final var result = Array.newInstance(arrays.getFirst().getClass().getComponentType(), length);
        int idx = 0;
        for (final var arr : arrays) {
            System.arraycopy(arr, 0, result, idx, arr.length);
            idx += arr.length;
        }

        return result;
    }

    private final static Object OPTON_PRESENT = new Object();
}
