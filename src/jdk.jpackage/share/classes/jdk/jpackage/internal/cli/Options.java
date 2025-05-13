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


/**
 * R/O collection of objects associated with identifiers.
 * <p>
 * Use {@link OptionValue} for typed access of the stored objects.
 */
@FunctionalInterface
public interface Options {

    <T> Optional<T> find(OptionIdentifier id);

    default boolean contains(OptionIdentifier id) {
        return find(id).isPresent();
    }

    default Options setParent(Options other) {
        Objects.requireNonNull(other);
        final var that = this;
        return new Options() {
            @Override
            public <T> Optional<T> find(OptionIdentifier id) {
                return that.<T>find(id).or(() -> other.find(id));
            }
        };
    }

    public static Options of(Map<OptionIdentifier, Object> map) {
        Objects.requireNonNull(map);
        return new Options() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<T> find(OptionIdentifier id) {
                return Optional.ofNullable((T)map.get(id));
            }
        };
    }

    public static Options concat(Options... options) {
        final var copy = List.copyOf(List.of(options));
        return new Options() {
            @Override
            public <T> Optional<T> find(OptionIdentifier id) {
                return copy.stream().map(o -> {
                    return o.<T>find(id);
                }).filter(Optional::isPresent).map(Optional::orElseThrow).findFirst();
            }
        };
    }
}
