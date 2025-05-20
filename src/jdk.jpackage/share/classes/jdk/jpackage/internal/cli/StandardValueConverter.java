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

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;


final class StandardValueConverter {

    static ValueConverter<String> identityConv() {
        return IDENTITY_CONV;
    }

    static ValueConverter<Path> pathConv() {
        return PATH_CONV;
    }

    @SuppressWarnings("unchecked")
    static final <T> ValueConverter<T[]> toArray(ValueConverter<T> from, Function<String, String[]> conv) {
        final var stringArrayConv = stringArrayConv(conv);
        return ValueConverter.create(value -> {
            return Stream.of(stringArrayConv.convert(value)).map(from::convert).toArray(length -> {
                return (T[])Array.newInstance(from.valueType(), length);
            });
        }, (Class<? extends T[]>)from.valueType().arrayType());
    }

    static ValueConverter<String[]> stringArrayConv(Function<String, String[]> conv) {
        return ValueConverter.create(conv, String[].class);
    }

    static Function<String, String[]> regexpSplitter(String regexp) {
        return str -> {
            return str.split(regexp);
        };
    }

    private static final ValueConverter<String> IDENTITY_CONV = ValueConverter.create(x -> x, String.class);
    private static final ValueConverter<Path> PATH_CONV = ValueConverter.create(Path::of, Path.class);
}
