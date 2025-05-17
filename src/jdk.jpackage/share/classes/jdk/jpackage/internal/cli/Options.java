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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;


/**
 * R/O collection of objects associated with identifiers.
 * <p>
 * Use {@link OptionValue} for typed access of the stored objects.
 */
public interface Options {

    Optional<Object> find(OptionIdentifier id);

    boolean contains(String optionName);

    default boolean contains(OptionIdentifier id) {
        return find(id).isPresent();
    }

    default Options setDefaultValue(OptionIdentifier id, Object value) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(value);
        if (contains(id)) {
            return this;
        } else {
            return concat(this, of(Map.of(id, value)));
        }
    }

    default Options setParent(Options other) {
        return concat(this, other);
    }

    public static Options of(Map<OptionIdentifier, Object> map) {
        Objects.requireNonNull(map);
        return new Options() {
            @Override
            public Optional<Object> find(OptionIdentifier id) {
                return Optional.ofNullable(map.get(id));
            }

            @Override
            public boolean contains(String optionName) {
                Objects.requireNonNull(optionName);
                return false;
            }
        };
    }

    public static Options concat(Options... options) {
        final var copy = List.copyOf(List.of(options));
        return new Options() {
            @Override
            public Optional<Object> find(OptionIdentifier id) {
                return copy.stream().map(o -> {
                    return o.find(id);
                }).filter(Optional::isPresent).map(Optional::orElseThrow).findFirst();
            }

            @Override
            public boolean contains(String optionName) {
                return copy.stream().anyMatch(StandardPredicate.containts(optionName));
            }
        };
    }

    public final static class StandardPredicate {

        public static Predicate<Options> containts(OptionIdentifier id) {
            Objects.requireNonNull(id);
            return options -> {
                return options.contains(id);
            };
        }

        public static Predicate<Options> containts(String optionName) {
            Objects.requireNonNull(optionName);
            return options -> {
                return options.contains(optionName);
            };
        }

        public static <T> Predicate<Options> containts(OptionValue<T> ov) {
            return ov::containsIn;
        }

        public static <T> Predicate<Options> isEqual(OptionIdentifier id, T value) {
            return isEqual(id, () -> value);
        }

        public static <T> Predicate<Options> isEqual(OptionValue<T> ov, T value) {
            return isEqual(ov, (Supplier<T>)() -> value);
        }

        public static <T> Predicate<Options> isEqual(OptionIdentifier id, Supplier<T> supplier) {
            Objects.requireNonNull(id);
            Objects.requireNonNull(supplier);
            return options -> {
                return options.find(id).map(supplier.get()::equals).isPresent();
            };
        }

        public static <T> Predicate<Options> isEqual(OptionValue<T> ov, Supplier<T> supplier) {
            Objects.requireNonNull(ov);
            Objects.requireNonNull(supplier);
            return options -> {
                return ov.findIn(options).map(supplier.get()::equals).isPresent();
            };
        }
    }
}
