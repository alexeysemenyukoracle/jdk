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
import jdk.jpackage.internal.cli.Validator.ValidatingConsumer;
import jdk.jpackage.internal.cli.Validator.ValidatingPredicate;

final class StandardValidator {

    static final ValidatingPredicate<Path> IS_DIRECTORY = Files::isDirectory;

    static final ValidatingPredicate<Path> IS_DIRECTORY_EMPTY_OR_NON_EXISTANT = path -> {
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

    static final ValidatingConsumer<String> IS_URL = url -> {
        try {
            new URI(url);
        } catch (URISyntaxException ex) {
            throw new Validator.ValidatingConsumerException(ex);
        }
    };

    static <U extends Exception> Validator.Builder<Path, U> isDirectory(Validator.Builder<Path, U> builder) {
        return builder.predicate(IS_DIRECTORY);
    }

    static <U extends Exception> Validator.Builder<Path, U> isDirectoryEmptyOrNonExistant(Validator.Builder<Path, U> builder) {
        return builder.predicate(IS_DIRECTORY_EMPTY_OR_NON_EXISTANT);
    }

    static <U extends Exception> Validator.Builder<String, U> isUrl(Validator.Builder<String, U> builder) {
        return builder.consumer(IS_URL);
    }
}
