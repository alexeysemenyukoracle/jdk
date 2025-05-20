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

interface OptionValueConverter<T> {

    /**
     * Converts the given string value corresponding to the given option name into a
     * Java type.
     *
     * @param optionName  the option name
     * @param optionValue the string value of the option to convert
     * @return the converted value
     */
    T convert(OptionName optionName, String optionValue);

    /**
     * Gives the class of the type of values this converter converts to.
     *
     * @return the target class for conversion
     */
    Class<? extends T> valueType();

    /**
     * Thrown to indicate an error in the normal execution of the converter.
     */
    final static class ConverterException extends RuntimeException {

        ConverterException(String msg, Throwable cause) {
            super(msg, cause);
        }

        ConverterException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    static <T> Builder<T> build() {
        return new Builder<>();
    }

    static <T> Builder<T> build(Class<T> valueType) {
        return build();
    }

    static final class Builder<T> {

        OptionValueConverter<T> create() {
            final var localConverter = Objects.requireNonNull(converter);
            final var localExceptionFactory = Objects.requireNonNull(exceptionFactory);
            final var localMsgId = Objects.requireNonNull(msgId);

            return new OptionValueConverter<>() {
                @Override
                public T convert(OptionName optionName, String optionValue) {
                    Objects.requireNonNull(optionName);
                    try {
                        return localConverter.convert(optionValue);
                    } catch (IllegalArgumentException ex) {
                        throw localExceptionFactory.create(optionName, optionValue, localMsgId, ex);
                    } catch (Exception ex) {
                        throw new ConverterException(ex);
                    }
                }

                @Override
                public Class<? extends T> valueType() {
                    return converter.valueType();
                }
            };
        }

        Builder<T> converter(ValueConverter<T> v) {
            converter = v;
            return this;
        }

        <U> Builder<U> convert(ValueConverter<U> v) {
            final Builder<U> newBuilder = build();
            msgId().ifPresent(newBuilder::msgId);
            exceptionFactory().ifPresent(newBuilder::exceptionFactory);
            return newBuilder.converter(v);
        }

        Builder<T> msgId(String v) {
            msgId = v;
            return this;
        }

        Builder<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            exceptionFactory = v;
            return this;
        }

        Optional<ValueConverter<T>> converter() {
            return Optional.ofNullable(converter);
        }

        Optional<String> msgId() {
            return Optional.ofNullable(msgId);
        }

        Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
            return Optional.ofNullable(exceptionFactory);
        }

        private ValueConverter<T> converter;
        private String msgId;
        private OptionValueExceptionFactory<? extends RuntimeException> exceptionFactory;
    }
}
