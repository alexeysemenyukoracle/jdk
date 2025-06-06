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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class OptionsTest {

    @Test
    public void test_of() {
        var fooID = OptionIdentifier.createUnique();
        var barID = OptionIdentifier.createUnique();
        var buzID = OptionIdentifier.createUnique();

        var options = Options.of(Map.of(fooID, "Hello", barID, 100));

        expect().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
    }

    @Test
    public void test_of_empty() {
        var options = Options.of(Map.of());

        assertFalse(options.contains(OptionIdentifier.createUnique()));

        var barID = dummyOption("bar");
        assertFalse(options.contains(barID));
        assertFalse(options.contains(barID.getSpec().name()));
    }

    @Test
    public void test_of_Option() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");
        var buzID = OptionIdentifier.createUnique();

        var options = Options.of(Map.of(fooID, "Hello", barID, 100));

        expect().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
        assertFalse(options.contains(OptionName.of("foo")));
    }

    @Test
    public void test_copyWithDefaultValue() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello"));
        var expected = expect().add(fooID, "Hello");

        expected.apply(options);
        assertFalse(options.contains(barID.getSpec().name()));

        options = options.copyWithDefaultValue(barID, 89);
        expected.add(barID, 89).apply(options);
    }

    @Test
    public void test_copyWithDefaultValue_nop() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello", barID, 89));
        var expected = expect().add(fooID, "Hello").add(barID, 89);

        expected.apply(options);
        options = options.copyWithDefaultValue(barID, 75);
        expected.apply(options);
    }

    @Test
    public void test_copyWithParent() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello"));
        expect().add(fooID, "Hello").apply(options);
        assertFalse(options.contains(barID.getSpec().name()));

        var parentOptions = Options.of(Map.of(barID, 89));
        expect().add(barID, 89).apply(parentOptions);

        expect().add(fooID, "Hello").add(barID, 89).apply(options.copyWithParent(parentOptions));
    }

    @Test
    public void test_copyWithParent_override() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello", barID, 137));
        var expected = expect().add(fooID, "Hello").add(barID, 137);
        expected.apply(options);

        var parentOptions = Options.of(Map.of(barID, 89));
        expect().add(barID, 89).apply(parentOptions);

        expected.apply(options.copyWithParent(parentOptions));
    }

    private static Option dummyOption(String name) {
        return Option.create(dummyOptionSpec(name));
    }

    private static OptionSpec<String> dummyOptionSpec(String name) {
        return new OptionSpec<>(
                List.of(OptionName.of(name)),
                Optional.empty(),
                Set.of(new OptionScope() {}),
                OptionSpec.MergePolicy.USE_FIRST,
                Optional.empty(),
                "");
    }

    private static ExpectedOptions expect() {
        return new ExpectedOptions();
    }


    private static class ExpectedOptions {

        ExpectedOptions add(OptionIdentifier id, Object expectedValue) {
            expected.put(Objects.requireNonNull(id), Objects.requireNonNull(expectedValue));
            return this;
        }

        void apply(Options options) {
            for (var e : expected.entrySet()) {
                assertEquals(e.getValue(), options.find(e.getKey()).orElseThrow());
                if (e.getKey() instanceof Option option) {
                    for (var name : option.getSpec().names()) {
                        assertTrue(options.contains(name));
                    }
                }
            }
        }

        private final Map<OptionIdentifier, Object> expected = new HashMap<>();
    }
}
