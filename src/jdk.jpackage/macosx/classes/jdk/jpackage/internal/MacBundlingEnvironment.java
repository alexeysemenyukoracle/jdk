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

import static jdk.jpackage.internal.MacFromOptions.createMacApplication;
import static jdk.jpackage.internal.MacFromOptions.createMacDmgPackage;
import static jdk.jpackage.internal.MacFromOptions.createMacPkgPackage;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_DMG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_PKG;

import java.util.Optional;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.util.Result;

public class MacBundlingEnvironment extends DefaultBundlingEnvironment {

    public MacBundlingEnvironment() {
        super(build()
                .defaultOperation(CREATE_MAC_DMG)
                .bundler(CREATE_MAC_APP_IMAGE, MacBundlingEnvironment::createAppImage)
                .bundler(CREATE_MAC_DMG, LazyLoad::dmgSysEnv, MacBundlingEnvironment::createDmdPackage)
                .bundler(CREATE_MAC_PKG, MacBundlingEnvironment::createPkgPackage));
    }

    private static void createDmdPackage(Options optionValues, MacDmgSystemEnvironment sysEnv) {

        final var pkg = createMacDmgPackage(optionValues);

        Packager.<MacDmgPackage>build().outputDir(OptionUtils.outputDir(optionValues))
                .pkg(pkg)
                .env(buildEnv().create(optionValues, pkg))
                .pipelineBuilderMutator((pipelineBuilder, env, _, outputDir) -> {
                    var packager = new MacDmgPackager(env, pkg, outputDir, sysEnv);
                    packager.applyToPipeline(pipelineBuilder);
                }).execute(MacPackagingPipeline.build(Optional.of(pkg)));
    }

    private static void createPkgPackage(Options optionValues) {

        final var pkg = createMacPkgPackage(optionValues);

        Packager.<MacPkgPackage>build().outputDir(OptionUtils.outputDir(optionValues))
                .pkg(pkg)
                .env(buildEnv().create(optionValues, pkg))
                .pipelineBuilderMutator((pipelineBuilder, env, _, outputDir) -> {
                    var packager = new MacPkgPackager(env, pkg, outputDir);
                    packager.applyToPipeline(pipelineBuilder);
                }).execute(MacPackagingPipeline.build(Optional.of(pkg)));
    }

    private static void createAppImage(Options optionValues) {

        final var app = createMacApplication(optionValues);

        createApplicationImage(optionValues, app, MacPackagingPipeline.build(Optional.empty()));
    }

    private static BuildEnvFromOptions buildEnv() {
        return new BuildEnvFromOptions()
                .predefinedAppImageLayout(APPLICATION_LAYOUT)
                .predefinedRuntimeImageLayout(MacPackage::guessRuntimeLayout);
    }

    private static final class LazyLoad {

        static Result<MacDmgSystemEnvironment> dmgSysEnv() {
            return DMG_SYS_ENV;
        }

        private static final Result<MacDmgSystemEnvironment> DMG_SYS_ENV = MacDmgSystemEnvironment.create();
    }
}
