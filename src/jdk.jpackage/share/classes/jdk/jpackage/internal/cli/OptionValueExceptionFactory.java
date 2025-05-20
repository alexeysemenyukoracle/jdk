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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

interface OptionValueExceptionFactory<T extends Exception> {

    T create(OptionName optionName, String optionValue, String formatString);

    T create(OptionName optionName, String optionValue, String formatString, Throwable cause);

    static <U extends Exception> OptionValueExceptionFactory<U> create(BiFunction<String, Throwable, U> ctor) {
        return build(ctor).create();
    }

    @FunctionalInterface
    interface ArgumentsMapper {
        String[] apply(String optionName, String optionValue);
    }

    enum StandardArgumentsMapper implements ArgumentsMapper {
        NAME_AND_VALUE,
        VALUE_AND_NAME,
        VALUE;

        @Override
        public String[] apply(String optionName, String optionValue) {
            switch (this) {
                case NAME_AND_VALUE -> {
                    return new String[] { optionName, optionValue };
                }
                case VALUE_AND_NAME -> {
                    return new String[] { optionValue, optionName };
                }
                case VALUE -> {
                    return new String[] { optionValue };
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }
        }
    }

    static <T extends Exception> Builder<T> build() {
        return new Builder<>();
    }

    static <T extends Exception> Builder<T> build(BiFunction<String, Throwable, T> ctor) {
        final Builder<T> builder = build();
        return builder.ctor(ctor);
    }


    static final class Builder<T extends Exception> {

        OptionValueExceptionFactory<T> create() {
            return OptionValueExceptionFactory.create(ctor,
                    Optional.ofNullable(formatArgumentsTransformer).orElse(StandardArgumentsMapper.NAME_AND_VALUE),
                    Optional.ofNullable(messageFormatter).orElse(I18N::format));
        }

        Builder<T> messageFormatter(BiFunction<String, Object[], String> v) {
            messageFormatter = v;
            return this;
        }

        Builder<T> ctor(BiFunction<String, Throwable, T> v) {
            ctor = v;
            return this;
        }

        Builder<T> formatArgumentsTransformer(ArgumentsMapper v) {
            formatArgumentsTransformer = v;
            return this;
        }

        private BiFunction<String, Throwable, T> ctor;
        private ArgumentsMapper formatArgumentsTransformer;
        private BiFunction<String, Object[], String> messageFormatter;
    }


    private static <T extends Exception> OptionValueExceptionFactory<T> create(BiFunction<String, Throwable, T> ctor,
            ArgumentsMapper formatArgumentsTransformer, BiFunction<String, Object[], String> messageFormatter) {
        Objects.requireNonNull(ctor);
        Objects.requireNonNull(formatArgumentsTransformer);
        Objects.requireNonNull(messageFormatter);

        return new OptionValueExceptionFactory<>() {

            @Override
            public T create(OptionName optionName, String optionValue, String formatString) {
                return ctor.apply(createMessage(optionName, optionValue, formatString), null);
            }

            @Override
            public T create(OptionName optionName, String optionValue, String formatString, Throwable cause) {
                return ctor.apply(createMessage(optionName, optionValue, formatString), cause);
            }

            private String createMessage(OptionName optionName, String optionValue, String formatString) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);
                Objects.requireNonNull(formatString);
                return messageFormatter.apply(formatString,
                        Stream.of(formatArgumentsTransformer.apply(optionName.formatForCommandLine(), optionValue)).toArray());
            }
        };
    }

    static final OptionValueExceptionFactory<RuntimeException> UNREACHABLE_EXCEPTION_FACTORY = new OptionValueExceptionFactory<>() {
        @Override
        public RuntimeException create(OptionName optionName, String optionValue, String formatString) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RuntimeException create(OptionName optionName, String optionValue, String formatString, Throwable cause) {
            throw new UnsupportedOperationException();
        }
    };
}
