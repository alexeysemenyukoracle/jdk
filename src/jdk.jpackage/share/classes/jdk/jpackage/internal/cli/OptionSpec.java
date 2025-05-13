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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


record OptionSpec<T>(List<OptionName> names, Optional<OptionValueConverter<T>> valueConverter,
        Set<OptionScope> scope, Optional<Validator<T, ? extends Exception>> valueValidator,
        MergePolicy mergePolicy) {

    enum MergePolicy {
        USE_FIRST,
        USE_LAST,
        CONCATENATE
    }

    OptionSpec {
        Objects.requireNonNull(names);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Empty name list");
        }
        Objects.requireNonNull(valueConverter);
        Objects.requireNonNull(scope);
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Empty scope");
        }
        Objects.requireNonNull(valueValidator);
        Objects.requireNonNull(mergePolicy);

        if (valueConverter.isEmpty() && valueValidator.isPresent()) {
            throw new IllegalArgumentException("Validator is not applicable");
        }

        final var typeMustBeArray = mergePolicy.equals(MergePolicy.CONCATENATE);
        final var type = valueType(valueConverter);
        if (typeMustBeArray != type.isArray()) {
            throw new IllegalArgumentException(String.format("Invalid merge policy [%s] for type [%s]", mergePolicy, type));
        }
    }

    OptionName name() {
        return names.getFirst();
    }

    List<OptionName> otherNames() {
        return names.subList(1, names.size());
    }

    Stream<OptionSpec<T>> generateForEveryName() {
        return names().stream().map(v -> {
            return new OptionSpec<>(List.of(v), valueConverter, scope, valueValidator, mergePolicy);
        });
    }

    List<OptionName> findNamesIn(Options cmdline) {
        return names().stream().filter(cmdline::contains).toList();
    }

    String formatNameForErrorMessage(Options cmdline) {
        return findNamesIn(cmdline).getFirst().formatForCommandLine();
    }

    boolean hasValue() {
        return valueConverter.isPresent();
    }

    Class<? extends T> valueType() {
        return valueType(valueConverter);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> valueType(Optional<OptionValueConverter<T>> valueConverter) {
        return valueConverter.map(OptionValueConverter::valueType).map(x -> (Class<T>)x).orElse((Class<T>)String.class);
    }
}
