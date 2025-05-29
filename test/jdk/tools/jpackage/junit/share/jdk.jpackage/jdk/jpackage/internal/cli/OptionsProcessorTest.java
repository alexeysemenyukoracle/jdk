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
import java.util.stream.Stream;
import jdk.jpackage.internal.model.StandardPackageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OptionsProcessorTest {

    @Test
    public void test_readAdditionalLauncherProperties(@TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("description=bar", "type=msi"));

        final var result = OptionsProcessor.readAdditionalLauncherProperties(propFile, 
                Stream.of(StandardOptionValue.TYPE, StandardOptionValue.DESCRIPTION).map(OptionValue::getOption).toList());

        assertTrue(result.hasValue());

        assertEquals(StandardPackageType.WIN_MSI, StandardOptionValue.TYPE.getFrom(result.orElseThrow()));
        assertEquals("bar", StandardOptionValue.DESCRIPTION.getFrom(result.orElseThrow()));
        
        assertFalse(StandardOptionValue.INPUT.containsIn(result.orElseThrow()));
    }
    
    @Test
    public void test_readAdditionalLauncherProperties_negative(@TempDir Path workDir) throws IOException {
        final var propFile = workDir.resolve("launcher.properties");
        Files.write(propFile, List.of("description=bar", "icon=*"));

        final var result = OptionsProcessor.readAdditionalLauncherProperties(propFile, 
                Stream.of(StandardOptionValue.ICON, StandardOptionValue.DESCRIPTION).map(OptionValue::getOption).toList());

        assertFalse(result.hasValue());

        assertEquals(1, result.errors().size());
    }
}
