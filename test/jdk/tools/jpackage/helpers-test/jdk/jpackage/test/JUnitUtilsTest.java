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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class JUnitUtilsTest {

    @Test
    public void test_assertArrayEquals() {
        JUnitUtils.assertArrayEquals(new int[] {1, 2, 3}, new int[] {1, 2, 3});
        JUnitUtils.assertArrayEquals(new long[] {1, 2, 3}, new long[] {1, 2, 3});
        JUnitUtils.assertArrayEquals(new boolean[] {true, true}, new boolean[] {true, true});
    }

    @Test
    public void test_assertArrayEquals_negative() {
        assertThrows(AssertionError.class, () -> {
            JUnitUtils.assertArrayEquals(new int[] {1, 2, 3}, new int[] {2, 3});
        });
    }

    @Test
    public void test_exceptionAsPropertyMapWithMessageWithoutCause() {

        var ex = new Exception("foo");

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of("getClass", Exception.class.getName(), "getMessage", "foo"), map);
    }

    @Test
    public void test_exceptionAsPropertyMapWithMessageWithCause() {

        var ex = new Exception("foo", new IllegalArgumentException("Cause", new RuntimeException("Ops!")));

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of(
                "getClass", Exception.class.getName(),
                "getMessage", "foo",
                "getCause", Map.of(
                        "getClass", IllegalArgumentException.class.getName(),
                        "getMessage", "Cause",
                        "getCause", Map.of(
                                "getClass", RuntimeException.class.getName(),
                                "getMessage", "Ops!"
                        )
                )
        ), map);
    }

    @Test
    public void test_exceptionAsPropertyMapWithoutMessageWithCause() {

        var ex = new RuntimeException(null, new UnknownError("Ops!"));

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of(
                "getClass", RuntimeException.class.getName(),
                "getCause", Map.of(
                        "getMessage", "Ops!",
                        "getCause", ObjectMapper.NULL
                )
        ), map);
    }

    @Test
    public void test_exceptionAsPropertyMapWithoutMessageWithoutCause() {

        var ex = new UnsupportedOperationException();

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of("getClass", UnsupportedOperationException.class.getName()), map);
    }

    @Test
    public void test_exceptionAsProperty_CustomException() {

        var ex = new CustomException("Hello", Path.of(""), Optional.empty(), null);

        var map = JUnitUtils.exceptionAsPropertyMap(ex);

        assertEquals(Map.of(
                "getClass", CustomException.class.getName(),
                "getMessage", "Hello",
                "op", Map.of("get", ObjectMapper.NULL),
                "path2", Path.of("")
        ), map);
    }


    final static class CustomException extends Exception {

        CustomException(String message, Path path, Optional<Object> optional, Throwable cause) {
            super(message, cause);
            this.path = path;
            this.optional = optional;
        }

        Path path() {
            return path;
        }

        public Path path2() {
            return path;
        }

        public Optional<Object> op() {
            return optional;
        }

        private final Path path;
        private final Optional<Object> optional;

        private static final long serialVersionUID = 1L;

    }
}
