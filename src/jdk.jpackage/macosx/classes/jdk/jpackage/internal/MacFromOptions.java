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

import static jdk.jpackage.internal.FromOptions.buildApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.ICON;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_CATEGORY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_IMAGE_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_STORE;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_IDENTIFIER;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_NAME;
import static jdk.jpackage.internal.cli.StandardOption.MAC_BUNDLE_SIGNING_PREFIX;
import static jdk.jpackage.internal.cli.StandardOption.MAC_DMG_CONTENT;
import static jdk.jpackage.internal.cli.StandardOption.MAC_ENTITLEMENTS;
import static jdk.jpackage.internal.cli.StandardOption.MAC_INSTALLER_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGN;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGNING_KEYCHAIN;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGNING_KEY_NAME;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.model.MacPackage.RUNTIME_BUNDLE_LAYOUT;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.ApplicationBuilder.MainLauncherStartupInfo;
import jdk.jpackage.internal.SigningIdentityBuilder.ExpiredCertificateException;
import jdk.jpackage.internal.SigningIdentityBuilder.StandardCertificateSelector;
import jdk.jpackage.internal.cli.StandardFaOption;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacLauncher;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class MacFromOptions {

    static MacApplication createMacApplication(Options optionValues) {
        return createMacApplicationInternal(optionValues).app();
    }

    static MacDmgPackage createMacDmgPackage(Options optionValues) {

        final var app = createMacApplicationInternal(optionValues);

        final var superPkgBuilder = createMacPackageBuilder(optionValues, app, MAC_DMG);

        final var pkgBuilder = new MacDmgPackageBuilder(superPkgBuilder);

        MAC_DMG_CONTENT.ifPresentIn(optionValues, pkgBuilder::dmgContent);

        return pkgBuilder.create();
    }

    static MacPkgPackage createMacPkgPackage(Options optionValues) {

        // This is over complicated to make "MacSignTest.testExpiredCertificate" test
        // pass.
        // If the same expired certificate is used for app image and native package
        // signing, the test expects native package signing configuration to fail.
        // This is counterintuitive, as app image signing should be configured first,
        // since the app image needs to be built first; however, that's the way it is.

        final boolean sign = MAC_SIGN.findIn(optionValues).orElse(false);
        final boolean appStore = MAC_APP_STORE.findIn(optionValues).orElse(false);

        final var appResult = Result.create(() -> createMacApplicationInternal(optionValues));

        final Optional<MacPkgPackageBuilder> pkgBuilder;
        if (appResult.hasValue()) {
            final var superPkgBuilder = createMacPackageBuilder(optionValues, appResult.orElseThrow(), MAC_PKG);
            pkgBuilder = Optional.of(new MacPkgPackageBuilder(superPkgBuilder));
        } else {
            rethrowIfNotExpiredCertificateException(appResult);
            pkgBuilder = Optional.empty();
        }

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(optionValues);
            MAC_INSTALLER_SIGN_IDENTITY.ifPresentIn(optionValues, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(optionValues).ifPresent(userName -> {
                final StandardCertificateSelector domain;
                if (appStore) {
                    domain = StandardCertificateSelector.APP_STORE_PKG_INSTALLER;
                } else {
                    domain = StandardCertificateSelector.PKG_INSTALLER;
                }

                signingIdentityBuilder.certificateSelector(StandardCertificateSelector.create(userName, domain));
            });

            if (pkgBuilder.isPresent()) {
                pkgBuilder.orElseThrow().signingBuilder(signingIdentityBuilder);
            } else {
                final var ex = Result.create(signingIdentityBuilder::create)
                        .firstError().map(Exception.class::cast).orElseGet(appResult.firstError()::orElseThrow);
                Log.error(ex.getMessage());
                rethrowUnchecked(ex);
            }
        }

        return pkgBuilder.orElseThrow().create();
    }

    private record ApplicationWithDetails(MacApplication app, Optional<ExternalApplication> externalApp) {
        ApplicationWithDetails {
            Objects.requireNonNull(app);
            Objects.requireNonNull(externalApp);
        }
    }

    private static ApplicationWithDetails createMacApplicationInternal(Options optionValues) {

        final var predefinedRuntimeLayout = PREDEFINED_RUNTIME_IMAGE.findIn(optionValues)
                .map(MacPackage::guessRuntimeLayout)
                .map(RuntimeLayout::unresolve).orElse(null);

        final var launcherFromOptions = new LauncherFromOptions().faMapper(MacFromOptions::createMacFa);

        final var superAppBuilder = buildApplicationBuilder()
                .runtimeLayout(RUNTIME_BUNDLE_LAYOUT)
                .predefinedRuntimeLayout(predefinedRuntimeLayout)
                .create(optionValues, toFunction(launcherOptionValues -> {
                    var launcher = launcherFromOptions.create(launcherOptionValues);
                    return MacLauncher.create(launcher);
                }), APPLICATION_LAYOUT);

        if (PREDEFINED_APP_IMAGE.containsIn(optionValues)) {
            // Set the main launcher start up info.
            // AppImageFile assumes the main launcher start up info is available when
            // it is constructed from Application instance.
            // This happens when jpackage signs predefined app image.
            final var mainLauncherStartupInfo = new MainLauncherStartupInfo(superAppBuilder.mainLauncherClassName().orElseThrow());
            final var launchers = superAppBuilder.launchers().orElseThrow();
            final var mainLauncher = ApplicationBuilder.overrideLauncherStartupInfo(launchers.mainLauncher(), mainLauncherStartupInfo);
            superAppBuilder.launchers(new ApplicationLaunchers(MacLauncher.create(mainLauncher), launchers.additionalLaunchers()));
        }

        final var app = superAppBuilder.create();

        final var appBuilder = new MacApplicationBuilder(app);

        PREDEFINED_APP_IMAGE.ifPresentIn(optionValues, predefinedAppImage -> {
            appBuilder.externalInfoPlistFile(predefinedAppImage.resolve("Contents/Info.plist"));
        });

        ICON.ifPresentIn(optionValues, appBuilder::icon);
        MAC_BUNDLE_NAME.ifPresentIn(optionValues, appBuilder::bundleName);
        MAC_BUNDLE_IDENTIFIER.ifPresentIn(optionValues, appBuilder::bundleIdentifier);
        MAC_APP_CATEGORY.ifPresentIn(optionValues, appBuilder::category);

        final boolean sign;
        final boolean appStore;

        if (OptionUtils.bundlingOperation(optionValues) == SIGN_MAC_APP_IMAGE) {
            final var appImageFileExtras = new MacAppImageFileExtras(superAppBuilder.externalApplication().orElseThrow());
            sign = appImageFileExtras.signed();
            appStore = appImageFileExtras.appStore();
        } else {
            sign = MAC_SIGN.findIn(optionValues).orElse(false);
            appStore = MAC_APP_STORE.findIn(optionValues).orElse(false);
        }

        appBuilder.appStore(appStore);

        if (sign) {
            final var signingIdentityBuilder = createSigningIdentityBuilder(optionValues);
            MAC_APP_IMAGE_SIGN_IDENTITY.ifPresentIn(optionValues, signingIdentityBuilder::signingIdentity);
            MAC_SIGNING_KEY_NAME.findIn(optionValues).ifPresent(userName -> {
                final StandardCertificateSelector domain;
                if (appStore) {
                    domain = StandardCertificateSelector.APP_STORE_APP_IMAGE;
                } else {
                    domain = StandardCertificateSelector.APP_IMAGE;
                }

                signingIdentityBuilder.certificateSelector(StandardCertificateSelector.create(userName, domain));
            });

            final var signingBuilder = new AppImageSigningConfigBuilder(signingIdentityBuilder);
            if (appStore) {
                signingBuilder.entitlementsResourceName("sandbox.plist");
            }

            app.mainLauncher().flatMap(Launcher::startupInfo).ifPresent(signingBuilder::signingIdentifierPrefix);
            MAC_BUNDLE_SIGNING_PREFIX.ifPresentIn(optionValues, signingBuilder::signingIdentifierPrefix);

            MAC_ENTITLEMENTS.ifPresentIn(optionValues, signingBuilder::entitlements);

            appBuilder.signingBuilder(signingBuilder);
        }

        return new ApplicationWithDetails(appBuilder.create(), superAppBuilder.externalApplication());
    }

    private static MacPackageBuilder createMacPackageBuilder(Options optionValues, ApplicationWithDetails app, PackageType type) {

        final var builder = new MacPackageBuilder(createPackageBuilder(optionValues, app.app(), type));

        app.externalApp()
                .map(MacAppImageFileExtras::new)
                .map(MacAppImageFileExtras::signed)
                .ifPresent(builder::predefinedAppImageSigned);

        return builder;
    }

    private static void rethrowIfNotExpiredCertificateException(Result<?> result) {
        final var ex = result.firstError().orElseThrow();

        if (ex instanceof ExpiredCertificateException) {
            return;
        }

        if (ex instanceof ExceptionBox box) {
            if (box.getCause() instanceof Exception cause) {
                rethrowIfNotExpiredCertificateException(Result.ofError(cause));
            }
        }

        rethrowUnchecked(ex);
    }

    private static SigningIdentityBuilder createSigningIdentityBuilder(Options optionValues) {
        final var builder = new SigningIdentityBuilder();
        MAC_SIGNING_KEYCHAIN.findIn(optionValues).map(Path::toString).ifPresent(builder::keychain);
        return builder;
    }

    private static MacFileAssociation createMacFa(Options optionValues, FileAssociation fa) {

        final var builder = new MacFileAssociationBuilder();

        StandardFaOption.MAC_CFBUNDLETYPEROLE.ifPresentIn(optionValues, builder::cfBundleTypeRole);
        StandardFaOption.MAC_LSHANDLERRANK.ifPresentIn(optionValues, builder::lsHandlerRank);
        StandardFaOption.MAC_NSSTORETYPEKEY.ifPresentIn(optionValues, builder::nsPersistentStoreTypeKey);
        StandardFaOption.MAC_NSDOCUMENTCLASS.ifPresentIn(optionValues, builder::nsDocumentClass);
        StandardFaOption.MAC_LSTYPEISPACKAGE.ifPresentIn(optionValues, builder::lsTypeIsPackage);
        StandardFaOption.MAC_LSDOCINPLACE.ifPresentIn(optionValues, builder::lsSupportsOpeningDocumentsInPlace);
        StandardFaOption.MAC_UIDOCBROWSER.ifPresentIn(optionValues, builder::uiSupportsDocumentBrowser);
        StandardFaOption.MAC_NSEXPORTABLETYPES.ifPresentIn(optionValues, builder::nsExportableTypes);
        StandardFaOption.MAC_UTTYPECONFORMSTO.ifPresentIn(optionValues, builder::utTypeConformsTo);

        return toFunction(builder::create).apply(fa);
    }
}
