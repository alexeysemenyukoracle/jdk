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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class HelpTest {

    @Test
    public void testHelpOption() {
        final var cmdline = new JOptSimpleOptionsBuilder()
                .options(StandardOption.options())
                .helpOption(StandardOption.HELP)
                .create().apply(new String[] { "-h" }).orElseThrow().create();

        assertTrue(StandardOption.HELP.containsIn(cmdline));
    }

    @Test
    public void printHelp( ) {
        StandardHelpFormatter.INSTANCE.format(System.out::print);
    }

    @Test
    public void testOptionGroups( ) {

        // Test group names are unique.
        final var groups = Stream.of(StandardHelpFormatter.OptionGroup.values())
                .map(StandardHelpFormatter.OptionGroup::value)
                .collect(Collectors.toMap(HelpFormatter.OptionGroup::name, HelpFormatter.OptionGroup::options));

        // Names of all options supported on the current platform.
        final var allCurrentPlatformOptionNames = StandardOption.options().stream().map(Option::getSpec)
                .filter(StandardOption.currentPlatformOption())
                .map(OptionSpec::names).flatMap(Collection::stream)
                .sorted().toList();

        // Names of all options in the help groups.
        final var groupOptions = groups.values().stream().flatMap(Collection::stream)
                .map(OptionSpec::names).flatMap(Collection::stream)
                .sorted().toList();

        // Test that each option belongs to only one group except of `--runtime-image`
        groupOptions.stream().collect(Collectors.toMap(x -> x, x -> x, (a, b) -> {
            if (a.equals(StandardOption.PREDEFINED_RUNTIME_IMAGE.getSpec().name())) {
                return a;
            } else {
                throw new AssertionError(String.format("Option [%s] is included in multiple groups", a.name()));
            }
        }));

        // Test that each option is added to some group.
        assertEquals(allCurrentPlatformOptionNames, groupOptions.stream().distinct().toList());
    }
}
