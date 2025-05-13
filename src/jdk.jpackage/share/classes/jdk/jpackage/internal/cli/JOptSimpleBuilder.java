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

    public static Function<String[], Options> createParser() {
        return createParser(StandardOptionValue.options());
    }

    static Function<String[], Options> createParser(Iterable<Option> options) {
        final var parser = new OptionParser();
        final var optionMap = initParser(parser, StreamSupport.stream(options.spliterator(), false));

        return args -> {
            final var optionSet = parser.parse(args);

            return new Options() {
                @SuppressWarnings("unchecked")
                @Override
                public <T> Optional<T> find(OptionIdentifier id) {
                    final var joptOptionSpec = Objects.requireNonNull(optionMap.get(id), "Unknown option id");
                    final var cliOptionSpec = (OptionSpec<T>)((Option)id).getSpec();
                    final List<T> values = (List<T>)optionSet.valuesOf(joptOptionSpec);
                    return getOptionValue(values, cliOptionSpec);
                }
            };
        };
    }

    private static Map<Option, jdk.internal.joptsimple.OptionSpec<?>> initParser(OptionParser parser, Stream<Option> options) {
        Objects.requireNonNull(parser);
        return options.collect(toMap(x -> x, option -> {
            return addOptionSpecToParser(parser, option.getSpec());
        }));
    }

    private static jdk.internal.joptsimple.OptionSpec<?> addOptionSpecToParser(OptionParser parser, OptionSpec<?> spec) {
        final var specBuilder = parser.accepts(spec.name());
        if (spec.withValue()) {
            final var argBuilder = specBuilder.withRequiredArg();
            spec.valueConverterAndValidator().map(JOptSimpleBuilder::conv).ifPresent(argBuilder::withValuesConvertedBy);
            return argBuilder;
        } else {
            return specBuilder;
        }
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
    private static <T> Optional<T> getOptionValue(List<T> values, OptionSpec<T> spec) {
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
                    return Optional.of((T)concatArrays((List<T[]>)values));
                }
            }
            throw new IllegalStateException();
        }
    }

    private static <T> T[] concatArrays(List<T[]> arrays) {
        int length = 0;
        for (final var arr : arrays) {
            length += arr.length;
        }

        @SuppressWarnings("unchecked")
        final var result = (T[])Array.newInstance(arrays.getFirst().getClass().getComponentType(), length);
        int idx = 0;
        for (final var arr : arrays) {
            System.arraycopy(arr, 0, result, idx, arr.length);
            idx += arr.length;
        }

        return result;
    }
}
