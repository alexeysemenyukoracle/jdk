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

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_BUNDLE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_NATIVE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.MAC_SIGNING;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.fromOptionName;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.scope;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.BundlingOperation;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;

public final class StandardOptionValue {

    public final static OptionValue<PackageType> TYPE = build("type").shortName("t").valueConverter(new ValueConverter<PackageType>() {
        @Override
        public PackageType convert(String value) {
            if ("app-image".equals(value)) {
                return AppImagePackageType.APP_IMAGE;
            } else {
                return fromCmdLineType(value);
            }
        }

        @Override
        public Class<? extends PackageType> valueType() {
            return PackageType.class;
        }

        private static StandardPackageType fromCmdLineType(String type) {
            Objects.requireNonNull(type);
            return Stream.of(StandardPackageType.values()).filter(pt -> {
                return pt.suffix().substring(1).equals(type);
            }).findAny().get();
        }
    }).createOptionValue();

    public final static OptionValue<Path> INPUT = build("input", CREATE_APP_IMAGE).shortName("i").ofDirectory();

    public final static OptionValue<Path> DEST = build("dest").shortName("d").ofDirectory();

    public final static OptionValue<String> DESCRIPTION = build("description").ofString();

    public final static OptionValue<String> VENDOR = build("vendor").ofString();

    public final static OptionValue<String> APPCLASS = build("main-class").ofString();

    public final static OptionValue<String> NAME = build("name").shortName("n").ofString();

    public final static OptionValue<Boolean> VERBOSE = build("verbose").noValue();

    public final static OptionValue<Path> RESOURCE_DIR = build("resource-dir", scope().add(CREATE_BUNDLE).add(MAC_SIGNING).create()).ofDirectory();

    public final static OptionValue<List<String>> ARGUMENTS = build("arguments").ofStringList();

    public final static OptionValue<List<String>> JLINK_OPTIONS = build("jlink-options").ofStringList();

    public final static OptionValue<Path> ICON = build("icon").ofPath();

    public final static OptionValue<String> COPYRIGHT = build("copyright").ofString();

    public final static OptionValue<Path> LICENSE_FILE = build("license-file").ofPath();

    public final static OptionValue<String> VERSION = build("app-version").ofString();

    public final static OptionValue<String> ABOUT_URL = build("about-url").ofUrl();

    public final static OptionValue<List<String>> JAVA_OPTIONS = build("java-options").ofStringList();

    public final static OptionValue<List<Path>> APP_CONTENT = build("app-content").ofPathList();

    public final static OptionValue<List<Path>> FILE_ASSOCIATIONS = build("file-associations").ofPathList();

    public final static OptionValue<List<AdditionalLauncher>> ADD_LAUNCHER = build("add-launcher").valueConverter(new ValueConverter<AdditionalLauncher[]>() {
        @Override
        public AdditionalLauncher[] convert(String value) {
            var components = value.split("=", 2);
            if (components.length == 1) {
                components = new String[] { null, components[0] };
            }
            return new AdditionalLauncher[] { new AdditionalLauncher(components[0],
                    StandardValueConverter.pathConv().convert(components[1])) };
        }

        @Override
        public Class<? extends AdditionalLauncher[]> valueType() {
            return AdditionalLauncher[].class;
        }
    }).repetitive().<AdditionalLauncher[]>toOptionValueBuilder().to(List::of).defaultValue(List.of()).create();

    public final static OptionValue<Path> TEMP_ROOT = build("temp").ofDirectory();

    public final static OptionValue<Path> INSTALL_DIR = build("install-dir").ofPath();

    public final static OptionValue<Path> PREDEFINED_APP_IMAGE = build("app-image", scope().add(CREATE_NATIVE).add(SIGN_MAC_APP_IMAGE).create()).ofDirectory();

    public final static OptionValue<Path> PREDEFINED_RUNTIME_IMAGE = build("runtime-image").ofDirectory();

    public final static OptionValue<Path> MAIN_JAR = build("main-jar").ofPath();

    public final static OptionValue<String> MODULE = build("main-jar").shortName("m").ofString();

    public final static OptionValue<List<String>> ADD_MODULES = build("add-modules").ofStringList();

    public final static OptionValue<List<Path>> MODULE_PATH = build("module-path").ofPathList();

    public final static OptionValue<Boolean> LAUNCHER_AS_SERVICE = build("launcher-as-service").noValue();

    //
    // Linux-specific
    //

    public final static OptionValue<String> LINUX_RELEASE = build("linux-app-release", CREATE_NATIVE).ofString();

    public final static OptionValue<String> LINUX_BUNDLE_NAME = build("linux-package-name", CREATE_NATIVE).ofString();

