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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOption;
import jdk.jpackage.internal.util.FileUtils;

final class TempDirectory implements Closeable {

    TempDirectory(Options options, RetryExecutorFactory retryExecutorFactory) throws IOException {
        this(StandardOption.TEMP_ROOT.findIn(options), retryExecutorFactory);
    }

    TempDirectory(Optional<Path> tempDir, RetryExecutorFactory retryExecutorFactory) throws IOException {
        if (tempDir.isPresent()) {
            this.path = tempDir.orElseThrow();
        } else {
            this.path = Files.createTempDirectory("jdk.jpackage");
        }

        deleteOnClose = tempDir.isEmpty();
        this.retryExecutorFactory = Objects.requireNonNull(retryExecutorFactory);
    }

    Options map(Options options) {
        if (deleteOnClose) {
            return options.copyWithDefaultValue(StandardOption.TEMP_ROOT, path);
        } else {
            return options;
        }
    }

    Path path() {
        return path;
    }

    boolean deleteOnClose() {
        return deleteOnClose;
    }

    @Override
    public void close() throws IOException {
        if (deleteOnClose) {
            retryExecutorFactory.<Void, IOException>retryExecutor(IOException.class)
                    .setMaxAttemptsCount(5)
                    .setAttemptTimeout(2, TimeUnit.SECONDS)
                    .setExecutable(context -> {
                        try {
                            FileUtils.deleteRecursive(path);
                        } catch (IOException ex) {
                            if (!context.isLastAttempt()) {
                                throw ex;
                            } else {
                                List<Path> remainingFiles;
                                try (var walk = Files.walk(path)) {
                                    remainingFiles = walk.filter(Files::isRegularFile).toList();
                                } catch (IOException walkEx) {
                                    remainingFiles = List.of();
                                }

                                if (remainingFiles.isEmpty()) {
                                    Log.info(I18N.format("warning.tempdir.cleanup-failed", path));
                                } else {
                                    remainingFiles.forEach(file -> {
                                        Log.info(I18N.format("warning.tempdir.cleanup-file-failed", file));
                                    });
                                }
                            }
                        }
                        return null;
                    }).execute();
        }
    }

    private final Path path;
    private final boolean deleteOnClose;
    private final RetryExecutorFactory retryExecutorFactory;
}
