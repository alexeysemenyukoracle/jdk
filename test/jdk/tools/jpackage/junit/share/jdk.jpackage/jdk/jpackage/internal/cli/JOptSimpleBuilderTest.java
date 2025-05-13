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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.model.BundlingOperation;
import org.junit.jupiter.api.Test;

public class JOptSimpleBuilderTest {

    @Test
    public void testInput() {
        final var pwd = Path.of("").toAbsolutePath();
        test().addOptionValue(
                build("input").shortName("i").ofDirectory(),
                pwd
        ).addArgs(
                "--input", "", "-i", pwd.toString()
        ).run();
    }

    @Test
    public void testArguments() {
        test().addOptionValue(
                build("arguments").ofStringList(),
                List.of("a", "b", "c", "de")
        ).addArgs(
                "--arguments", "a b c", "--arguments", "de"
        ).run();

        test().addOptionValue(
                build("arguments").withoutValueSeparator().ofStringList(),
                List.of("a b c", "de")
        ).addArgs(
                "--arguments", "a b c", "--arguments", "de"
        ).run();
    }

    private static OptionSpecBuilder build(String optionName) {
        return new OptionSpecBuilder().name(optionName).scope(new BundlingOperation() {});
    }

    private static TestBuilder test() {
        return new TestBuilder();
    }

    private static class TestBuilder {

        void run() {
            final var parser = JOptSimpleBuilder.createParser(
                    options.keySet().stream().map(OptionValue::asOption).map(Optional::orElseThrow).toList());

            final var cmdline = parser.apply(args.toArray(String[]::new));

            for (final var e : options.entrySet()) {
                final var optionValue = e.getKey();
                final var expectedValue = e.getValue();
                final var actualValue = optionValue.getFrom(cmdline);
                assertEquals(expectedValue, actualValue);
            }
        }

        TestBuilder addOptionValue(OptionValue<?> option, Object expectedValue) {
            options.put(option, expectedValue);
            return this;
        }

        TestBuilder addArgs(String...v) {
            return addArgs(List.of(v));
        }

        TestBuilder addArgs(Collection<String> v) {
            args.addAll(v);
            return this;
        }

        private final Map<OptionValue<?>, Object> options = new HashMap<>();
        private final List<String> args = new ArrayList<>();
    }
}
