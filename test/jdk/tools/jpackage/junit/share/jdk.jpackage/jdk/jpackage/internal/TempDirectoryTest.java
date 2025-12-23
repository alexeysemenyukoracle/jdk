/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOption;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TempDirectoryTest {

    @Test
    void test_directory_use(@TempDir Path tempDirPath) throws IOException {
        try (var tempDir = new TempDirectory(Optional.of(tempDirPath), RetryExecutorFactory.DEFAULT)) {
            assertEquals(tempDir.path(), tempDirPath);
            assertFalse(tempDir.deleteOnClose());

            var cmdline = Options.of(Map.of());
            assertSame(cmdline, tempDir.map(cmdline));
        }

        assertTrue(Files.isDirectory(tempDirPath));
    }

    @Test
    void test_directory_new() throws IOException {
        var tempDir = new TempDirectory(Optional.empty(), RetryExecutorFactory.DEFAULT);
        try (tempDir) {
            assertTrue(Files.isDirectory(tempDir.path()));
            assertTrue(tempDir.deleteOnClose());

            var cmdline = Options.of(Map.of());
            var mappedCmdline = tempDir.map(cmdline);
            assertEquals(tempDir.path(), StandardOption.TEMP_ROOT.getFrom(mappedCmdline));
        }

        assertFalse(Files.isDirectory(tempDir.path()));
    }

    @EnabledOnOs(value = OS.WINDOWS, disabledReason = "Can reliably lock a file using FileLock to cause an IOException on Windows only")
    @SuppressWarnings("try")
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_close(boolean allAttemptsFail) throws IOException {

        var logSink = new StringWriter();
        var logPrintWriter = new PrintWriter(logSink, true);
        Log.setPrintWriter(logPrintWriter, logPrintWriter);

        var tempDir = new TempDirectory(Optional.empty(), RetryExecutorMock::new);

        for (var fname : List.of("a", "b")) {
            Files.createFile(tempDir.path().resolve(fname));
        }

        var lockedFile = tempDir.path().resolve("a");

        RetryExecutorMock.currentConfig = new RetryExecutorMock.Config(lockedFile, !allAttemptsFail);

        tempDir.close();

        logPrintWriter.flush();
        var logMessages = new BufferedReader(new StringReader(logSink.toString())).lines().toList();

        if (allAttemptsFail) {
            assertTrue(Files.exists(lockedFile));
            assertFalse(Files.exists(tempDir.path().resolve("b")));
            assertEquals(List.of(I18N.format("warning.tempdir.cleanup-file-failed", lockedFile)), logMessages);
        } else {
            assertFalse(Files.exists(tempDir.path()));
            assertEquals(List.of(), logMessages);
        }

    }

    private static final class RetryExecutorMock<T, E extends Exception> extends RetryExecutor<T, E> {

        RetryExecutorMock(Class<? extends E> exceptionType) {
            super(exceptionType);
            setSleepFunction(_ -> {});
            config = Objects.requireNonNull(RetryExecutorMock.currentConfig);
        }

        @SuppressWarnings({ "try", "unchecked" })
        @Override
        public RetryExecutor<T,E> setExecutable(ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> v) {
            return super.setExecutable(context -> {
                if (context.isLastAttempt() && config.unlockOnLastAttempt()) {
                    return v.apply(context);
                } else {
                    try (var out = new FileOutputStream(config.lockedFile().toFile()); var lock = out.getChannel().lock()) {
                        return v.apply(context);
                    } catch (IOException ex) {
                        if (exceptionType().isInstance(ex)) {
                            throw (E)ex;
                        } else {
                            throw ExceptionBox.toUnchecked(ex);
                        }
                    }
                }
            });
        };

        private final Config config;

        record Config(Path lockedFile, boolean unlockOnLastAttempt) {
            Config {
                Objects.requireNonNull(lockedFile);
            }
        }

        static Config currentConfig;
    }
}
