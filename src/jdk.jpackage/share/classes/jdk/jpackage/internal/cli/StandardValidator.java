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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

final class StandardValidator {

    static <U extends Exception> UnaryOperator<Validator.Builder<Path, U>> isDirectory() {
        return builder -> {
            return builder.predicate(IS_DIRECTORY);
        };
    }

    static <U extends Exception> UnaryOperator<Validator.Builder<Path, U>> isDirectoryEmptyOrNonExistant() {
        return builder -> {
            return builder.predicate(IS_DIRECTORY_EMPTY_OR_NON_EXISTANT);
        };
    }

    static <U extends Exception> UnaryOperator<Validator.Builder<String, U>> isUrl() {
        return builder -> {
            return builder.consumer(IS_URL);
        };
    }

    private static final Predicate<Path> IS_DIRECTORY = Files::isDirectory;

    private static final Predicate<Path> IS_DIRECTORY_EMPTY_OR_NON_EXISTANT = path -> {
        if (!Files.exists(path)) {
            return true;
        } else if (Files.isDirectory(path)) {
            try (var dirContents = Files.list(path)) {
                return dirContents.findAny().isEmpty();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else {
            return false;
        }
    };

    private static final Consumer<String> IS_URL = url -> {
        try {
            new URI(url);
        } catch (URISyntaxException ex) {
            throw new Validator.ValidatingConsumerException(ex);
        }
    };
}
