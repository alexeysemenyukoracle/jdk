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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

@FunctionalInterface
interface Validator<T, U extends Exception> {

    List<U> validate(OptionName optionName, ParsedValue<T> optionValue);

    /**
     * Thrown to indicate that the given value didn't pass validation.
     */
    final static class ValidatingConsumerException extends RuntimeException {

        ValidatingConsumerException(String msg, Throwable cause) {
            super(msg, cause);
        }

        ValidatingConsumerException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }


    /**
     * Thrown to indicate an error in the normal execution of a validator.
     */
    final static class ValidatorException extends RuntimeException {

        ValidatorException(String msg, Throwable cause) {
            super(msg, cause);
        }

        ValidatorException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }


    interface ParsedValue<T> {
        String sourceString();
        T value();
    }


    static <T, U extends Exception> Builder<T, U> build() {
        return new Builder<>();
    }

    static <T, U extends Exception> Builder<T, U> build(Class<T> valueType, OptionValueExceptionFactory<U> exceptionFactory) {
        final Builder<T, U> builder = build();
        return builder.exceptionFactory(exceptionFactory);
    }


    static final class Builder<T, U extends Exception> {

        Validator<T, U> create() {
            return new Details.ScalarValidator<>(Optional.ofNullable(predicate), Optional.ofNullable(consumer), formatString, exceptionFactory);
        }

        Validator<T[], U> createArray() {
            return new Details.ArrayValidator<>(create());
        }

        Builder<T, U> predicate(Predicate<T> v) {
            consumer = null;
            predicate = v;
            return this;
        }

        Builder<T, U> consumer(Consumer<T> v) {
            predicate = null;
            consumer = v;
            return this;
        }

        Builder<T, U> formatString(String v) {
            formatString = v;
            return this;
        }

        Builder<T, U> exceptionFactory(OptionValueExceptionFactory<? extends U> v) {
            exceptionFactory = v;
            return this;
        }

        Optional<Predicate<T>> predicate() {
            return Optional.ofNullable(predicate);
        }

        Optional<Consumer<T>> consumer() {
            return Optional.ofNullable(consumer);
        }

        boolean hasValidatingMethod() {
            return predicate().isPresent() || consumer().isPresent();
        }

        Optional<String> formatString() {
            return Optional.ofNullable(formatString);
        }

        Optional<OptionValueExceptionFactory<? extends U>> exceptionFactory() {
            return Optional.ofNullable(exceptionFactory);
        }

        private Predicate<T> predicate;
        private Consumer<T> consumer;
        private String formatString;
        private OptionValueExceptionFactory<? extends U> exceptionFactory;
    }


    final static class Details {
        private record ScalarValidator<T, U extends Exception>(Optional<Predicate<T>> predicate, Optional<Consumer<T>> consumer,
                String formatString, OptionValueExceptionFactory<? extends U> exceptionFactory) implements Validator<T, U> {

            ScalarValidator {
                Objects.requireNonNull(predicate);
                Objects.requireNonNull(consumer);
                if (predicate.isEmpty() == consumer.isEmpty()) {
                    throw new IllegalArgumentException("Either consumer or predicate must be non-empty");
                }
                Objects.requireNonNull(formatString);
                Objects.requireNonNull(exceptionFactory);
            }

            @Override
            public List<U> validate(OptionName optionName, ParsedValue<T> optionValue) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);

                try {
                    return predicate.map(validator -> {
                        if (validator.test(optionValue.value())) {
                            return List.<U>of();
                        } else {
                            return List.of((U)exceptionFactory.create(optionName, optionValue.sourceString(), formatString));
                        }
                    }).or(() -> {
                        return consumer.map(validator -> {
                            try {
                                validator.accept(optionValue.value());
                                return List.of();
                            } catch (ValidatingConsumerException ex) {
                                return List.of((U)exceptionFactory.create(optionName, optionValue.sourceString(), formatString, ex.getCause()));
                            }
                        });
                    }).orElseThrow();
                } catch (ValidatorException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new ValidatorException(ex);
                }
            }
        }


        private record ArrayValidator<T, U extends Exception>(Validator<T, U> elementValidator) implements Validator<T[], U> {
            ArrayValidator {
                Objects.requireNonNull(elementValidator);
            }

            @Override
            public List<U> validate(OptionName optionName, ParsedValue<T[]> optionValue) {
                return Stream.of(optionValue.value()).map(v -> {
                    return elementValidator.validate(optionName, new ParsedValue<>() {

                        @Override
                        public String sourceString() {
                            return optionValue.sourceString();
                        }

                        @Override
                        public T value() {
                            return v;
                        }

                    });
                }).flatMap(Collection::stream).toList();
            }
        }
    }
}

