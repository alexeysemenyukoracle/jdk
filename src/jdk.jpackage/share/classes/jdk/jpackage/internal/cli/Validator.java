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

record Validator<T, U extends Exception>(Optional<ValidatingPredicate<T>> predicate, Optional<ValidatingConsumer<T>> consumer,
        String msgId, OptionValueExceptionFactory<? extends U> exceptionFactory) {

    interface ValidatingMethod {
    }

    @FunctionalInterface
    interface ValidatingConsumer<T> extends ValidatingMethod {
        void accept(T v) throws ValidatingConsumerException;
    }

    @FunctionalInterface
    interface ValidatingPredicate<T> extends ValidatingMethod {
        boolean test(T v);
    }


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
     * Thrown to indicate an error in the normal execution of the validator.
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


    Validator {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(consumer);
        if (predicate.isEmpty() == consumer.isEmpty()) {
            throw new IllegalArgumentException("Either consumer or predicate must be non-empty");
        }
        Objects.requireNonNull(msgId);
        Objects.requireNonNull(exceptionFactory);
    }

    Optional<U> validate(OptionName optionName, ParsedValue<T> optionValue) {
        Objects.requireNonNull(optionName);
        Objects.requireNonNull(optionValue);

        try {
            return predicate.flatMap(validator -> {
                if (validator.test(optionValue.value())) {
                    return Optional.empty();
                } else {
                    return Optional.of((U)exceptionFactory.create(optionName, optionValue.sourceString(), msgId));
                }
            }).or(() -> {
                return consumer.flatMap(validator -> {
                    try {
                        validator.accept(optionValue.value());
                        return Optional.empty();
                    } catch (ValidatingConsumerException ex) {
                        return Optional.of((U)exceptionFactory.create(optionName, optionValue.sourceString(), msgId, ex.getCause()));
                    }
                });
            });
        } catch (ValidatorException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidatorException(ex);
        }
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
            return new Validator<>(Optional.ofNullable(predicate), Optional.ofNullable(consumer), msgId, exceptionFactory);
        }

        Builder<T, U> predicate(ValidatingPredicate<T> v) {
            consumer = null;
            predicate = v;
            return this;
        }

        Builder<T, U> consumer(ValidatingConsumer<T> v) {
            predicate = null;
            consumer = v;
            return this;
        }

        Builder<T, U> msgId(String v) {
            msgId = v;
            return this;
        }

        Builder<T, U> exceptionFactory(OptionValueExceptionFactory<? extends U> v) {
            exceptionFactory = v;
            return this;
        }

        Optional<ValidatingPredicate<T>> predicate() {
            return Optional.ofNullable(predicate);
        }

        Optional<ValidatingConsumer<T>> consumer() {
            return Optional.ofNullable(consumer);
        }

        Optional<ValidatingMethod> method() {
            return predicate().map(ValidatingMethod.class::cast).or(this::consumer);
        }

        Optional<String> msgId() {
            return Optional.ofNullable(msgId);
        }

        Optional<OptionValueExceptionFactory<? extends U>> exceptionFactory() {
            return Optional.ofNullable(exceptionFactory);
        }

        private ValidatingPredicate<T> predicate;
        private ValidatingConsumer<T> consumer;
        private String msgId;
        private OptionValueExceptionFactory<? extends U> exceptionFactory;
    }
}
