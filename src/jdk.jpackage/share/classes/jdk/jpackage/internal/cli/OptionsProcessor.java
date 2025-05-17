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

import static jdk.jpackage.internal.cli.StandardOptionValue.ADD_MODULES;
import static jdk.jpackage.internal.cli.StandardOptionValue.JLINK_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOptionValue.MAC_APP_IMAGE_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOptionValue.MAC_INSTALLER_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOptionValue.MAC_SIGNING_KEY_NAME;
import static jdk.jpackage.internal.cli.StandardOptionValue.MAIN_JAR;
import static jdk.jpackage.internal.cli.StandardOptionValue.MODULE;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_RUNTIME_IMAGE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication;

public final class OptionsProcessor {

    public record BasicConfig(StandardBundlingOperation bundlingOperation,
            Optional<ExternalApplication> externalApp, Optional<Path> appImage,
            Optional<Path> runtimeImage, boolean isRuntimeInstaller) {

        public BasicConfig {
            Objects.requireNonNull(bundlingOperation);
            Objects.requireNonNull(externalApp);
            Objects.requireNonNull(appImage);
            Objects.requireNonNull(runtimeImage);
        }

        public boolean isBundlingAppImage() {
            return bundlingOperation.packageType().equals(AppImagePackageType.APP_IMAGE);
        }

        public boolean hasAppImage() {
            return appImage.isPresent();
        }

        public boolean hasRuntimeImage() {
            return runtimeImage.isPresent();
        }

        public BasicConfig(Options cmdline) {
            this(BUNDLING_OPERATION.getFrom(cmdline), EXTERNAL_APP.findIn(cmdline),
                    PREDEFINED_APP_IMAGE.findIn(cmdline),
                    PREDEFINED_RUNTIME_IMAGE.findIn(cmdline),
                    isRuntimeInstaller(cmdline));
        }

        private static boolean isRuntimeInstaller(Options cmdline) {
            return StandardBundlingOperation.CREATE_BUNDLE.contains(BUNDLING_OPERATION.getFrom(cmdline))
                    && PREDEFINED_RUNTIME_IMAGE.containsIn(cmdline)
                    && !PREDEFINED_APP_IMAGE.containsIn(cmdline)
                    && !MAIN_JAR.containsIn(cmdline)
                    && !MODULE.containsIn(cmdline);
        }

        private final static OptionValue<ExternalApplication> EXTERNAL_APP = OptionValue.create();
    }

    OptionsProcessor(Options cmdline, BundlingEnvironment bundlingEnv) {
        this(cmdline.setDefaultValue(BUNDLING_OPERATION.id(), BUNDLING_OPERATION.findIn(cmdline).or(() -> {
            return getBundlingOperation(cmdline, OperatingSystem.current(), bundlingEnv);
        }).orElseThrow(() -> {
            return I18N.buildConfigException("ERR_InvalidInstallerType", StandardOptionValue.TYPE.getFrom(cmdline)).create();
        })));
    }

    OptionsProcessor(Options cmdline) {
        this.cmdline = Objects.requireNonNull(cmdline);
        this.cfg = Objects.requireNonNull(new BasicConfig(cmdline));
    }

    OptionsProcessor(Options cmdline, BasicConfig cfg) {
        this.cmdline = Objects.requireNonNull(cmdline);
        this.cfg = Objects.requireNonNull(cfg);
    }

    Optional<ConfigException> validate() {
        final var outOfScopeOptionError = StandardOptionValue.options().stream()
                .filter(cmdline::contains)
                .map(Option::getSpec)
                .filter(matchBundlingOperation(cmdline).negate())
                .findFirst().map(this::onOutOfScopeOption);

        if (outOfScopeOptionError.isPresent()) {
            return outOfScopeOptionError;
        }

        final var mutualExclusiveOptionError = MUTUAL_EXCLUSIVE_OPTIONS.stream().map(v -> {
            return v.validate(cmdline);
        }).filter(Optional::isPresent).map(Optional::orElseThrow).findFirst();

        if (mutualExclusiveOptionError.isPresent()) {
            return mutualExclusiveOptionError;
        }

        if (cfg.isBundlingAppImage() && Stream.of(MODULE, MAIN_JAR).noneMatch(ov -> {
            return ov.containsIn(cmdline);
        })) {
            return Optional.of(I18N.buildConfigException("ERR_NoEntryPoint").create());
        }

        return Optional.empty();
    }

    private ConfigException onOutOfScopeOption(OptionSpec<?> optionSpec) {
        Objects.requireNonNull(optionSpec);

        if (optionSpec.scope().stream()
                .filter(StandardBundlingOperation.class::isInstance)
                .map(StandardBundlingOperation.class::cast)
                .map(StandardBundlingOperation::os).noneMatch(cfg.bundlingOperation().os()::equals)) {
            // The option is for different OS.
            return I18N.buildConfigException("ERR_UnsupportedOption", mapFormatArguments(optionSpec)).create();
        } else if (StandardBundlingOperation.SIGN_MAC_APP_IMAGE.equals(cfg.bundlingOperation())) {
            // The option is not applicable when signing a predefined app image.
            return I18N.buildConfigException("ERR_InvalidOptionWithAppImageSigning", mapFormatArguments(optionSpec)).create();
        } else if (StandardBundlingOperation.CREATE_NATIVE.contains(cfg.bundlingOperation()) && cfg.isRuntimeInstaller()) {
            // The option is not applicable when packaging of a runtime in a native bundle.
            return I18N.buildConfigException("ERR_NoInstallerEntryPoint", mapFormatArguments(optionSpec)).create();
        } else {
            return I18N.buildConfigException("ERR_InvalidTypeOption", cfg.bundlingOperation().packageTypeValue()).create();
        }
    }

