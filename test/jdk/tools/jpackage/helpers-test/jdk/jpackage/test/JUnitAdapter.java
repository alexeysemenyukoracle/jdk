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
package jdk.jpackage.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class JUnitAdapter {

    public JUnitAdapter() {
        if (System.getProperty("test.src") == null) {
            // Was called by somebody else but not by jtreg
            System.setProperty("test.src", Path.of("@@openJdkDir@@/test/jdk/tools/jpackage").toString());
        }
    }

    @TestFactory
    Stream<DynamicTest> createJPackageTests() throws Throwable {
        final List<TestInstance> tests = new ArrayList<>();
        try (final var testBuilder = TestBuilder.build().workDirRoot(Path.of("")).testConsumer(tests::add).create()) {
            testBuilder.processCmdLineArg("--jpt-before-run=jdk.jpackage.test.JPackageCommand.useToolProviderByDefault");
            testBuilder.processCmdLineArg("--jpt-run=" + getClass().getName());
        }
        return tests.stream().map(test -> {
            return DynamicTest.dynamicTest(test.fullName(), () -> {
                final var rootWorkDir = Files.createTempDirectory("jdk.jpackage-test");
                try {
                    TKit.runTests(List.of(new TestInstance(test, rootWorkDir)), Set.of(TKit.RunTestMode.FAIL_FAST));
                } finally {
                    TKit.deleteDirectoryRecursive(rootWorkDir);
                }
            });
        });
    }

    static List<String> captureJPackageTestLog(ThrowingRunnable runnable) {
        final var buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            TKit.withExtraLogStream(runnable, ps);
        }

        try (final var in = new ByteArrayInputStream(buf.toByteArray());
                final var reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                final var bufReader = new BufferedReader(reader)) {
            return bufReader.lines().map(line -> {
                // Skip timestamp
                return line.substring(LOG_MSG_TIMESTAMP_LENGTH);
            }).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final int LOG_MSG_TIMESTAMP_LENGTH = "[HH:mm:ss.SSS] ".length();
}
