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
import java.util.Set;
import java.util.function.Consumer;
import jdk.jpackage.internal.model.BundlingOperation;


record OptionSpec<T>(String name, Optional<ValueConverter<T>> valueConverter,
        Optional<String> shortName, Set<BundlingOperation> scope,
        Optional<Consumer<T>> valueValidator, MergePolicy mergePolicy) {

    enum MergePolicy {
        USE_FIRST,
        USE_LAST,
        CONCATENATE
    }

    OptionSpec {
        Objects.requireNonNull(name);
        Objects.requireNonNull(valueConverter);
        Objects.requireNonNull(shortName);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(valueValidator);
        Objects.requireNonNull(mergePolicy);
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Empty scope");
        }
    }

    boolean withValue() {
        return valueConverter.isPresent();
    }

    Optional<ValueConverter<T>> valueConverterAndValidator() {
        if (valueValidator.isEmpty()) {
            return valueConverter;
        } else {
            return valueConverter.map(c -> {
                return new ValidatingValueConverter<>(c, valueValidator.orElseThrow());
            });
        }
    }

    private record ValidatingValueConverter<T>(ValueConverter<T> converter, Consumer<T> validator) implements ValueConverter<T> {

        @Override
        public T convert(String value) {
            final var v = converter.convert(value);
            validator.accept(v);
            return v;
        }

        @Override
        public Class<? extends T> valueType() {
            return converter.valueType();
        }
    }
}
