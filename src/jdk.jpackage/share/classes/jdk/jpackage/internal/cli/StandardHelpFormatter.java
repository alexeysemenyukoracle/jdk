/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static jdk.jpackage.internal.cli.StandardOptionValue.currentPlatformOption;
import static jdk.jpackage.internal.cli.StandardOptionValue.sharedOption;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class StandardHelpFormatter {

    enum OptionGroup {
        GENERIC_OPTIONS("generic", shared().options(genericOptions())),

        RUNTIME_IMAGE_OPTIONS("runtime-image", shared().options(runtimeImageOptions())),

        APPLICATION_IMAGE_OPTIONS("app-image", shared().options(appImageOptions())),

        LAUNCHER_OPTIONS("launcher", shared().options(launcherOptions())),
        LAUNCHER_PLATFORM_OPTIONS("launcher-platform", platform().options(launcherOptions())),

        PACKAGE_OPTIONS("package", shared().options(nativePackageOptions())),
        PACKAGE_PLATFORM_OPTIONS("package-platform", platform().options(nativePackageOptions())),
        ;

        OptionGroup(String name, Builder builder) {
            this.name = "help.option-group." + Objects.requireNonNull(name);
            this.optionSpecs = builder.create();
        }

        HelpFormatter.OptionGroup toOptionGroup() {
            return new HelpFormatter.OptionGroup(name, optionSpecs);
        }

        boolean isEmpty() {
            return optionSpecs.isEmpty();
        }

        static Builder shared() {
            return new Builder(sharedOption());
        }

        static Builder platform() {
            return new Builder(Predicate.not(sharedOption()));
        }

        private static final class Builder {

            Builder(Predicate<OptionSpec<?>> platformFilter) {
                this.platformFilter = Objects.requireNonNull(platformFilter);
            }

            List<OptionSpec<?>> create() {
                return optionSpecs.stream().filter(platformFilter).sorted(standardSorter()).toList();
            }

            Builder options(Stream<? extends OptionSpec<?>> v) {
                optionSpecs.addAll(v.toList());
                return this;
            }

            private final Predicate<OptionSpec<?>> platformFilter;
            private List<OptionSpec<?>> optionSpecs = new ArrayList<>();
        }

        private static Stream<? extends OptionSpec<?>> genericOptions() {
            return Stream.of(
                    StandardOptionValue.TYPE,
                    StandardOptionValue.APP_VERSION,
                    StandardOptionValue.COPYRIGHT,
                    StandardOptionValue.DESCRIPTION,
                    StandardOptionValue.HELP,
                    StandardOptionValue.ICON,
                    StandardOptionValue.NAME,
                    StandardOptionValue.DEST,
                    StandardOptionValue.TEMP_ROOT,
                    StandardOptionValue.VENDOR,
                    StandardOptionValue.VERBOSE,
                    StandardOptionValue.VERSION
            ).map(OptionValue::optionSpec);
        }

        private static Stream<? extends OptionSpec<?>> appImageOptions() {
            return Stream.of(
                    StandardOptionValue.INPUT,
                    StandardOptionValue.APP_CONTENT
            ).map(OptionValue::optionSpec);
        }

        private static Stream<? extends OptionSpec<?>> runtimeImageOptions() {
            return Stream.of(
                    StandardOptionValue.ADD_MODULES,
                    StandardOptionValue.MODULE_PATH,
                    StandardOptionValue.JLINK_OPTIONS,
                    StandardOptionValue.PREDEFINED_RUNTIME_IMAGE
            ).map(OptionValue::optionSpec);
        }

        private static Stream<? extends OptionSpec<?>> launcherOptions() {
            final var fromPropertyFile = StandardOptionValue.LAUNCHER_PROPERTIES.stream()
                    .map(OptionValue::optionSpec)
                    .filter(currentPlatformOption())
                    .filter(spec -> {
                        // Want options applicable to the app image bundling on the current platform.
                        return StandardBundlingOperation.narrow(spec.scope().stream())
                                .filter(StandardBundlingOperation.CREATE_APP_IMAGE::contains)
                                .findFirst().isPresent();
                    })
                    .filter(Predicate.not(genericOptions().toList()::contains));

            final Stream<? extends OptionSpec<?>> additional = Stream.of(
                    StandardOptionValue.ADD_LAUNCHER
            ).map(OptionValue::optionSpec);

            return Stream.concat(fromPropertyFile, additional);
        }

        private static Stream<? extends OptionSpec<?>> nativePackageOptions() {
            // The most straightforward way to get the list of these options is to
            // subtract the options from other groups from the list of all supported options.
            // This presumes this method is called after the other enum elements have been initialized.
            final var base = StandardOptionValue.options().stream().map(Option::getSpec).filter(currentPlatformOption())
                    .filter(Predicate.not(Stream.of(
                            genericOptions(),
                            appImageOptions(),
                            runtimeImageOptions(),
                            launcherOptions()).flatMap(x -> x).collect(Collectors.toSet())::contains));

            return Stream.concat(base, Stream.of(RUNTIME_INSTALLER_RUNTIME_IMAGE));
        }

        private static Comparator<OptionSpec<?>> standardSorter() {
            // Sort alphabetically by the first name except of the "--type" option, it goes first.
            return Comparator.comparing(OptionSpec::name, new Comparator<OptionName>() {

                @Override
                public int compare(OptionName o1, OptionName o2) {
                    if (o1.equals(TYPE) && o2.equals(TYPE)) {
                        return 0;
                    } else if (o1.equals(TYPE)) {
                        return -1;
                    } else if (o2.equals(TYPE)) {
                        return 1;
                    } else {
                        return o1.compareTo(o2);
                    }
                }

                private final static OptionName TYPE = StandardOptionValue.TYPE.optionSpec().name();
            });
        }

        private final String name;
        private final List<? extends OptionSpec<?>> optionSpecs;
    }

    private StandardHelpFormatter() {}

    private static OptionSpec<Path> createRuntimeInstallerOptionSpec() {
        final var srcSpec = StandardOptionValue.PREDEFINED_RUNTIME_IMAGE.optionSpec();
        return new OptionSpec<>(srcSpec.names(), srcSpec.converter(), srcSpec.scope(),
                srcSpec.mergePolicy(), srcSpec.valuePattern(),
                "help.option.description.installer-runtime-image");
    }

    final static HelpFormatter INSTANCE;

    private final static OptionSpec<Path> RUNTIME_INSTALLER_RUNTIME_IMAGE = createRuntimeInstallerOptionSpec();

    static {
        final var builder = HelpFormatter.build();

        Stream.of(OptionGroup.values())
                .filter(Predicate.not(OptionGroup::isEmpty))
                .map(OptionGroup::toOptionGroup)
                .forEach(builder::groups);

        INSTANCE = builder.create();
    }
}
