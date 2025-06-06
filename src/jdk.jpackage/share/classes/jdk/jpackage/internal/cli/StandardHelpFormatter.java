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

import static jdk.jpackage.internal.cli.StandardOption.currentPlatformOption;
import static jdk.jpackage.internal.cli.StandardOption.sharedOption;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.HelpFormatter.ConsoleOptionFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.ConsoleOptionGroupFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.OptionFormatter;
import jdk.jpackage.internal.cli.HelpFormatter.OptionGroupFormatter;

/**
 * jpackage help formatter
 */
final class StandardHelpFormatter {

    enum OptionGroup {
        SAMPLES("sample", shared()),

        GENERIC_OPTIONS("generic", shared().options(genericOptions())),

        RUNTIME_IMAGE_OPTIONS("runtime-image", shared().options(runtimeImageOptions())),

        APPLICATION_IMAGE_OPTIONS("app-image", shared().options(appImageOptions())),

        LAUNCHER_OPTIONS("launcher", shared().options(launcherOptions())),
        LAUNCHER_PLATFORM_OPTIONS("launcher-platform", platform().options(launcherOptions())),

        PACKAGE_OPTIONS("package", shared().options(nativePackageOptions())),
        PACKAGE_PLATFORM_OPTIONS("package-platform", platform().options(nativePackageOptions())),
        ;

        OptionGroup(String name, Builder builder) {
            value = new HelpFormatter.OptionGroup("help.option-group." + Objects.requireNonNull(name), builder.create());
        }

        HelpFormatter.OptionGroup value() {
            return value;
        }

        boolean isEmpty() {
            return value.options().isEmpty();
        }

        static Builder shared() {
            return new Builder(sharedOption());
        }

        static Builder platform() {
            return new Builder(Predicate.not(sharedOption()));
        }

        private static final class Builder {

            private Builder(Predicate<OptionSpec<?>> platformFilter) {
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
                    StandardOption.TYPE,
                    StandardOption.APP_VERSION,
                    StandardOption.COPYRIGHT,
                    StandardOption.DESCRIPTION,
                    StandardOption.HELP,
                    StandardOption.ICON,
                    StandardOption.NAME,
                    StandardOption.DEST,
                    StandardOption.TEMP_ROOT,
                    StandardOption.VENDOR,
                    StandardOption.VERBOSE,
                    StandardOption.VERSION
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> appImageOptions() {
            return Stream.of(
                    StandardOption.INPUT,
                    StandardOption.APP_CONTENT
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> runtimeImageOptions() {
            return Stream.of(
                    StandardOption.ADD_MODULES,
                    StandardOption.MODULE_PATH,
                    StandardOption.JLINK_OPTIONS,
                    StandardOption.PREDEFINED_RUNTIME_IMAGE
            ).map(OptionValue::getSpec);
        }

        private static Stream<? extends OptionSpec<?>> launcherOptions() {
            final var fromPropertyFile = StandardOption.launcherOptions().stream()
                    .map(Option::getSpec)
                    .filter(currentPlatformOption())
                    .filter(spec -> {
                        // Want options applicable to the app image bundling on the current platform.
                        return StandardBundlingOperation.narrow(spec.scope().stream())
                                .filter(StandardBundlingOperation.CREATE_APP_IMAGE::contains)
                                .findFirst().isPresent();
                    })
                    .filter(Predicate.not(genericOptions().toList()::contains));

            final Stream<? extends OptionSpec<?>> additional = Stream.of(
                    StandardOption.ADD_LAUNCHER
            ).map(OptionValue::getSpec);

            return Stream.concat(fromPropertyFile, additional);
        }

        private static Stream<? extends OptionSpec<?>> nativePackageOptions() {
            // The most straightforward way to get the list of these options is to
            // subtract the options from other groups from the list of all supported options.
            // This presumes this method is called after the other enum elements have been initialized.
            final var base = StandardOption.options().stream().map(Option::getSpec).filter(currentPlatformOption())
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

                private static final OptionName TYPE = StandardOption.TYPE.getSpec().name();
            });
        }

        private final HelpFormatter.OptionGroup value;
    }


    private static final class GroupFormatter implements OptionGroupFormatter {

        @Override
        public void formatHeader(String groupName, Consumer<CharSequence> sink) {
            groupFormatter.formatHeader(groupName, sink);
        }

        @Override
        public void formatBody(Iterable<? extends OptionSpec<?>> optionSpecs, Consumer<CharSequence> sink) {
            groupFormatter.formatBody(optionSpecs, sink);
        }

        @Override
        public void format(HelpFormatter.OptionGroup group, Consumer<CharSequence> sink) {
            formatHeader(group.name(), sink);
            if (group == OptionGroup.GENERIC_OPTIONS.value()) {
                optionSpecFormatter.format("@<filename>", Optional.empty(), "help.option.description.argument-file", sink);
            }
            formatBody(group.options(), sink);
        }

        private final OptionFormatter optionSpecFormatter = new ConsoleOptionFormatter(2, 10);
        private final OptionGroupFormatter groupFormatter = new ConsoleOptionGroupFormatter(optionSpecFormatter);
    }


    private StandardHelpFormatter() {}

    private static OptionSpec<Path> createRuntimeInstallerOptionSpec() {
        final var srcSpec = StandardOption.PREDEFINED_RUNTIME_IMAGE.getSpec();
        return new OptionSpec<>(srcSpec.names(), srcSpec.converter(), srcSpec.scope(),
                srcSpec.mergePolicy(), srcSpec.valuePattern(),
                "help.option.description.installer-runtime-image");
    }

    static final HelpFormatter INSTANCE;

    private static final OptionSpec<Path> RUNTIME_INSTALLER_RUNTIME_IMAGE = createRuntimeInstallerOptionSpec();

    static {
        final var builder = HelpFormatter.build().groupFormatter(new GroupFormatter());

        Stream.of(OptionGroup.values())
                .filter(Predicate.<OptionGroup>isEqual(OptionGroup.SAMPLES).or(Predicate.not(OptionGroup::isEmpty)))
                .map(OptionGroup::value)
                .forEach(builder::groups);

        INSTANCE = builder.create();
    }
}
