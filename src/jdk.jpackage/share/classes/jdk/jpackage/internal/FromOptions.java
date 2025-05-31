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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.OptionUtils.isRuntimeInstaller;
import static jdk.jpackage.internal.cli.StandardOptionValue.ABOUT_URL;
import static jdk.jpackage.internal.cli.StandardOptionValue.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOptionValue.ADDITIONAL_LAUNCHERS;
import static jdk.jpackage.internal.cli.StandardOptionValue.ADD_MODULES;
import static jdk.jpackage.internal.cli.StandardOptionValue.APP_CONTENT;
import static jdk.jpackage.internal.cli.StandardOptionValue.COPYRIGHT;
import static jdk.jpackage.internal.cli.StandardOptionValue.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardOptionValue.INPUT;
import static jdk.jpackage.internal.cli.StandardOptionValue.INSTALL_DIR;
import static jdk.jpackage.internal.cli.StandardOptionValue.JLINK_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOptionValue.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardOptionValue.LICENSE_FILE;
import static jdk.jpackage.internal.cli.StandardOptionValue.MODULE_PATH;
import static jdk.jpackage.internal.cli.StandardOptionValue.NAME;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.VENDOR;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import jdk.jpackage.internal.cli.OptionIdentifier;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;

final class FromOptions {

    static ApplicationBuilder createApplicationBuilder(Options optionValues,
            Function<Options, Launcher> launcherMapper,
            ApplicationLayout appLayout) {
        return createApplicationBuilder(optionValues, launcherMapper, appLayout, Optional.of(RuntimeLayout.DEFAULT));
    }

    static ApplicationBuilder createApplicationBuilder(Options optionValues,
            Function<Options, Launcher> launcherMapper,
            ApplicationLayout appLayout, Optional<RuntimeLayout> predefinedRuntimeLayout) {

        final var appBuilder = new ApplicationBuilder();

        NAME.copyInto(optionValues, appBuilder::name);
        DESCRIPTION.copyInto(optionValues, appBuilder::description);
// TODO        appBuilder.version(APP_VERSION.fetchFrom(params));
        VENDOR.copyInto(optionValues, appBuilder::vendor);
        COPYRIGHT.copyInto(optionValues, appBuilder::copyright);
        INPUT.copyInto(optionValues, appBuilder::srcDir);
        APP_CONTENT.copyInto(optionValues, appBuilder::contentDirs);

        final var isRuntimeInstaller = isRuntimeInstaller(optionValues);

        final var predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.findIn(optionValues);

        final var predefinedRuntimeDirectory = predefinedRuntimeLayout.flatMap(
                layout -> predefinedRuntimeImage.map(layout::resolveAt)).map(RuntimeLayout::runtimeDirectory);

        if (isRuntimeInstaller) {
            appBuilder.appImageLayout(predefinedRuntimeLayout.orElseThrow());
        } else {
            appBuilder.appImageLayout(appLayout);

            if (PREDEFINED_APP_IMAGE.containsIn(optionValues)) {
                final var appImageFile = AppImageFile.load(PREDEFINED_APP_IMAGE.getFrom(optionValues), appLayout);
                appBuilder.initFromExernalApplication(appImageFile, launcherInfo -> {
                    return launcherMapper.apply(mapLauncherInfo(optionValues, launcherInfo));
                });
            } else {
                final var launchers = createLaunchers(optionValues, launcherMapper);

                final var runtimeBuilderBuilder = new RuntimeBuilderBuilder();

                MODULE_PATH.copyInto(optionValues, runtimeBuilderBuilder::modulePath);

                predefinedRuntimeDirectory.ifPresentOrElse(runtimeBuilderBuilder::forRuntime, () -> {
                    final var startupInfos = launchers.asList().stream()
                            .map(Launcher::startupInfo)
                            .map(Optional::orElseThrow).toList();
                    final var jlinkOptionsBuilder = runtimeBuilderBuilder.forNewRuntime(startupInfos);
                    ADD_MODULES.findIn(optionValues).map(Set::copyOf).ifPresent(jlinkOptionsBuilder::addModules);
                    JLINK_OPTIONS.copyInto(optionValues, jlinkOptionsBuilder::options);
                    jlinkOptionsBuilder.appy();
                });

                appBuilder.launchers(launchers).runtimeBuilder(runtimeBuilderBuilder.create());
            }
        }

        return appBuilder;
    }

    static PackageBuilder createPackageBuilder(Options optionValues, Application app, PackageType type) {

        final var builder = new PackageBuilder(app, type);

        NAME.copyInto(optionValues, builder::name);
        DESCRIPTION.copyInto(optionValues, builder::description);
        APP_VERSION.copyInto(optionValues, builder::version);
        ABOUT_URL.copyInto(optionValues, builder::aboutURL);
        LICENSE_FILE.copyInto(optionValues, builder::licenseFile);
        PREDEFINED_APP_IMAGE.copyInto(optionValues, builder::predefinedAppImage);
        INSTALL_DIR.copyInto(optionValues, builder::installDir);

        return builder;
    }

    private static ApplicationLaunchers createLaunchers(Options optionValues, Function<Options, Launcher> launcherMapper) {
        var launchers = ADDITIONAL_LAUNCHERS.getFrom(optionValues);

        var mainLauncher = launcherMapper.apply(optionValues);
        var additionalLaunchers = launchers.stream().map(launcherMapper).toList();

        return new ApplicationLaunchers(mainLauncher, additionalLaunchers);
    }

    private static Options mapLauncherInfo(LauncherInfo launcherInfo) {
        Map<OptionIdentifier, ? super Object> optionValues = new HashMap<>();
        optionValues.put(NAME.id(), launcherInfo.name());
        optionValues.put(LAUNCHER_AS_SERVICE.id(), launcherInfo.service());
        for (var e : launcherInfo.extra().entrySet()) {
            optionValues.put(OptionIdentifier.of(e.getKey()), e.getValue());
        }
        return Options.of(optionValues);
    }
    
    private static Options mapLauncherInfo(Options optionValues, LauncherInfo launcherInfo) {
        return Options.concat(mapLauncherInfo(launcherInfo), optionValues);
    }

}
