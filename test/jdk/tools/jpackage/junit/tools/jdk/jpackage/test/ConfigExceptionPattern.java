/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jpackage.test;

import static jdk.jpackage.test.ExceptionPattern.printNullableProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.model.ConfigException;


public interface ConfigExceptionPattern extends ExceptionPattern {

    Optional<Optional<CannedArgument>> advice();

    default boolean match(Exception ex, Runnable mismatchCallback) {
        Objects.requireNonNull(ex);

        Function<Boolean, Boolean> exit = result -> {
            if (!result) {
                mismatchCallback.run();
            }
            return result;
        };

        if (ex instanceof ConfigException cex) {
            if (!advice().map(advice -> {
                return exit.apply(Objects.equals(advice.map(CannedArgument::getValue).orElse(null), cex.getAdvice()));
            }).orElse(true)) {
                return false;
            } else {
                return ExceptionPattern.super.match(ex, mismatchCallback);
            }
        } else {
            return exit.apply(false);
        }
    }

    default ConfigExceptionPattern resolveCannedArguments(Function<CannedArgument, String> mapper) {
        Objects.requireNonNull(mapper);

        var builder = build().initFrom(ExceptionPattern.super.resolveCannedArguments(mapper));

        advice().ifPresent(advice -> {
            advice.map(mapper::apply).map(builder::expectAdvice);
        });

        return builder.create();
    }

    default ConfigExceptionPattern copyWithAdvice(String advice) {
        return build().initFrom(this).expectAdvice(advice).create();
    }

    public static Builder build() {
        return new Builder();
    }

    public static class Builder extends ExceptionPattern.Builder {

        public Builder initFrom(ExceptionPattern pattern) {
            super.initFrom(pattern);
            if (pattern instanceof ConfigExceptionPattern cexPattern) {
                cexPattern.advice().ifPresentOrElse(v -> {
                    advice = v;
                }, this::skipAdviceCheck);
            }
            return this;
        }

        final public Builder expectAdvice(String v) {
            return expectAdvice(CannedArgument.ofString(v));
        }

        final public Builder expectAdvice(CannedArgument v) {
            advice = Optional.ofNullable(v);
            return this;
        }

        final public Builder expectNullAdvice() {
            advice = Optional.empty();
            return this;
        }

        final public Builder skipAdviceCheck() {
            advice = null;
            return this;
        }

        public ConfigExceptionPattern create() {
            var pattern = super.create();
            return new Stub(pattern.message(), Optional.ofNullable(advice), pattern.cause(), pattern.type());
        }

        private Optional<CannedArgument> advice;
    }

    record Stub(
            Optional<Optional<CannedArgument>> message,
            Optional<Optional<CannedArgument>> advice,
            Optional<Optional<ExceptionPattern>> cause,
            Optional<Class<? extends Exception>> type) implements ConfigExceptionPattern {

        public Stub {
            Objects.requireNonNull(message);
            Objects.requireNonNull(advice);
            Objects.requireNonNull(cause);
            Objects.requireNonNull(type);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            printNullableProperty(message, Optional.of("message"), sb);
            printNullableProperty(advice, Optional.of("advice"), sb);
            printNullableProperty(cause, Optional.of("cause"), sb);
            printNullableProperty(Optional.of(type), Optional.empty(), sb);
            return sb.toString();
        }
    }
}
