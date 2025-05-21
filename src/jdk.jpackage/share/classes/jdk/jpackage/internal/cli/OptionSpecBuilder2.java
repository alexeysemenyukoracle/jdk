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

import java.io.File;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

final class OptionSpecBuilder2<T> {

    static <T> OptionSpecBuilder2<T> create(Class<? extends T> valueType) {
        return new OptionSpecBuilder2<>(valueType);
    }

    static String pathSeparator() {
        return File.pathSeparator;
    }

    static <T> Function<OptionValue.Builder<T[]>, OptionValue<List<T>>> toList() {
        return builder -> {
            return builder.to(List::of).create();
        };
    }

    private OptionSpecBuilder2(Class<? extends T> valueType) {
        this.valueType = Objects.requireNonNull(valueType);
    }


    final class ArrayOptionSpecBuilder {

        private ArrayOptionSpecBuilder(Function<String, String[]> splitter) {
            this.splitter = Objects.requireNonNull(splitter);
        }

        OptionValue<T[]> create() {
            return toOptionValueBuilder().create();
        }

        <U> OptionValue<U> create(Function<OptionValue.Builder<T[]>, OptionValue<U>> transformer) {
            return transformer.apply(toOptionValueBuilder());
        }

        OptionValue.Builder<T[]> toOptionValueBuilder() {
            final var builder = OptionValue.<T[]>build().spec(createOptionSpec());
            defaultValue().ifPresent(builder::defaultValue);
            return builder;
        }

        OptionSpec<T[]> createOptionSpec() {
            final OptionSpec.MergePolicy mergePolicy = OptionSpec.MergePolicy.CONCATENATE;
            return new OptionSpec<>(names(), createConverter(), scope, createValidator(), mergePolicy);
        }

