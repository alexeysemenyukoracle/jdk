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
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;

final class OptionSpecBuilder<T> {

    static <T> OptionSpecBuilder<T> create(Class<? extends T> valueType) {
        return new OptionSpecBuilder<>(valueType);
    }

    static String pathSeparator() {
        return File.pathSeparator;
    }

    static <T> Function<OptionValue.Builder<T[]>, OptionValue<List<T>>> toList() {
        return builder -> {
            return builder.to(List::of).create();
        };
    }

    private OptionSpecBuilder(Class<? extends T> valueType) {
        this.valueType = Objects.requireNonNull(valueType);
    }


    final class ArrayOptionSpecBuilder {

        private ArrayOptionSpecBuilder() {
        }

        OptionSpecBuilder<T> outer() {
            return OptionSpecBuilder.this;
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
            return new OptionSpec<>(names(), Optional.of(createConverter()), scope,
                    createValidator(), OptionSpecBuilder.this.mergePolicy().orElse(MergePolicy.CONCATENATE));
        }

        ArrayOptionSpecBuilder defaultValue(T[] v) {
            arrayDefaultValue = v;
            return this;
        }

        ArrayOptionSpecBuilder mutate(Consumer<OptionSpecBuilder<?>.ArrayOptionSpecBuilder> mutator) {
            mutator.accept(this);
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFormatString(String v) {
            OptionSpecBuilder.this.validatorExceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFormatString(UnaryOperator<String> mutator) {
            OptionSpecBuilder.this.validatorExceptionFormatString(mutator);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFormatString(String v) {
            OptionSpecBuilder.this.converterExceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFormatString(UnaryOperator<String> mutator) {
            OptionSpecBuilder.this.converterExceptionFormatString(mutator);
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            OptionSpecBuilder.this.validatorExceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder validatorExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
            OptionSpecBuilder.this.validatorExceptionFactory(mutator);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            OptionSpecBuilder.this.converterExceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder converterExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
            OptionSpecBuilder.this.converterExceptionFactory(mutator);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFormatString(String v) {
            OptionSpecBuilder.this.exceptionFormatString(v);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFormatString(UnaryOperator<String> mutator) {
            OptionSpecBuilder.this.exceptionFormatString(mutator);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            OptionSpecBuilder.this.exceptionFactory(v);
            return this;
        }

        ArrayOptionSpecBuilder exceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
            OptionSpecBuilder.this.exceptionFactory(mutator);
            return this;
        }

        ArrayOptionSpecBuilder converter(ValueConverter<T> v) {
            OptionSpecBuilder.this.converter(v);
            return this;
        }

        ArrayOptionSpecBuilder converter(Function<String, T> v) {
            OptionSpecBuilder.this.converter(v);
            return this;
        }

        ArrayOptionSpecBuilder validator(Predicate<T> v) {
            OptionSpecBuilder.this.validator(v);
            return this;
        }

        @SuppressWarnings("overloads")
        ArrayOptionSpecBuilder validator(Consumer<T> v) {
            OptionSpecBuilder.this.validator(v);
            return this;
        }

        @SuppressWarnings("overloads")
        ArrayOptionSpecBuilder validator(UnaryOperator<Validator.Builder<T, RuntimeException>> mutator) {
            OptionSpecBuilder.this.validator(mutator);
            return this;
        }

        ArrayOptionSpecBuilder withoutConverter() {
            OptionSpecBuilder.this.withoutConverter();
            return this;
        }

        ArrayOptionSpecBuilder withoutValidator() {
            OptionSpecBuilder.this.withoutValidator();
            return this;
        }

        ArrayOptionSpecBuilder name(String v) {
            OptionSpecBuilder.this.name(v);
            return this;
        }

        ArrayOptionSpecBuilder shortName(String v) {
            OptionSpecBuilder.this.shortName(v);
            return this;
        }

        ArrayOptionSpecBuilder mergePolicy(MergePolicy v) {
            OptionSpecBuilder.this.mergePolicy(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(OptionScope... v) {
            OptionSpecBuilder.this.scope(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(Collection<? extends OptionScope> v) {
            OptionSpecBuilder.this.scope(v);
            return this;
        }

        ArrayOptionSpecBuilder scope(UnaryOperator<Set<OptionScope>> mutator) {
            OptionSpecBuilder.this.scope(mutator);
            return this;
        }

        ArrayOptionSpecBuilder inScope(OptionScope... v) {
            OptionSpecBuilder.this.inScope(v);
            return this;
        }

        ArrayOptionSpecBuilder inScope(Collection<? extends OptionScope> v) {
            OptionSpecBuilder.this.inScope(v);
            return this;
        }

        ArrayOptionSpecBuilder outOfScope(OptionScope... v) {
            OptionSpecBuilder.this.outOfScope(v);
            return this;
        }

        ArrayOptionSpecBuilder outOfScope(Collection<? extends OptionScope> v) {
            OptionSpecBuilder.this.outOfScope(v);
            return this;
        }

        private OptionValueConverter<T[]> createConverter() {
            return converterBuilder.createArray();
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
                return OptionSpecBuilder.this.defaultValue().map(v -> {
                    @SuppressWarnings("unchecked")
                    final var arr = (T[])Array.newInstance(valueType, 1);
                    arr[0] = v;
                    return arr;
                });
            });
        }

        private T[] arrayDefaultValue;
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
        return new OptionSpec<>(names(), createConverter(), scope, createValidator(),
                mergePolicy().orElse(MergePolicy.USE_LAST));
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

    ArrayOptionSpecBuilder toArray(Function<String, String[]> tokenizer) {
        converterBuilder.tokenizer(Objects.requireNonNull(tokenizer));
        return new ArrayOptionSpecBuilder();
    }

    OptionSpecBuilder<T> mutate(Consumer<OptionSpecBuilder<?>> mutator) {
        mutator.accept(this);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFormatString(String v) {
        validatorBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFormatString(UnaryOperator<String> mutator) {
        validatorBuilder.formatString(mutator.apply(validatorBuilder.formatString().orElse(null)));
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFormatString(String v) {
        converterBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFormatString(UnaryOperator<String> mutator) {
        converterBuilder.formatString(mutator.apply(converterBuilder.formatString().orElse(null)));
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        validatorBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return validatorExceptionFactory(mutator.apply(validatorBuilder.exceptionFactory().orElse(null)));
    }

    OptionSpecBuilder<T> converterExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        converterBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return converterExceptionFactory(mutator.apply(converterBuilder.exceptionFactory().orElse(null)));
    }

    OptionSpecBuilder<T> exceptionFormatString(String v) {
        return validatorExceptionFormatString(v).converterExceptionFormatString(v);
    }

    OptionSpecBuilder<T> exceptionFormatString(UnaryOperator<String> mutator) {
        return validatorExceptionFormatString(mutator).converterExceptionFormatString(mutator);
    }

    OptionSpecBuilder<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        return validatorExceptionFactory(v).converterExceptionFactory(v);
    }

    OptionSpecBuilder<T> exceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return validatorExceptionFactory(mutator).converterExceptionFactory(mutator);
    }

    OptionSpecBuilder<T> converter(ValueConverter<T> v) {
        converterBuilder.converter(v);
        return this;
    }

    OptionSpecBuilder<T> converter(Function<String, T> v) {
        return converter(ValueConverter.create(v, valueType));
    }

    OptionSpecBuilder<T> validator(Predicate<T> v) {
        validatorBuilder.predicate(v::test);
        return this;
    }

    @SuppressWarnings("overloads")
    OptionSpecBuilder<T> validator(Consumer<T> v) {
        validatorBuilder.consumer(v::accept);
        return this;
    }

    @SuppressWarnings("overloads")
    OptionSpecBuilder<T> validator(UnaryOperator<Validator.Builder<T, RuntimeException>> mutator) {
        validatorBuilder = mutator.apply(validatorBuilder);
        return this;
    }

    OptionSpecBuilder<T> withoutConverter() {
        converterBuilder.convert(null);
        return this;
    }

    OptionSpecBuilder<T> withoutValidator() {
        validatorBuilder.predicate(null).consumer(null);
        return this;
    }

    OptionSpecBuilder<T> name(String v) {
        name = v;
        return this;
    }

    OptionSpecBuilder<T> shortName(String v) {
        if (v != null && v.length() != 1) {
            throw new IllegalArgumentException();
        }
        shortName = v;
        return this;
    }

    OptionSpecBuilder<T> mergePolicy(MergePolicy v) {
        mergePolicy = v;
        return this;
    }

    OptionSpecBuilder<T> scope(OptionScope... v) {
        return scope(Set.of(v));
    }

    OptionSpecBuilder<T> scope(Collection<? extends OptionScope> v) {
        scope = Set.copyOf(v);
        return this;
    }

    OptionSpecBuilder<T> scope(UnaryOperator<Set<OptionScope>> mutator) {
        return scope(mutator.apply(scope().orElseGet(Set::of)));
    }

    OptionSpecBuilder<T> inScope(OptionScope... v) {
        return inScope(Set.of(v));
    }

    OptionSpecBuilder<T> inScope(Collection<? extends OptionScope> v) {
        final Set<OptionScope> newScope = new HashSet<>(v);
        scope().ifPresent(newScope::addAll);
        scope = newScope;
        return this;
    }

    OptionSpecBuilder<T> outOfScope(OptionScope... v) {
        return outOfScope(Set.of(v));
    }

    OptionSpecBuilder<T> outOfScope(Collection<? extends OptionScope> v) {
        if (scope != null) {
            final Set<OptionScope> newScope = new HashSet<>(scope);
            newScope.removeAll(v);
            scope = newScope;
        }
        return this;
    }

    OptionSpecBuilder<T> defaultValue(T v) {
        defaultValue = v;
        return this;
    }

    private Optional<String> name() {
        return Optional.ofNullable(name);
    }

    private Optional<String> shortName() {
        return Optional.ofNullable(shortName);
    }

    private Optional<MergePolicy> mergePolicy() {
        return Optional.ofNullable(mergePolicy);
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
        ).filter(Optional::isPresent).map(Optional::orElseThrow).map(OptionName::new).distinct().toList();
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
    private MergePolicy mergePolicy;
    private Set<OptionScope> scope;
    private T defaultValue;
    private OptionValueConverter.Builder<T> converterBuilder = OptionValueConverter.build();
    private Validator.Builder<T, RuntimeException> validatorBuilder = Validator.build();
}
