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
package jdk.jpackage.internal;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOptionValue;
import jdk.jpackage.internal.util.FileUtils;

final class TempDirectory implements Closeable {

    TempDirectory(Options optionValues) {
        final var tempDir = StandardOptionValue.TEMP_ROOT.findIn(optionValues);
        if (tempDir.isPresent()) {
            this.path = tempDir.orElseThrow();
            this.optionValues = optionValues;
        } else {
            try {
                this.path = Files.createTempDirectory("jdk.jpackage");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            this.optionValues = optionValues.copyWithDefaultValue(StandardOptionValue.TEMP_ROOT, path);
        }

        deleteOnClose = tempDir.isEmpty();
    }

    Options optionValues() {
        return optionValues;
    }

    Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        if (deleteOnClose) {
            FileUtils.deleteRecursive(path);
        }
    }

    private final Path path;
    private final Options optionValues;
    private final boolean deleteOnClose;
}