        ArrayOptionSpecBuilder defaultValue(T[] v) {
            arrayDefaultValue = v;
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFormatString(String v) {
            OptionSpecBuilder2.this.validatorExceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFormatString(String v) {
            OptionSpecBuilder2.this.converterExceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFactory(OptionValueExceptionFactory<? extends Exception> v) {
            OptionSpecBuilder2.this.validatorExceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            OptionSpecBuilder2.this.converterExceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFormatString(String v) {
            OptionSpecBuilder2.this.exceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            OptionSpecBuilder2.this.exceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder converter(ValueConverter<T> v) {
            OptionSpecBuilder2.this.converter(v);
            return this;
        }

        ArrayOptionSpecBuilder converter(Function<String, T> v) {
            OptionSpecBuilder2.this.converter(v);
            return this;
        }

        ArrayOptionSpecBuilder validator(Predicate<T> v) {
            OptionSpecBuilder2.this.validator(v);
            return this;
        }

        ArrayOptionSpecBuilder validator(Consumer<T> v) {
            OptionSpecBuilder2.this.validator(v);
            return this;
        }

        ArrayOptionSpecBuilder validator(UnaryOperator<Validator.Builder<T, Exception>> modifier) {
            OptionSpecBuilder2.this.validator(modifier);
            return this;
        }

        ArrayOptionSpecBuilder withoutConverter() {
            OptionSpecBuilder2.this.withoutConverter();
            return this;
        }

        ArrayOptionSpecBuilder withoutValidator() {
            OptionSpecBuilder2.this.withoutValidator();
            return this;
        }

        ArrayOptionSpecBuilder name(String v) {
            OptionSpecBuilder2.this.name(v);
            return this;
        }

        ArrayOptionSpecBuilder shortName(String v) {
            OptionSpecBuilder2.this.shortName(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(OptionScope... v) {
            OptionSpecBuilder2.this.scope(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(Collection<? extends OptionScope> v) {
            OptionSpecBuilder2.this.scope(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(UnaryOperator<Set<OptionScope>> modifier) {
            OptionSpecBuilder2.this.scope(modifier);
            return this;
        }

        ArrayOptionSpecBuilder enhanceScope(OptionScope... v) {
            OptionSpecBuilder2.this.enhanceScope(v);
            return this;
        }

        ArrayOptionSpecBuilder enhanceScope(Collection<? extends OptionScope> v) {
            OptionSpecBuilder2.this.enhanceScope(v);
            return this;
        }

        private Optional<OptionValueConverter<T[]>> createConverter() {
            return converterBuilder.converter().map(c -> {
                return StandardValueConverter.toArray(c, splitter);
            }).map(converterBuilder::convert).map(OptionValueConverter.Builder::create);
        }

        private Optional<Validator<T[], ? extends Exception>> createValidator() {
            if (validatorBuilder.hasValidatingMethod()) {
                return Optional.of(validatorBuilder.createArray());
            } else {
                return Optional.empty();
            }
        }

        private Optional<T[]> defaultValue() {
            return Optional.ofNullable(arrayDefaultValue).or(() -> {
                return OptionSpecBuilder2.this.defaultValue().map(v -> {
                    @SuppressWarnings("unchecked")
                    final var arr = (T[])Array.newInstance(valueType, 1);
                    arr[0] = v;
                    return arr;
                });
            });
        }

        private T[] arrayDefaultValue;
        private final Function<String, String[]> splitter;
    }


    OptionValue<T> create() {
        return toOptionValueBuilder().create();
    }

    <U> OptionValue<U> create(Function<OptionValue.Builder<T>, OptionValue<U>> transformer) {
        return transformer.apply(toOptionValueBuilder());
    }

    OptionValue.Builder<T> toOptionValueBuilder() {
        final var builder = OptionValue.<T>build().spec(createOptionSpec());
        defaultValue().ifPresent(builder::defaultValue);
        return builder;
    }

    OptionSpec<T> createOptionSpec() {
        final OptionSpec.MergePolicy mergePolicy = OptionSpec.MergePolicy.USE_LAST;
        return new OptionSpec<>(names(), createConverter(), scope, createValidator(), mergePolicy);
    }

    ArrayOptionSpecBuilder toArray(String splitRegexp) {
        Objects.requireNonNull(splitRegexp);
        return toArray(str -> {
            return str.split(splitRegexp);
        });
    }

    ArrayOptionSpecBuilder toArray() {
        return toArray(str -> {
            return new String[] { str };
        });
    }

    ArrayOptionSpecBuilder toArray(Function<String, String[]> conv) {
        return new ArrayOptionSpecBuilder(conv);
    }

    OptionSpecBuilder2<T> validatorExceptionFormatString(String v) {
        validatorBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder2<T> converterExceptionFormatString(String v) {
        converterBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder2<T> validatorExceptionFactory(OptionValueExceptionFactory<? extends Exception> v) {
        validatorBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder2<T> converterExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        converterBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder2<T> exceptionFormatString(String v) {
        return validatorExceptionFormatString(v).converterExceptionFormatString(v);
    }

    OptionSpecBuilder2<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        return validatorExceptionFactory(v).converterExceptionFactory(v);
    }

    OptionSpecBuilder2<T> converter(ValueConverter<T> v) {
        converterBuilder.converter(v);
        return this;
    }

    OptionSpecBuilder2<T> converter(Function<String, T> v) {
        return converter(ValueConverter.create(v, valueType));
    }

    OptionSpecBuilder2<T> validator(Predicate<T> v) {
        validatorBuilder.predicate(v::test);
        return this;
    }

    OptionSpecBuilder2<T> validator(Consumer<T> v) {
        validatorBuilder.consumer(v::accept);
        return this;
    }

    OptionSpecBuilder2<T> validator(UnaryOperator<Validator.Builder<T, Exception>> modifier) {
        validatorBuilder = modifier.apply(validatorBuilder);
        return this;
    }

    OptionSpecBuilder2<T> withoutConverter() {
        converterBuilder.convert(null);
        return this;
    }

    OptionSpecBuilder2<T> withoutValidator() {
        validatorBuilder.predicate(null).consumer(null);
        return this;
    }

    OptionSpecBuilder2<T> name(String v) {
        name = v;
        return this;
    }

    OptionSpecBuilder2<T> shortName(String v) {
        shortName = v;
        return this;
    }

    OptionSpecBuilder2<T> scope(OptionScope... v) {
        return scope(Set.of(v));
    }

    OptionSpecBuilder2<T> scope(Collection<? extends OptionScope> v) {
        scope = Set.copyOf(v);
        return this;
    }

    OptionSpecBuilder2<T> scope(UnaryOperator<Set<OptionScope>> modifier) {
        return scope(modifier.apply(scope().orElseGet(Set::of)));
    }

    OptionSpecBuilder2<T> enhanceScope(OptionScope... v) {
        return enhanceScope(Set.of(v));
    }

    OptionSpecBuilder2<T> enhanceScope(Collection<? extends OptionScope> v) {
        final Set<OptionScope> newScope = new HashSet<>();
        newScope.addAll(v);
        scope().ifPresent(newScope::addAll);
        scope = newScope;
        return this;
    }

    OptionSpecBuilder2<T> defaultValue(T v) {
        defaultValue = v;
        return this;
    }

    private Optional<String> name() {
        return Optional.ofNullable(name);
    }

    private Optional<String> shortName() {
        return Optional.ofNullable(shortName);
    }

    private Optional<Set<OptionScope>> scope() {
        return Optional.ofNullable(scope);
    }

    private Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    private List<OptionName> names() {
        return Stream.of(
                Optional.of(name().orElseThrow()),
                shortName()
        ).filter(Optional::isPresent).map(Optional::orElseThrow).map(OptionName::new).toList();
    }

    private Optional<OptionValueConverter<T>> createConverter() {
        if (converterBuilder.converter().isPresent()) {
            return Optional.of(converterBuilder.create());
        } else {
            return Optional.empty();
        }
    }

    private Optional<Validator<T, ? extends Exception>> createValidator() {
        if (validatorBuilder.hasValidatingMethod()) {
            return Optional.of(validatorBuilder.create());
        } else {
            return Optional.empty();
        }
    }

    private final Class<? extends T> valueType;
    private String name;
    private String shortName;
    private Set<OptionScope> scope;
    private T defaultValue;
    private OptionValueConverter.Builder<T> converterBuilder = OptionValueConverter.build();
    private Validator.Builder<T, Exception> validatorBuilder = Validator.build();
}
