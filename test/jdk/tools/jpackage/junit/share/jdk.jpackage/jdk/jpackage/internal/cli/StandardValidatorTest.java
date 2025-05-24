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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class StandardValidatorTest {

    @Test
    public void test_IS_DIRECTORY(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY;

        assertTrue(testee.test(tempDir));
        assertFalse(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertFalse(testee.test(file));
        assertTrue(testee.test(tempDir));
    }

    @Test
    public void test_IS_EXISTENT_NOT_DIRECTORY(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_EXISTENT_NOT_DIRECTORY;

        assertFalse(testee.test(tempDir));
        assertFalse(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertTrue(testee.test(file));
        assertFalse(testee.test(tempDir));
    }

    @Test
    public void test_IS_DIRECTORY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY_OR_NON_EXISTENT;

        assertTrue(testee.test(tempDir));
        assertTrue(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertFalse(testee.test(file));
        assertTrue(testee.test(tempDir));
    }

    @Test
    public void test_IS_DIRECTORY_EMPTY_OR_NON_EXISTENT(@TempDir Path tempDir) throws IOException {

        final var testee = StandardValidator.IS_DIRECTORY_EMPTY_OR_NON_EXISTENT;

        assertTrue(testee.test(tempDir));
        assertTrue(testee.test(tempDir.resolve("foo")));

        final var file = tempDir.resolve("foo");
        Files.writeString(file, "foo");

        assertFalse(testee.test(file));
        assertFalse(testee.test(tempDir));
    }

    @Test
    public void test_IS_URL() throws IOException {

        final var testee = StandardValidator.IS_URL;

        assertDoesNotThrow(() -> testee.accept("http://foo"));

        final var ex = assertThrowsExactly(Validator.ValidatingConsumerException.class, () -> testee.accept(":"));

        assertNotNull(ex.getCause());
    }
}
