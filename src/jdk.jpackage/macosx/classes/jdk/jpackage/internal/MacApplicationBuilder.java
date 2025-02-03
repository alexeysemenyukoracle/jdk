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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacApplicationMixin;
import jdk.jpackage.internal.model.SigningConfig;

final class MacApplicationBuilder {

    MacApplicationBuilder(Application app) {
        this.app = Objects.requireNonNull(app);
    }

    private MacApplicationBuilder(MacApplicationBuilder other) {
        this(other.app);
        icon = other.icon;
        bundleName = other.bundleName;
        bundleIdentifier = other.bundleIdentifier;
        category = other.category;
        externalInfoPlistFile = other.externalInfoPlistFile;
    }

    MacApplicationBuilder icon(Path v) {
        icon = v;
        return this;
    }

    MacApplicationBuilder bundleName(String v) {
        bundleName = v;
        return this;
    }

    MacApplicationBuilder bundleIdentifier(String v) {
        bundleIdentifier = v;
        return this;
    }

    MacApplicationBuilder category(String v) {
        category = v;
        return this;
    }

    MacApplicationBuilder externalInfoPlistFile(Path v) {
        externalInfoPlistFile = v;
        return this;
    }

    MacApplicationBuilder signingBuilder(SigningConfigBuilder v) {
        signingBuilder = v;
        return this;
    }

    MacApplication create() throws ConfigException {
        if (externalInfoPlistFile != null) {
            return createCopyForExternalInfoPlistFile().create();
        }

        final var mixin = new MacApplicationMixin.Stub(validatedIcon(), validatedBundleName(),
                validatedBundleIdentifier(), validatedCategory(), createSigningConfig());

        return MacApplication.create(app, mixin);
    }

    static boolean isValidBundleIdentifier(String id) {
        for (int i = 0; i < id.length(); i++) {
            char a = id.charAt(i);
            // We check for ASCII codes first which we accept. If check fails,
            // check if it is acceptable extended ASCII or unicode character.
            if ((a >= 'A' && a <= 'Z') || (a >= 'a' && a <= 'z')
                    || (a >= '0' && a <= '9') || (a == '-' || a == '.')) {
                continue;
            }
            return false;
        }
        return true;
    }

    private MacApplicationBuilder createCopyForExternalInfoPlistFile() throws ConfigException {
        try {
            final var plistFile = AppImageInfoPListFile.loadFromInfoPList(externalInfoPlistFile);

            final var builder = new MacApplicationBuilder(this);

            builder.externalInfoPlistFile(null);

            if (builder.bundleName == null) {
                builder.bundleName(plistFile.bundleName());
            }

            if (builder.bundleIdentifier == null) {
                builder.bundleIdentifier(plistFile.bundleIdentifier());
            }

            if (builder.category == null) {
                builder.category(plistFile.category());
            }

            return builder;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (Exception ex) {
            throw I18N.buildConfigException("message.app-image-requires-identifier")
                    .advice("message.app-image-requires-identifier.advice")
                    .cause(ex)
                    .create();
        }
    }

    private Optional<SigningConfig> createSigningConfig() throws ConfigException {
        if (signingBuilder != null) {
            return Optional.of(signingBuilder.create());
        } else {
            return Optional.empty();
        }
    }

    private String validatedBundleName() throws ConfigException {
        final var value = Optional.ofNullable(bundleName).orElseGet(() -> {
            final var appName = app.name();
            if (appName.length() > MAX_BUNDLE_NAME_LENGTH) {
                return appName.substring(0, MAX_BUNDLE_NAME_LENGTH);
            } else {
                return appName;
            }
        });

        if (value.length() > MAX_BUNDLE_NAME_LENGTH) {
            Log.error(I18N.format("message.bundle-name-too-long-warning", "--mac-package-name", value));
        }

        return value;
    }

    private String validatedBundleIdentifier() throws ConfigException {
        final var value = Optional.ofNullable(bundleIdentifier).orElseGet(() -> {
            return app.mainLauncher()
                    .flatMap(Launcher::startupInfo)
                    .map(LauncherStartupInfo::simpleClassName)
                    .orElseGet(app::name);
        });

        if (!isValidBundleIdentifier(value)) {
            throw I18N.buildConfigException("message.invalid-identifier", value)
                    .advice("message.invalid-identifier.advice")
                    .create();
        }

        return value;
    }

    private String validatedCategory() throws ConfigException {
        return Optional.ofNullable(category).orElseGet(DEFAULTS::category);
    }

    private Optional<Path> validatedIcon() throws ConfigException {
        if (icon != null) {
            LauncherBuilder.validateIcon(icon);
        }

        return Optional.ofNullable(icon);
    }

    private record Defaults(String category) {
    }

    private Path icon;
    private String bundleName;
    private String bundleIdentifier;
    private String category;
    private Path externalInfoPlistFile;
    private SigningConfigBuilder signingBuilder;

    private final Application app;

    private static final Defaults DEFAULTS = new Defaults("utilities");

    private static final int MAX_BUNDLE_NAME_LENGTH = 16;
}
