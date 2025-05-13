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
import static jdk.jpackage.internal.cli.StandardValueConverter.pathArrayConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.stringArrayConv;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import jdk.jpackage.internal.model.BundlingOperation;

final class OptionSpecBuilder {
    OptionSpec<?> createOptionSpec() {
        return new OptionSpec<Object>(name,
                valueConverter().map(ValueConverter.class::cast),
                shortName(),
                scope,
                valueValidator().map(Consumer.class::cast),
                Optional.ofNullable(mergePolicy).orElse(OptionSpec.MergePolicy.USE_LAST));
    }

    <T> OptionValue.Builder<T> toOptionValueBuilder() {
        return OptionValue.<T>build().spec(createOptionSpec());
    }

    <T> OptionValue<T> createOptionValue() {
        final OptionValue.Builder<T> builder = toOptionValueBuilder();
        return builder.create();
    }

    OptionValue<String> ofString() {
        return oneOrZero().valueConverter(identityConv()).createOptionValue();
    }

    OptionValue<Path> ofPath() {
        return oneOrZero().valueConverter(pathConv()).createOptionValue();
    }

    OptionValue<Path> ofDirectory() {
        return valueValidator(StandardValueValidator::validateDirectory).ofPath();
    }

    OptionValue.Builder<String[]> ofStringArray() {
        return repetitive().valueSeparatorIfUnknown("\\s").valueConverter(stringArrayConv(valueSeparator())).toOptionValueBuilder();
    }

    OptionValue.Builder<Path[]> ofPathArray() {
        return repetitive().valueSeparatorIfUnknown(File.pathSeparator).valueConverter(pathArrayConv(valueSeparator())).toOptionValueBuilder();
    }

    OptionValue.Builder<Path[]> ofDirectoryArray() {
        return valueValidator(StandardValueValidator::validateDirectoryArray).ofPathArray();
    }

    OptionValue<List<String>> ofStringList() {
        return ofStringArray().to(List::of).create();
    }

    OptionValue<List<Path>> ofPathList() {
        return ofPathArray().to(List::of).create();
    }

    OptionValue<List<Path>> ofDirectoryList() {
        return ofDirectoryArray().to(List::of).create();
    }

    OptionValue<String> ofUrl() {
        return valueValidator(StandardValueValidator::validateUrl).ofString();
    }

    OptionValue<Boolean> noValue() {
        return oneOrZero().valueValidator(null).<Void>toOptionValueBuilder().to(_ -> true).create();
    }

    OptionSpecBuilder repetitive() {
        return repetitive(true);
    }

    OptionSpecBuilder repetitive(boolean v) {
        mergePolicy = v ? OptionSpec.MergePolicy.CONCATENATE : OptionSpec.MergePolicy.USE_LAST;
        return this;
    }

    OptionSpecBuilder name(String v) {
        name = v;
        return this;
    }

    <T> OptionSpecBuilder valueConverter(ValueConverter<? extends T> v) {
        valueConverter = v;
        return this;
    }

    <T> OptionSpecBuilder valueValidator(Consumer<? extends T> v) {
        valueValidator = v;
        return this;
    }

    OptionSpecBuilder shortName(String v) {
        shortName = v;
        return this;
    }

    OptionSpecBuilder valueSeparator(String regexp) {
        valueSeparatorRegexp = regexp;
        valueSeparatorRegexpConfigured = true;
        return this;
    }

    OptionSpecBuilder withoutValueSeparator() {
        return valueSeparator(null);
    }

    OptionSpecBuilder scope(BundlingOperation... v) {
        return scope(Set.of(v));
    }

    OptionSpecBuilder scope(Set<BundlingOperation> v) {
        scope = v;
        return this;
    }

    Optional<String> name() {
        return Optional.ofNullable(name);
    }

    Optional<ValueConverter<?>> valueConverter() {
        return Optional.ofNullable(valueConverter);
    }

    Optional<Consumer<?>> valueValidator() {
        return Optional.ofNullable(valueValidator);
    }

    Optional<String> shortName() {
        return Optional.ofNullable(shortName);
    }

    Optional<String> valueSeparator() {
        return Optional.ofNullable(valueSeparatorRegexp);
    }

    Optional<Set<BundlingOperation>> scope() {
        return Optional.ofNullable(scope);
    }

    private OptionSpecBuilder oneOrZero() {
        return withoutValueSeparator().repetitive(false);
    }

    private OptionSpecBuilder valueSeparatorIfUnknown(String regexp) {
        if (valueSeparatorRegexpConfigured) {
            return this;
        } else {
            return valueSeparator(regexp);
        }
    }

    private String name;
    private ValueConverter<?> valueConverter;
    private Consumer<?> valueValidator;
    private String shortName;
    private String valueSeparatorRegexp;
    private boolean valueSeparatorRegexpConfigured;
    private Set<BundlingOperation> scope;
    private OptionSpec.MergePolicy mergePolicy;
}
