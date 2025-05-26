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

import static jdk.jpackage.internal.cli.TestUtils.configureConverter;
import static jdk.jpackage.internal.cli.TestUtils.configureValidator;
import jdk.jpackage.internal.cli.OptionValueConverter.ConverterException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionValueConverterTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test(boolean positive) {

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            return Integer.valueOf(str);
        }, Integer.class)).mutate(configureConverter()).create();

        if (positive) {
            final var token = StringToken.of("758");
            assertEquals(758, converter.convert(OptionName.of("number"), token).orElseThrow());
        } else {
            final var token = StringToken.of("foo");
            final var result = converter.convert(OptionName.of("number"), token);

            assertEquals(1, result.errors().size());

            final var ex = result.firstError().orElseThrow();

            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof NumberFormatException);
            assertEquals("Option --number: bad substring [foo] in string [foo]", ex.getMessage());
        }
    }

    @Test
    public void testConverterException() {

        final var exception = new RuntimeException("Always fail");

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            throw exception;
        }, Integer.class)).mutate(configureConverter()).create();

        final var token = StringToken.of("foo");
        final var ex = assertThrowsExactly(ConverterException.class, () -> converter.convert(OptionName.of("number"), token));

        assertSame(exception, ex.getCause());
    }

    @Test
    public void testValidatorExceptionTunneling() {

        final var exception = new RuntimeException("Always fail");

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            return Integer.valueOf(str);
        }, Object.class)).mutate(configureConverter()).validator(Validator.<Object, RuntimeException>build().predicate(_ -> {
            throw exception;
        }).mutate(configureValidator()).create()).create();

        final var token = StringToken.of("100");
        final var ex = assertThrowsExactly(ConverterException.class, () -> converter.convert(OptionName.of("number"), token));

        assertSame(exception, ex.getCause());
    }
}
