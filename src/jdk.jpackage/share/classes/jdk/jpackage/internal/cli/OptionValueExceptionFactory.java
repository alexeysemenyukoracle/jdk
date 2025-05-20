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
import jdk.jpackage.internal.util.LocalizedExceptionBuilder;

interface OptionValueExceptionFactory<T extends Exception> {

    T create(OptionName optionName, String optionValue, String msgId);

    T create(OptionName optionName, String optionValue, String msgId, Throwable cause);

    static <U extends Exception> OptionValueExceptionFactory<U> create(BiFunction<String, Throwable, U> ctor) {
        return create(ctor, StandardArgumentsMapper.NAME_AND_VALUE);
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

    static <T extends Exception> OptionValueExceptionFactory<T> create(BiFunction<String, Throwable, T> ctor, ArgumentsMapper formatArgumentsTransformer) {
        Objects.requireNonNull(ctor);
        Objects.requireNonNull(formatArgumentsTransformer);

        return new OptionValueExceptionFactory<>() {

            @Override
            public T create(OptionName optionName, String optionValue, String msgId) {
                return createMessage(optionName, optionValue, msgId).create(ctor);
            }

            @Override
            public T create(OptionName optionName, String optionValue, String msgId, Throwable cause) {
                return createMessage(optionName, optionValue, msgId).cause(cause).create(ctor);
            }

            private LocalizedExceptionBuilder<?> createMessage(OptionName optionName, String optionValue, String msgId) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);
                Objects.requireNonNull(msgId);
                return I18N.buildException().message(msgId, Stream.of(formatArgumentsTransformer.apply(optionName.formatForCommandLine(), optionValue)).toArray());
            }
        };
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
            return OptionValueExceptionFactory.create(ctor, Optional.ofNullable(formatArgumentsTransformer).orElse(StandardArgumentsMapper.NAME_AND_VALUE));
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
    }
}
