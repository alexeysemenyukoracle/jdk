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

import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.regexpSplitter;
import static jdk.jpackage.internal.cli.StandardValueConverter.stringArrayConv;

import jdk.jpackage.internal.cli.Validator.ValidatingMethod;
import jdk.jpackage.internal.cli.Validator.ValidatingPredicate;
import jdk.jpackage.internal.cli.Validator.ValidatingConsumer;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class OptionSpecBuilder<T> {

    static OptionSpecBuilder<String> create() {
        return new OptionSpecBuilder<>(String.class);
    }

    OptionSpecBuilder(Class<? extends T> valueType) {
        this.valueType = Objects.requireNonNull(valueType);
    }

    private <U> OptionSpecBuilder(Class<? extends T> valueType, OptionSpecBuilder<U> other) {
        this(valueType);
        other.name().ifPresent(this::name);
        other.shortName().ifPresent(this::shortName);
        other.scope().ifPresent(this::scope);
        other.valueConverter().map(OptionValueConverter.class::cast).ifPresent(this::valueConverter);
        other.valueValidator().map(Validator.class::cast).ifPresent(this::valueValidator);
        converterBuilderFactory.putAll(other.converterBuilderFactory);
        validatorBuilderFactory.putAll(other.validatorBuilderFactory);
    }

    OptionSpec<T> createOptionSpec() {
        final OptionSpec.MergePolicy mergePolicy;
        if (valueConverter().map(OptionValueConverter::valueType).map(Class::isArray).orElse(false)) {
            mergePolicy = OptionSpec.MergePolicy.CONCATENATE;
        } else {
            mergePolicy = OptionSpec.MergePolicy.USE_LAST;
        }

        return new OptionSpec<>(names(), valueConverter(), scope, valueValidator(),
                Optional.ofNullable(mergePolicy).orElse(OptionSpec.MergePolicy.USE_LAST));
    }

    final static class ScalarValueConverterBuilder {

        ScalarValueConverterBuilder(OptionSpecBuilder<?> parentBuilder) {
            this.parentBuilder = Objects.requireNonNull(parentBuilder);
        }

        ArrayValueConverterBuilder split(String regexp) {
            return new ArrayValueConverterBuilder(parentBuilder, regexpSplitter(regexp));
        }

        ArrayValueConverterBuilder splitPaths() {
            return split(File.pathSeparator);
        }

        ArrayValueConverterBuilder split(Function<String, String[]> splitter) {
            return new ArrayValueConverterBuilder(parentBuilder, splitter);
        }

        OptionSpecBuilder<Path> toPath() {
            return converter(pathConv());
        }

        <T> OptionSpecBuilder<T> converter(ValueConverter<T> v) {
            return parentBuilder.valueConverter(impl.convert(v));
        }

        ScalarValueConverterBuilder msgId(String v) {
            impl.msgId(v);
            return this;
        }

        ScalarValueConverterBuilder exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            impl.exceptionFactory(v);
            return this;
        }

        private final OptionValueConverter.Builder<String> impl = OptionValueConverter.build();
        private final OptionSpecBuilder<?> parentBuilder;
    }


    final static class ArrayValueConverterBuilder {

        ArrayValueConverterBuilder(OptionSpecBuilder<?> parentBuilder, Function<String, String[]> splitter) {
            this.parentBuilder = Objects.requireNonNull(parentBuilder);
            this.splitter = Objects.requireNonNull(splitter);
        }

        OptionSpecBuilder<Path[]> toPathArray() {
            return converter(StandardValueConverter.toArray(pathConv(), splitter));
        }

        OptionSpecBuilder<String[]> toStringArray() {
            return converter(stringArrayConv(splitter));
        }

        <T> OptionSpecBuilder<T> converter(ValueConverter<T> v) {
            return parentBuilder.valueConverter(impl.convert(v));
        }

        ArrayValueConverterBuilder msgId(String v) {
            impl.msgId(v);
            return this;
        }

        ArrayValueConverterBuilder exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            impl.exceptionFactory(v);
            return this;
        }

        private final OptionValueConverter.Builder<String[]> impl = OptionValueConverter.build();
        private final OptionSpecBuilder<?> parentBuilder;
        private final Function<String, String[]> splitter;
    }


    final class ValueValidatorBuilder {

        ValueValidatorBuilder(Validator.Builder<T, Exception> impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        OptionSpecBuilder<Path> isDirectory() {
            return adopt(Path.class, StandardValidator.isDirectory(Validator.build())).commit();
        }

        OptionSpecBuilder<Path> isDirectoryEmptyOrNonExistant() {
            return adopt(Path.class, StandardValidator.isDirectoryEmptyOrNonExistant(Validator.build())).commit();
        }

        OptionSpecBuilder<String> isUrl() {
            return adopt(String.class, StandardValidator.isUrl(Validator.build())).commit();
        }

        OptionSpecBuilder<T> predicate(ValidatingPredicate<T> v) {
            impl.predicate(v);
            return commit();
        }

        OptionSpecBuilder<T> consumer(ValidatingConsumer<T> v) {
            impl.consumer(v);
            return commit();
        }

        ValueValidatorBuilder msgId(String v) {
            impl.msgId(v);
            return this;
        }

        ValueValidatorBuilder exceptionFactory(OptionValueExceptionFactory<? extends Exception> v) {
            impl.exceptionFactory(v);
            return this;
        }

        private OptionSpecBuilder<T> commit() {
            OptionSpecBuilder.this.valueValidator(impl);
            return OptionSpecBuilder.this;
        }

        @SuppressWarnings("unchecked")
        private <W> OptionSpecBuilder<W>.ValueValidatorBuilder adopt(Class<? extends W> newValueType, Validator.Builder<W, Exception> builder) {
            if (!newValueType.equals(valueType)) {
                throw new IllegalStateException();
            }
            impl.msgId().ifPresent(builder::msgId);
            impl.exceptionFactory().ifPresent(builder::exceptionFactory);
            return ((OptionSpecBuilder<W>)OptionSpecBuilder.this).new ValueValidatorBuilder(builder);
        }

        private final Validator.Builder<T, Exception> impl;
    }


    OptionValue.Builder<T> toOptionValueBuilder() {
        return OptionValue.<T>build().spec(createOptionSpec());
    }

    OptionValue<T> createOptionValue() {
        return toOptionValueBuilder().create();
    }

    OptionValue<String> ofString() {
        return valueConverter(OptionValueConverter.build(String.class).convert(identityConv())).createOptionValue();
    }

    OptionValue<Path> ofPath() {
        return convert().toPath().createOptionValue();
    }

    OptionValue<Path> ofDirectory() {
        return convert().toPath().validate().isDirectory().createOptionValue();
    }

    OptionValue.Builder<Path[]> ofPathArray() {
        return convert().splitPaths().toPathArray().toOptionValueBuilder();
    }

    OptionValue<List<Path>> ofPathList() {
        return ofPathArray().to(List::of).create();
    }

    OptionValue<Boolean> noValue() {
        final var ovBuilder = valueConverter((OptionValueConverter<T>)null).valueValidator((Validator<T, ?>)null).toOptionValueBuilder();
        return ovBuilder.to(_ -> true).defaultValue(false).create();
    }

    OptionSpecBuilder<T> name(String v) {
        name = v;
        return this;
    }

    ValueValidatorBuilder validate() {
        return new ValueValidatorBuilder(Validator.build());
    }

    ScalarValueConverterBuilder convert() {
        return new ScalarValueConverterBuilder(this);
    }

    @SuppressWarnings("unchecked")
    <U> OptionSpecBuilder<U> valueConverter(OptionValueConverter<U> v) {
        final OptionSpecBuilder<U> newBuilder;
        if (v == null || v.valueType().isAssignableFrom(valueType)) {
            newBuilder = (OptionSpecBuilder<U>)this;
        } else {
            newBuilder = new OptionSpecBuilder<>(v.valueType(), this);
        }

        newBuilder.valueConverter = v;
        return newBuilder;
    }

    private <U> OptionSpecBuilder<U> valueConverter(OptionValueConverter.Builder<U> v) {
        @SuppressWarnings("unchecked")
        final var builder = Optional.ofNullable(converterBuilderFactory.get(v.converter().orElseThrow().valueType())).map(supplier -> {
            return (OptionValueConverter.Builder<U>)supplier.get();
        }).orElseGet(OptionValueConverter::build);

        v.converter().ifPresent(builder::converter);
        v.msgId().ifPresent(builder::msgId);
        v.exceptionFactory().ifPresent(builder::exceptionFactory);

        return valueConverter(builder.create());
    }

    OptionSpecBuilder<T> valueValidator(Validator<T, ? extends Exception> v) {
        valueValidator = v;
        return this;
    }

    private OptionSpecBuilder<T> valueValidator(Validator.Builder<T, ? extends Exception> v) {
        @SuppressWarnings("unchecked")
        final var builder = Optional.ofNullable(validatorBuilderFactory.get(v.method().orElseThrow())).map(supplier -> {
            return (Validator.Builder<T, Exception>)supplier.get();
        }).orElseGet(Validator::build);

        v.consumer().ifPresent(builder::consumer);
        v.predicate().ifPresent(builder::predicate);
        v.msgId().ifPresent(builder::msgId);
        v.exceptionFactory().ifPresent(builder::exceptionFactory);

        return valueValidator(builder.create());
    }

    OptionSpecBuilder<T> shortName(String v) {
        shortName = v;
        return this;
    }

    OptionSpecBuilder<T> scope(OptionScope... v) {
        return scope(Set.of(v));
    }

    OptionSpecBuilder<T> scope(Collection<? extends OptionScope> v) {
        scope = Set.copyOf(v);
        return this;
    }

    OptionSpecBuilder<T> enhanceScope(OptionScope... v) {
        return enhanceScope(Set.of(v));
    }

    OptionSpecBuilder<T> enhanceScope(Collection<? extends OptionScope> v) {
        final Set<OptionScope> newScope = new HashSet<>();
        newScope.addAll(v);
        scope().ifPresent(newScope::addAll);
        scope = newScope;
        return this;
    }

    Optional<String> name() {
        return Optional.ofNullable(name);
    }

    Optional<OptionValueConverter<T>> valueConverter() {
        return Optional.ofNullable(valueConverter);
    }

    Optional<Validator<T, ? extends Exception>> valueValidator() {
        return Optional.ofNullable(valueValidator);
    }

    Optional<String> shortName() {
        return Optional.ofNullable(shortName);
    }

    Optional<Set<OptionScope>> scope() {
        return Optional.ofNullable(scope);
    }

    <U> OptionSpecBuilder<T> setConverterBuilder(Class<U> valueType, Supplier<OptionValueConverter.Builder<U>> builderSupplier) {
        Objects.requireNonNull(valueType);
        Objects.requireNonNull(builderSupplier);
        converterBuilderFactory.put(valueType, builderSupplier);
        return this;
    }

    <U, V extends Exception> OptionSpecBuilder<T> setValidatorBuilder(ValidatingMethod validatingMethod, Supplier<Validator.Builder<U, V>> builderSupplier) {
        Objects.requireNonNull(validatingMethod);
        Objects.requireNonNull(builderSupplier);
        validatorBuilderFactory.put(validatingMethod, builderSupplier);
        return this;
    }

    private List<OptionName> names() {
        return Stream.of(
                Optional.of(name),
                shortName()
        ).filter(Optional::isPresent).map(Optional::orElseThrow).map(OptionName::new).toList();
    }

    private final Class<? extends T> valueType;
    private String name;
    private OptionValueConverter<T> valueConverter;
    private Validator<T, ? extends Exception> valueValidator;
    private String shortName;
    private Set<OptionScope> scope;
    private Map<Class<?>, Supplier<? extends OptionValueConverter.Builder<?>>> converterBuilderFactory = new HashMap<>();
    private Map<ValidatingMethod, Supplier<? extends Validator.Builder<?, ?>>> validatorBuilderFactory = new HashMap<>();
}
