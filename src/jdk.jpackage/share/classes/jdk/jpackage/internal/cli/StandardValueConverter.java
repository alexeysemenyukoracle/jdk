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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


final class StandardValueConverter {

    static ValueConverter<String> identityConv() {
        return IDENTITY_CONV;
    }

    static ValueConverter<Path> pathConv() {
        return PATH_CONV;
    }

    static ValueConverter<String[]> stringArrayConv(Optional<String> splitRegexp) {
        Objects.requireNonNull(splitRegexp);
        return new ValueConverter<>() {
            @Override
            public String[] convert(String value) {
                return splitRegexp.map(value::split).orElseGet(() -> {
                    return new String[] { Objects.requireNonNull(value) };
                });
            }

            @Override
            public Class<? extends String[]> valueType() {
                return String[].class;
            }
        };
    }

    static ValueConverter<Path[]> pathArrayConv(Optional<String> splitRegexp) {
        Objects.requireNonNull(splitRegexp);
        final var stringArrayConv = stringArrayConv(splitRegexp);
        return new ValueConverter<>() {
            @Override
            public Path[] convert(String value) {
                return Stream.of(stringArrayConv.convert(value)).map(Path::of).toArray(Path[]::new);
            }

            @Override
            public Class<? extends Path[]> valueType() {
                return Path[].class;
            }
        };
    }

    private static final ValueConverter<String> IDENTITY_CONV = new ValueConverter<>() {
        @Override
        public String convert(String value) {
            return Objects.requireNonNull(value);
        }

        @Override
        public Class<? extends String> valueType() {
            return String.class;
        }
    };

    private static final ValueConverter<Path> PATH_CONV = new ValueConverter<>() {
        @Override
        public Path convert(String value) {
            return Path.of(value);
        }

        @Override
        public Class<? extends Path> valueType() {
            return Path.class;
        }
    };
}
