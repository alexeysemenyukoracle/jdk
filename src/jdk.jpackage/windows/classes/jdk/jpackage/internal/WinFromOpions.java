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

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.FromOptions.createApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.WinPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardOptionValue.ICON;
import static jdk.jpackage.internal.cli.StandardOptionValue.RESOURCE_DIR;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_CONSOLE_HINT;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_HELP_URL;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_INSTALLDIR_CHOOSER;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_MENU_GROUP;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_MENU_HINT;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_PER_USER_INSTALLATION;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_SHORTCUT_HINT;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_SHORTCUT_PROMPT;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_UPDATE_URL;
import static jdk.jpackage.internal.cli.StandardOptionValue.WIN_UPGRADE_UUID;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_DESKTOP;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_START_MENU;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.util.Map;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinExePackage;
import jdk.jpackage.internal.model.WinLauncher;
import jdk.jpackage.internal.model.WinLauncherMixin;
import jdk.jpackage.internal.model.WinMsiPackage;

final class WinFromOpions {

    static WinApplication createWinApplication(Options optionValues) {

        final var launcherFromOptions = new LauncherFromOptions();

        final var app = createApplicationBuilder(optionValues, toFunction(launcherOptionValues -> {

            final var launcher = launcherFromOptions.create(launcherOptionValues);

            final boolean isConsole = WIN_CONSOLE_HINT.getFrom(launcherOptionValues);

            final var shortcuts = Map.of(
                    WIN_SHORTCUT_DESKTOP, WIN_SHORTCUT_HINT,
                    WIN_SHORTCUT_START_MENU, WIN_MENU_HINT).entrySet().stream().filter(e -> {
                        final var hintOption = e.getValue();
                        return hintOption.findIn(launcherOptionValues).orElseGet(() -> hintOption.getFrom(optionValues));
                    }).map(Map.Entry::getKey).collect(toSet());

            return WinLauncher.create(launcher, new WinLauncherMixin.Stub(isConsole, shortcuts));

        }), APPLICATION_LAYOUT).create();

        return WinApplication.create(app);
    }

    static WinMsiPackage createWinMsiPackage(Options optionValues, WinApplication app) {

        final var superPkgBuilder = createPackageBuilder(optionValues, app, WIN_MSI);

        final var pkgBuilder = new WinMsiPackageBuilder(superPkgBuilder);

        WIN_HELP_URL.copyInto(optionValues, pkgBuilder::helpURL);
        pkgBuilder.isSystemWideInstall(!WIN_PER_USER_INSTALLATION.getFrom(optionValues));
        WIN_MENU_GROUP.copyInto(optionValues, pkgBuilder::startMenuGroupName);
        WIN_UPDATE_URL.copyInto(optionValues, pkgBuilder::updateURL);
        WIN_INSTALLDIR_CHOOSER.copyInto(optionValues, pkgBuilder::withInstallDirChooser);
        WIN_SHORTCUT_PROMPT.copyInto(optionValues, pkgBuilder::withShortcutPrompt);

        if (app.isService()) {
            RESOURCE_DIR.copyInto(optionValues, resourceDir -> {
                pkgBuilder.serviceInstaller(resourceDir.resolve("service-installer.exe"));
            });
        }

        WIN_UPGRADE_UUID.copyInto(optionValues, pkgBuilder::upgradeCode);

        return pkgBuilder.create();
    }

    static WinExePackage createWinExePackage(Options optionValues, WinMsiPackage msiPkg) {

        final var pkgBuilder = new WinExePackageBuilder(msiPkg);

        ICON.copyInto(optionValues, pkgBuilder::icon);

        return pkgBuilder.create();
    }
}
