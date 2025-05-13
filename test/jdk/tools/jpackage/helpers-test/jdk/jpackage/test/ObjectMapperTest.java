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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ObjectMapperTest {

    @Test
    public void test_String() {
        var om = ObjectMapper.blank().create();

        var map = om.map("foo");

        assertEquals("foo", map);
    }

    @Test
    public void test_int() {
        var om = ObjectMapper.blank().create();

        var map = om.map(100);

        assertEquals(100, map);
    }

    @Test
    public void test_null() {
        var om = ObjectMapper.blank().create();

        var map = om.map(null);

        assertNull(map);
    }

    @Test
    public void test_Object() {
        var obj = new Object();
        assertSame(obj, ObjectMapper.blank().create().map(obj));
        assertSame(obj, ObjectMapper.defaults().create().map(obj));
    }

    @Test
    public void test_empty_List() {
        var om = ObjectMapper.blank().create();

        var map = om.map(List.of());

        assertEquals(List.of(), map);
    }

    @Test
    public void test_List() {
        var om = ObjectMapper.blank().create();

        var map = om.map(List.of(100, "foo"));

        assertEquals(List.of(100, "foo"), map);
    }

    @Test
    public void test_empty_Map() {
        var om = ObjectMapper.blank().create();

        var map = om.map(Map.of());

        assertEquals(Map.of(), map);
    }

    @Test
    public void test_Map() {
        var om = ObjectMapper.blank().create();

        var map = om.map(Map.of(100, "foo"));

        assertEquals(Map.of(100, "foo"), map);
    }

    @Test
    public void test_MapSimple() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(Map.of(123, "foo", 321, new Simple.Stub("Hello", 567)));

        assertEquals(Map.of(123, "foo", 321, Map.of("a", "Hello", "b", 567)), map);
    }

    @Test
    public void test_ListSimple() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(List.of(100, new Simple.Stub("Hello", 567), "bar", new Simple() {}));

        assertEquals(List.of(100, Map.of("a", "Hello", "b", 567), "bar", Map.of("a", "foo", "b", 123)), map);
    }

    @Test
    public void test_Simple() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(new Simple() {});

        assertEquals(Map.of("a", "foo", "b", 123), map);
    }

    @Test
    public void test_Simple_null_property() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(new Simple.Stub(null, 123));

        assertEquals(Map.of("b", 123), map);
    }

    @Test
    public void test_Optional_String() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(Optional.of("foo"));

        assertEquals(Map.of("get", "foo"), map);
    }

    @Test
    public void test_Optional_empty() {
        var om = ObjectMapper.defaults().create();

        var map = om.map(Optional.empty());

        assertEquals(Map.of(), map);
    }

    interface Simple {
        default String a() {
            return "foo";
        }

        default int b() {
            return 123;
        }

        record Stub(String a, int b) implements Simple {}
    }
}
