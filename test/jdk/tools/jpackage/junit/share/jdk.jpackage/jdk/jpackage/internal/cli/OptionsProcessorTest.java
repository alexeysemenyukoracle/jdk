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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class OptionsProcessorTest {

    @Test
    public void testReadAdditionalLauncherProperties(@TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("description=bar", "type=msi"));

        final var result = readAdditionalLauncherProperties(propFile,
                StandardOption.TYPE, StandardOption.DESCRIPTION);

        assertTrue(result.hasValue());

        assertEquals(StandardPackageType.WIN_MSI, StandardOption.TYPE.getFrom(result.orElseThrow()));
        assertEquals("bar", StandardOption.DESCRIPTION.getFrom(result.orElseThrow()));

        assertFalse(StandardOption.INPUT.containsIn(result.orElseThrow()));
    }

    @ParameterizedTest
    @EnumSource(BooleanProperty.class)
    public void testReadAdditionalLauncherPropertiesBoolean(BooleanProperty value, @TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("launcher-as-service=" + value.propertyValue()));

        final var props = readAdditionalLauncherProperties(propFile,
                StandardOption.LAUNCHER_AS_SERVICE).orElseThrow();

        assertEquals(value.expectedValue(), StandardOption.LAUNCHER_AS_SERVICE.getFrom(props));
    }

    @Test
    public void testReadAdditionalLauncherProperties_negative(@TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("description=bar", "icon=*"));

        final var result = readAdditionalLauncherProperties(propFile,
                StandardOption.ICON, StandardOption.DESCRIPTION);

        assertFalse(result.hasValue());

        assertEquals(1, result.errors().size());
    }

    enum BooleanProperty {

        TRUE("true", true),
        TRUE_UPPERCASE_FIRST("True", true),
        TRUE_UPPERCASE_ALL("TRUE", true),
        TRUE_UPPERCASE_LAST("trueE", false),
        FALSE("false", false),
        FALSE_UPPERCASE("FALSE", false),
        EMPTY("", false),
        RANDOM("foo", false),
        TRUELISH("truee", false),
        FALSEISH("fals", false),
        ;

        BooleanProperty(String propertyValue, boolean expectedValue) {
            this.propertyValue = Objects.requireNonNull(propertyValue);
            this.expectedValue = Objects.requireNonNull(expectedValue);
        }

        String propertyValue() {
            return propertyValue;
        }

        boolean expectedValue() {
            return expectedValue;
        }

        private final String propertyValue;
        private final boolean expectedValue;
    }

    private static Result<Options> readAdditionalLauncherProperties(Path propFile, OptionValue<?>... options) {
        return OptionsProcessor.processPropertyFile(propFile,
                Stream.of(options).map(OptionValue::getOption).toList(),
                Optional.of(StandardOption::mapLauncherPropertyOptionSpec));
    }
}
