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

import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionSpecTest {

    @Test
    public void test_otherNames() {
        assertEquals(toOptionNames("b", "foo"), buildSpec().names("a", "b", "foo").create().otherNames());
        assertEquals(toOptionNames(), buildSpec().names("a").create().otherNames());
        assertEquals(toOptionNames("b", "a"), buildSpec().names("a", "b", "a").create().otherNames());
    }

    @Test
    public void test_names() {
        assertEquals(toOptionNames("a", "b", "foo"), buildSpec().names("a", "b", "foo").create().names());
        assertEquals(toOptionNames("a"), buildSpec().names("a").create().names());
        assertEquals(toOptionNames("a", "b", "a"), buildSpec().names("a", "b", "a").create().names());
    }

    @Test
    public void test_name() {
        assertEquals(OptionName.of("a"), buildSpec().names("a", "b", "foo").create().name());
        assertEquals(OptionName.of("a"), buildSpec().names("a").create().name());
    }

    @Test
    public void test_valueType() {
        assertThrows(RuntimeException.class, buildSpec().names("foo").create()::valueType);
        assertEquals(String.class, OptionSpecTest.<String>buildSpec().names("foo").converter(converter(identityConv())).create().valueType());
        assertEquals(Path.class, OptionSpecTest.<Path>buildSpec().names("foo").converter(converter(pathConv())).create().valueType());
    }

    @Test
    public void test_hasValue() {
        assertFalse(buildSpec().names("foo").create().hasValue());
        assertTrue(OptionSpecTest.<String>buildSpec().names("foo").converter(converter(identityConv())).create().hasValue());
        assertTrue(OptionSpecTest.<Path>buildSpec().names("foo").converter(converter(pathConv())).create().hasValue());
    }

    @Test
    public void testInvalid() {
        IllegalArgumentException ex;

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> buildSpec().names().create());
        assertEquals("Empty name list", ex.getMessage());

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> buildSpec().names("foo").scope(Set.of()).create());
        assertEquals("Empty scope", ex.getMessage());

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            buildSpec().names("foo").mergePolicy(MergePolicy.CONCATENATE).create();
        });
        assertEquals("Invalid merge policy [" + MergePolicy.CONCATENATE + "] for type []", ex.getMessage());

        ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            OptionSpecTest.<String>buildSpec().names("foo")
                    .converter(converter(identityConv()))
                    .mergePolicy(MergePolicy.CONCATENATE)
                    .create();
        });
        assertEquals("Invalid merge policy [" + MergePolicy.CONCATENATE + "] for type [" + String.class.getName() + "]", ex.getMessage());
    }


    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_generateForEveryName(boolean hasValue) {

        final var names = List.of("a", "b", "foo");

        final OptionSpecBuilder<?> builder;
        if (hasValue) {
            builder = OptionSpecTest.<String>buildSpec().names(names).converter(converter(identityConv()));
        } else {
            builder = buildSpec().names(names);
        }

        final var optionSpecs = builder.create().copyForEveryName().toList();

        assertEquals(names.size(), optionSpecs.size());

        IntStream.range(0, names.size()).forEach(i -> {
            assertEquals(builder.names(names.get(i)).create(), optionSpecs.get(i));
        });
    }

    private static <T> OptionSpecBuilder<T> buildSpec() {
        return new OptionSpecBuilder<>();
    }

    private static List<OptionName> toOptionNames(Collection<String> names) {
        return names.stream().map(OptionName::of).toList();
    }

    private static List<OptionName> toOptionNames(String... names) {
        return toOptionNames(List.of(names));
    }

    private static <T> OptionValueConverter<T> converter(ValueConverter<T> conv) {
        return OptionValueConverter.<T>build()
                .exceptionFactory(OptionValueExceptionFactory.UNREACHABLE_EXCEPTION_FACTORY)
                .formatString("")
                .converter(conv)
                .create();
    }


    private static final class OptionSpecBuilder<T> {

        OptionSpec<T> create() {
            return new OptionSpec<>(
                    Optional.ofNullable(names).orElseGet(List::of),
                    Optional.ofNullable(converter),
                    Optional.ofNullable(scope).orElseGet(Set::of),
                    mergePolicy,
                    Optional.ofNullable(valuePattern),
                    description);
        }

        OptionSpecBuilder<T> names(String... v) {
            return names(List.of(v));
        }

        OptionSpecBuilder<T> description(String v) {
            description = v;
            return this;
        }

        OptionSpecBuilder<T> names(List<String> v) {
            names = Optional.ofNullable(v).map(OptionSpecTest::toOptionNames).orElse(null);
            return this;
        }

        OptionSpecBuilder<T> converter(OptionValueConverter<T> v) {
            converter = v;
            return this;
        }

        OptionSpecBuilder<T> scope(Set<OptionScope> v) {
            scope = v;
            return this;
        }

        OptionSpecBuilder<T> mergePolicy(MergePolicy v) {
            mergePolicy = v;
            return this;
        }

        OptionSpecBuilder<T> valuePattern(String v) {
            valuePattern = v;
            return this;
        }

        private List<OptionName> names;
        private OptionValueConverter<T> converter;
        private Set<OptionScope> scope = Set.of(new OptionScope() {});
        private MergePolicy mergePolicy = MergePolicy.USE_LAST;
        private String valuePattern;
        private String description = "";
    }
}