    public final static OptionValue<String> LINUX_DEB_MAINTAINER = build("linux-deb-maintainer", CREATE_NATIVE).ofString();

    public final static OptionValue<String> LINUX_CATEGORY = build("linux-app-category", CREATE_NATIVE).ofString();

    public final static OptionValue<String> LINUX_RPM_LICENSE_TYPE = build("linux-rpm-license-type", CREATE_NATIVE).ofString();

    public final static OptionValue<String> LINUX_PACKAGE_DEPENDENCIES = build("linux-package-deps", CREATE_NATIVE).ofString();

    public final static OptionValue<Boolean> LINUX_SHORTCUT_HINT = build("linux-shortcut", CREATE_NATIVE).noValue();

    public final static OptionValue<String> LINUX_MENU_GROUP = build("linux-menu-group", CREATE_NATIVE).ofString();

    //
    // MacOS-specific
    //

    public final static OptionValue<List<Path>> DMG_CONTENT = build("mac-dmg-content").ofPathArray().to(List::of).defaultValue(List.of()).create();

    public final static OptionValue<Boolean> MAC_SIGN = build("mac-sign", MAC_SIGNING).shortName("s").noValue();

    public final static OptionValue<Boolean> MAC_APP_STORE = build("mac-app-store", MAC_SIGNING).noValue();

    public final static OptionValue<String> MAC_CATEGORY = build("mac-app-category").ofString();

    public final static OptionValue<String> MAC_BUNDLE_NAME = build("mac-package-name").ofString();

    public final static OptionValue<String> MAC_BUNDLE_IDENTIFIER = build("mac-package-identifier").ofString();

    public final static OptionValue<String> MAC_BUNDLE_SIGNING_PREFIX = build("mac-package-signing-prefix", MAC_SIGNING).ofString();

    public final static OptionValue<String> MAC_SIGNING_KEY_NAME = build("mac-signing-key-user-name", MAC_SIGNING).ofString();

    public final static OptionValue<String> MAC_APP_IMAGE_SIGN_IDENTITY = build("mac-app-image-sign-identity", MAC_SIGNING).ofString();

    public final static OptionValue<String> MAC_INSTALLER_SIGN_IDENTITY = build("mac-installer-sign-identity", CREATE_NATIVE).ofString();

    public final static OptionValue<Path> MAC_SIGNING_KEYCHAIN = build("mac-signing-keychain", MAC_SIGNING).ofPath();

    public final static OptionValue<Path> MAC_ENTITLEMENTS = build("mac-entitlements", MAC_SIGNING).ofPath();

    //
    // Windows-specific
    //

    public final static OptionValue<String> WIN_HELP_URL = build("win-help-url", CREATE_NATIVE).ofUrl();

    public final static OptionValue<String> WIN_UPDATE_URL = build("win-update-url", CREATE_NATIVE).ofUrl();

    public final static OptionValue<Boolean> WIN_MENU_HINT = build("win-menu", CREATE_NATIVE).noValue();

    public final static OptionValue<String> WIN_MENU_GROUP = build("win-menu-group", CREATE_NATIVE).ofString();

    public final static OptionValue<Boolean> WIN_SHORTCUT_HINT = build("win-shortcut", CREATE_NATIVE).noValue();

    public final static OptionValue<Boolean> WIN_SHORTCUT_PROMPT = build("win-shortcut-prompt", CREATE_NATIVE).noValue();

    public final static OptionValue<Boolean> WIN_PER_USER_INSTALLATION = build("win-per-user-install", CREATE_NATIVE).noValue();

    public final static OptionValue<Boolean> WIN_DIR_CHOOSER = build("win-dir-chooser", CREATE_NATIVE).noValue();

    public final static OptionValue<String> WIN_UPGRADE_UUID = build("win-upgrade-uuid", CREATE_NATIVE).ofString();

    public final static OptionValue<Boolean> WIN_CONSOLE_HINT = build("win-console", CREATE_NATIVE).noValue();

    static Set<Option> options() {
        return Stream.of(StandardOptionValue.class.getFields()).filter(f -> {
            return Modifier.isStatic(f.getModifiers());
        }).map(f -> {
            return toFunction(f::get).apply(null);
        }).filter(OptionValue.class::isInstance)
                .map(OptionValue.class::cast)
                .map(OptionValue::id)
                .map(Option.class::cast)
                .collect(toSet());
    }

    private static OptionSpecBuilder build(String name) {
        return new OptionSpecBuilder().name(name).scope(fromOptionName(name));
    }

    private static OptionSpecBuilder build(String name, Collection<BundlingOperation> baselineScope) {
        final var builder = build(name);
        builder.scope().map(scope -> {
            return scope.stream().filter(baselineScope::contains).collect(toSet());
        }).ifPresent(builder::scope);
        return builder;
    }
}
