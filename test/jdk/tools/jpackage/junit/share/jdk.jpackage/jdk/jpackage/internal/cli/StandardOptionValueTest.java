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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.ConvertedOptionsBuilder;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.OptionsBuilder;
import jdk.jpackage.test.Comm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class StandardOptionValueTest {

    @Test
    public void testNames() {

        final var options = StandardOptionValue.options();

        final var expectedOptionCount = Stream.of(StandardOptionValue.class.getFields()).filter(f -> {
            return Modifier.isStatic(f.getModifiers());
        }).map(f -> {
            return toFunction(f::get).apply(null);
        }).filter(OptionValue.class::isInstance).map(OptionValue.class::cast).filter(ov -> ov.asOption().isPresent()).count();

        final var actualOptionCount = options.size();

        assertEquals(expectedOptionCount, actualOptionCount);

        // Test option names are unique. Let Collectors.toMap() do it.
        options.stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).map(OptionName::name).collect(toMap(x -> x, x -> x));
    }

    @ParameterizedTest
    @MethodSource
    public void testAddLauncherOption(AddLauncherTestSpec testSpec, @TempDir Path workDir) throws IOException {
        testSpec.test(workDir);
    }

    @Test
    public void printMarkdownOptionTable() {
        OptionSpecFormatter.print(OptionSpecFormatter::groupByOption);
    }


    record AddLauncherTestSpec(Optional<AdditionalLauncher> expected, List<String> expectedErrors, Optional<String> optionValue) {
        AddLauncherTestSpec {
            if (expected.isEmpty() == expectedErrors.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        void test(Path workDir) throws IOException {
            final List<String> args = new ArrayList<>();
            args.add(StandardOptionValue.ADD_LAUNCHER.getSpec().name().formatForCommandLine());
            optionValue.ifPresent(args::add);
            if (optionValue.isEmpty()) {
                var path = workDir.resolve(expected.orElseThrow().propertyFile());
                Files.createDirectories(path.getParent());
                Files.createFile(path);
                expected.map(v -> v.name() + "=" + path).ifPresent(args::add);
            }

            final var result = new JOptSimpleOptionsBuilder().optionValues(StandardOptionValue.ADD_LAUNCHER).create()
                    .apply(args.toArray(String[]::new))
                    .flatMap(OptionsBuilder::convertedOptions).map(ConvertedOptionsBuilder::create);

            if (expectedErrors.isEmpty()) {
                final var cmdline = result.orElseThrow();
                assertEquals(expected.map(List::of).orElseGet(List::of), StandardOptionValue.ADD_LAUNCHER.getFrom(cmdline).stream().map(v -> {
                    if (optionValue.isEmpty()) {
                        return new AdditionalLauncher(v.name(), workDir.relativize(v.propertyFile()));
                    } else {
                        return v;
                    }
                }).toList());
            } else {
                assertEquals(expectedErrors.stream().sorted().toList(),
                        result.errors().stream().map(Exception::getMessage).sorted().toList());
            }
        }

        final static class Builder {

            AddLauncherTestSpec create() {
                return new AddLauncherTestSpec(Optional.ofNullable(expected), expectedErrors, Optional.ofNullable(optionValue));
            }

            Builder expect(String name, String path) {
                expected = new AdditionalLauncher(name, Path.of(path));
                return this;
            }

            Builder expectErrors(String... v) {
                expectedErrors.addAll(List.of(v));
                return this;
            }

            Builder expectMalformedError(String v) {
                Objects.requireNonNull(v);
                return optionValue(v).expectErrors(I18N.format("error.paramater-add-launcher-malformed", v, "--add-launcher"));
            }

            Builder optionValue(String v) {
                optionValue = v;
                return this;
            }

            private AdditionalLauncher expected;
            private List<String> expectedErrors = new ArrayList<>();
            private String optionValue;
        }
    }


    private static Collection<AddLauncherTestSpec> testAddLauncherOption() {
        return Stream.of(
                buildAddLauncherTest().expect("foo", "some.properties"),
                buildAddLauncherTest().expect("foo", "a/b/some.properties").expect("bar", "="),
                buildAddLauncherTest().expect("a", "a.properties").expect("a", "b.properties"),
                buildAddLauncherTest().expectMalformedError("some"),
                buildAddLauncherTest().expectMalformedError(""),
                buildAddLauncherTest().expectMalformedError("="),
                buildAddLauncherTest().expectMalformedError("a="),
                buildAddLauncherTest().expectMalformedError("=a"),
                buildAddLauncherTest().expectMalformedError("=a"),
                // Not a path
                buildAddLauncherTest().optionValue("foo=*").expectErrors(I18N.format("error.paramater-add-launcher-not-file", "*", "foo")),
                // The path is a directory
                buildAddLauncherTest().optionValue("foo=.").expectErrors(I18N.format("error.paramater-add-launcher-not-file", ".", "foo")),
                // The path doesn't exist
                buildAddLauncherTest().optionValue("Foo=foo/bar/buz/111.z").expectErrors(I18N.format("error.paramater-add-launcher-not-file", "foo/bar/buz/111.z", "Foo"))
        ).map(AddLauncherTestSpec.Builder::create).toList();
    }

    private static AddLauncherTestSpec.Builder buildAddLauncherTest() {
        return new AddLauncherTestSpec.Builder();
    }


    private final static class OptionSpecFormatter {
        static void print(BiConsumer<Consumer<String>, Collection<? extends OptionSpec<?>>> printer) {
            printer.accept(System.out::println, StandardOptionValue.options().stream().map(Option::getSpec).toList());
        }

        static void groupByOption(Consumer<String>sink, Collection<? extends OptionSpec<?>> specs) {
            sink.accept("| Option | Scope | With runtime installer | With predefined app image | Merge");
            sink.accept("| --- | --- | :---: | :---: | :---: |");
            for (final var spec : specs.stream().sorted(Comparator.comparing(v -> { return v.name().name(); })).toList()) {
                final var mods = filterByType(spec.scope(), BundlingOperationModifier.class);
                sink.accept(String.format("| %s | %s | %s | %s | %s |",
                        formatOptionNames(spec),
                        format(filterByType(spec.scope(), BundlingOperationOptionScope.class)),
                        (mods.contains(BundlingOperationModifier.BUNDLE_RUNTIME) ? "x" : ""),
                        (mods.contains(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE) ? "x" : ""),
                        spec.mergePolicy()
                ));
            }
        }

        private static String formatOptionNames(OptionSpec<?> spec) {
            return spec.names().stream().map(OptionName::formatForCommandLine).collect(joining(", "));
        }

        private static String format(OptionScope op) {
            return Optional.ofNullable(KNOWN_BUNDLING_OPERATIONS.get(op)).orElseGet(op::toString);
        }

        private static String format(Set<OptionScope> ops) {
            final List<String> knownScopeLabels = new ArrayList<>();

            for (;;) {
                final var theOps = ops;
                final var bestMatchedKnownScope = KNOWN_SCOPES.keySet().stream().map(knownGroup -> {
                    return Comm.compare(theOps, knownGroup);
                }).filter(comm -> {
                    return comm.unique2().isEmpty();
                }).max(Comparator.comparing(comm -> {
                    return comm.common().size();
                }));

                if (bestMatchedKnownScope.isEmpty()) {
                    break;
                } else {
                    knownScopeLabels.add(KNOWN_SCOPES.get(bestMatchedKnownScope.orElseThrow().common()));
                    ops = bestMatchedKnownScope.orElseThrow().unique1();
                }
            }

            return Stream.concat(
                    knownScopeLabels.stream(),
                    ops.stream().map(OptionSpecFormatter::format)
            ).sorted().collect(joining(", "));
        }

        private static Set<OptionScope> filterByType(Collection<OptionScope> ops, Class<? extends OptionScope> filterType) {
            return ops.stream().filter(filterType::isInstance).collect(Collectors.toSet());
        }

        private final static Map<OptionScope, String> KNOWN_BUNDLING_OPERATIONS = Map.of(
                StandardBundlingOperation.CREATE_WIN_APP_IMAGE, "app-image-win",
                StandardBundlingOperation.CREATE_LINUX_APP_IMAGE, "app-image-linux",
                StandardBundlingOperation.CREATE_MAC_APP_IMAGE, "app-image-mac",
                StandardBundlingOperation.CREATE_WIN_EXE, "win-exe",
                StandardBundlingOperation.CREATE_WIN_MSI, "win-msi",
                StandardBundlingOperation.CREATE_LINUX_RPM, "linux-rpm",
                StandardBundlingOperation.CREATE_LINUX_DEB, "linux-deb",
                StandardBundlingOperation.CREATE_MAC_PKG, "mac-pkg",
                StandardBundlingOperation.CREATE_MAC_DMG, "mac-dmg",
                StandardBundlingOperation.SIGN_MAC_APP_IMAGE, "mac-sign"
        );

        private final static Map<Set<OptionScope>, String> KNOWN_SCOPES = Map.of(
                Set.copyOf(StandardBundlingOperation.CREATE_APP_IMAGE), "app-image",
                Set.copyOf(StandardBundlingOperation.WINDOWS), "win",
                Set.copyOf(StandardBundlingOperation.MACOS), "mac",
                Set.of(StandardBundlingOperation.CREATE_MAC_APP_IMAGE, StandardBundlingOperation.CREATE_MAC_DMG, StandardBundlingOperation.CREATE_MAC_PKG), "mac-bundle",
                Set.copyOf(StandardBundlingOperation.LINUX), "linux",
                Set.copyOf(StandardBundlingOperation.CREATE_NATIVE), "native-bundle",
                Set.copyOf(StandardBundlingOperation.CREATE_BUNDLE), "bundle"
        );
    }

}