    private static Optional<StandardBundlingOperation> getBundlingOperation(Options cmdline, OperatingSystem os, BundlingEnvironment env) {
        Objects.requireNonNull(cmdline);
        Objects.requireNonNull(os);
        Objects.requireNonNull(env);
        return StandardOptionValue.TYPE.findIn(cmdline).flatMap(packageType -> {
            // Filter bundling operations supported on the given OS.
            return StandardBundlingOperation.ofPlatform(os).filter(bundlingOperation -> {
                // Filter bundling operations producing bundles of the given package type.
                return bundlingOperation.packageType().equals(packageType);
            }).filter(bundlingOperation -> {
                // Filter bundling operations supported in the given environment.
                return env.supportedOperations().contains(bundlingOperation);
            }).findFirst();
        });
    }

    private static Predicate<OptionSpec<?>> matchBundlingOperation(Options cmdline) {
        return matchInScope(BUNDLING_OPERATION.getFrom(cmdline));
    }

    private static Predicate<OptionSpec<?>> matchInScope(OptionScope scope) {
        Objects.requireNonNull(scope);
        return optionSpec -> {
            return optionSpec.scope().contains(scope);
        };
    }

    private Object[] mapFormatArguments(Object... args) {
        return mapFormatArguments(cmdline, args);
    }

    private static Optional<OptionSpec<?>> asOptionSpec(Object v) {
        if (v instanceof OptionSpec optionSpec) {
            return Optional.of(optionSpec);
        } else if (v instanceof OptionValue<?> ov) {
            return asOptionSpec(ov.asOption().orElseThrow().getSpec());
        } else if (v instanceof Option option) {
            return asOptionSpec(option.getSpec());
        } else {
            return Optional.empty();
        }
    }

    private static Object[] mapFormatArguments(Options cmdline, Object... args) {
        return Stream.of(args).map(arg -> {
            return asOptionSpec(arg).map(optionSpec -> {
                return (Object)optionSpec.formatNameForErrorMessage(cmdline);
            }).orElse(args);
        }).toArray();
    }

    private static List<Option> asOptionList(OptionValue<?>... optionValues) {
        return Stream.of(optionValues).map(OptionValue::asOption).map(Optional::orElseThrow).toList();
    }

    private record MutualExclusiveOptions(List<Option> options, Function<Object[], ConfigException> createException) {
        MutualExclusiveOptions {
            options.forEach(Objects::requireNonNull);
            if (options.size() < 2) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(createException);
        }

        Optional<ConfigException> validate(Options cmdline) {
            final var detectedOptions = options.stream().filter(cmdline::contains).toList();
            if (detectedOptions.size() > 1) {
                final var errMesageformatArgs = detectedOptions.stream().map(Option::getSpec).map(optionSpec -> {
                    return optionSpec.formatNameForErrorMessage(cmdline);
                }).toArray();
                return Optional.of(createException.apply(errMesageformatArgs));
            } else {
                return Optional.empty();
            }
        }

        MutualExclusiveOptions(List<Option> options) {
            this(options, args -> {
                return I18N.buildConfigException("ERR_MutuallyExclusiveOptions", args).create();
            });
        }
    }

    private final Options cmdline;
    private final BasicConfig cfg;

    private final static OptionValue<StandardBundlingOperation> BUNDLING_OPERATION = OptionValue.create();

    private final static List<MutualExclusiveOptions> MUTUAL_EXCLUSIVE_OPTIONS;

    static {
        final List<MutualExclusiveOptions> config = new ArrayList<>();

        Stream.of(
                asOptionList(PREDEFINED_RUNTIME_IMAGE, PREDEFINED_APP_IMAGE),
                asOptionList(PREDEFINED_RUNTIME_IMAGE, ADD_MODULES),
                asOptionList(PREDEFINED_RUNTIME_IMAGE, JLINK_OPTIONS),
                asOptionList(MAC_SIGNING_KEY_NAME, MAC_APP_IMAGE_SIGN_IDENTITY),
                asOptionList(MAC_SIGNING_KEY_NAME, MAC_INSTALLER_SIGN_IDENTITY)
        ).map(MutualExclusiveOptions::new).forEach(config::add);

        config.add(new MutualExclusiveOptions(asOptionList(MODULE, MAIN_JAR), _ -> {
            return I18N.buildConfigException("ERR_BothMainJarAndModule").create();
        }));

        MUTUAL_EXCLUSIVE_OPTIONS = List.copyOf(config);
    }
}
