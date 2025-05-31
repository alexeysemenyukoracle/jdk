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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.RuntimeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


public class PackagingPipelineTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildApplication(boolean withRuntimeBuilder, @TempDir Path workDir) throws ConfigException, PackagerException {

        final var app = createApp(TEST_LAYOUT_1, withRuntimeBuilder ? Optional.of(TestRuntimeBuilder.INSTANCE) : Optional.empty());
        final var env = buildEnv(workDir.resolve("build")).appImageDirFor(app).create();

        // Build application image in `env.appImageDir()` directory.
        final var builder = buildPipeline();
        if (app.runtimeBuilder().isEmpty()) {
            builder.task(BuildApplicationTaskID.RUNTIME).noaction().add();
        }

        builder.create().execute(env, app);

        TestLauncher.INSTANCE.verify((ApplicationLayout)env.appImageDirLayout());
        if (app.runtimeBuilder().isPresent()) {
            TestRuntimeBuilder.INSTANCE.verify(env.appImageDirLayout());
        }

        assertEquals(app.appImageDirName(), env.appImageDir().getFileName());
    }

    @Test
    void testCopyApplication(@TempDir Path workDir) throws ConfigException, PackagerException {

        final var srcApp = createApp(TEST_LAYOUT_1, TestRuntimeBuilder.INSTANCE);

        final var srcEnv = buildEnv(workDir.resolve("build")).appImageDirFor(srcApp).create();

        // Build application image in `srcEnv.appImageDir()` directory.
        buildPipeline().create().execute(srcEnv, srcApp);

        final var dstApp = createApp(TEST_LAYOUT_2, TestRuntimeBuilder.INSTANCE);

        final var dstEnv = buildEnv(workDir.resolve("build-2"))
                .appImageLayout(dstApp.imageLayout().resolveAt(workDir.resolve("a/b/c")))
                .create();

        // Copy application image from `srcEnv.appImageDir()` into `dstEnv.appImageDir()`
        // with layout transformation.
        // This test exercises flexibility of the packaging pipeline.
        final var builder = buildPipeline()
                .task(PrimaryTaskID.BUILD_APPLICATION_IMAGE).applicationAction(cfg -> {
                    assertSame(dstApp, cfg.app());
                    assertEquals(dstEnv.appImageDir(), cfg.env().appImageDirLayout().rootDirectory());
                    assertFalse(Files.exists(dstEnv.appImageDir()));
                    PackagingPipeline.copyAppImage(srcEnv.appImageDesc(), cfg.env().appImageDesc(), false);
                }).add();

        // Disable the default "build application image" actions of the tasks which
        // are the dependencies of `PrimaryTaskID.BUILD_APPLICATION_IMAGE` task as
        // their output will be overwritten in the custom action of this task.
        builder.taskGraphSnapshot().getAllTailsOf(PrimaryTaskID.BUILD_APPLICATION_IMAGE).forEach(taskId -> {
            builder.task(taskId).noaction().add();
        });

        builder.create().execute(dstEnv, dstApp);

        TestRuntimeBuilder.INSTANCE.verify(dstEnv.appImageDirLayout());
        TestLauncher.INSTANCE.verify((ApplicationLayout)dstEnv.appImageDirLayout());

        AppImageLayout.toPathGroup(dstEnv.appImageDirLayout()).paths().forEach(path -> {
            assertTrue(Files.exists(path));
        });

        assertEquals(Path.of("c"), dstEnv.appImageDir().getFileName());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildPackage(boolean transformLayout, @TempDir Path workDir) throws ConfigException, PackagerException, IOException {

        final var outputDir = workDir.resolve("bundles");
        final var pkg = createPackage(createApp(TEST_LAYOUT_1, TestRuntimeBuilder.INSTANCE));
        final var env = buildEnv(workDir.resolve("build")).appImageDirFor(pkg).create();

        buildPipeline().create().execute(env, pkg, outputDir);

        final var expected = createTestPackageFileContents(env.appImageDirLayout());
        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildRuntimeInstaller(boolean transformLayout, @TempDir Path workDir) throws ConfigException, PackagerException {
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildPackageFromExternalAppImage(boolean transformLayout) throws ConfigException, PackagerException {

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBuildPackageFromExternalAppImageNoCopy(boolean transformLayout) throws ConfigException, PackagerException {

    }

    private static Application createApp(ApplicationLayout appLayout) {
        return createApp(appLayout, Optional.empty());
    }

    private static Application createApp(ApplicationLayout appLayout, RuntimeBuilder runtimeBuilder) {
        return createApp(appLayout, Optional.of(runtimeBuilder));
    }

    private static Application createApp(ApplicationLayout appLayout, Optional<RuntimeBuilder> runtimeBuilder) {
        Objects.requireNonNull(appLayout);
        Objects.requireNonNull(runtimeBuilder);

        return new Application.Stub("foo", "My app", "1.0", "Acme", "copyright",
                Optional.empty(), List.of(), appLayout, runtimeBuilder, List.of(), Map.of());
    }

    private static Package createPackage(Application app) {
        return createPackage(app, Optional.empty());
    }

    private static Package createPackage(Application app, Path predefinedAppImage) {
        return createPackage(app, Optional.of(predefinedAppImage));
    }

    private static Package createPackage(Application app, Optional<Path> predefinedAppImage) {
        Objects.requireNonNull(app);
        Objects.requireNonNull(predefinedAppImage);
        return new Package.Stub(app, new PackageType() {}, "the-package", "My package",
                "1.0", Optional.empty(), Optional.empty(), predefinedAppImage, TEST_INSTALL_DIR);
    }

    private static BuildEnvBuilder buildEnv(Path rootDir) {
        return new BuildEnvBuilder(rootDir);
    }

    private static PackagingPipeline.Builder buildPipeline() {
        return PackagingPipeline.buildStandard()
                .task(BuildApplicationTaskID.APP_IMAGE_FILE).noaction().add()
                .task(BuildApplicationTaskID.LAUNCHERS).applicationAction(cfg -> {
                    TestLauncher.INSTANCE.create(cfg.resolvedLayout());
                }).add()
                .task(PrimaryTaskID.PACKAGE).packageAction(cfg -> {
                    var str = createTestPackageFileContents(cfg.resolvedLayout());
                    var packageFile = cfg.outputDir().resolve(cfg.pkg().packageFileNameWithSuffix());
                    Files.createDirectories(packageFile.getParent());
                    Files.writeString(packageFile, str);
                }).add();
    }

    private static String createTestPackageFileContents(AppImageLayout pkgLayout) throws IOException {
        var root = pkgLayout.rootDirectory();
        try (var walk = Files.walk(root)) {
            return walk.sorted().map(root::relativize).map(Path::toString).collect(Collectors.joining("\n"));
        }
    }


    private final static class TestRuntimeBuilder implements RuntimeBuilder {
        @Override
        public void create(AppImageLayout appImageLayout) {
            assertTrue(appImageLayout.isResolved());
            try {
                Files.createDirectories(appImageLayout.runtimeDirectory());
                Files.writeString(runtimeFile(appImageLayout), CONTENT);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static Path runtimeFile(AppImageLayout appImageLayout) {
            return appImageLayout.runtimeDirectory().resolve("my-runtime");
        }

        void verify(AppImageLayout appImageLayout) {
            try {
                assertEquals(CONTENT, Files.readString(runtimeFile(appImageLayout)));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private final static String CONTENT = "this is the runtime";

        static final TestRuntimeBuilder INSTANCE = new TestRuntimeBuilder();
    }


    private final static class TestLauncher {
        public void create(ApplicationLayout appLayout) {
            assertTrue(appLayout.isResolved());
            try {
                Files.createDirectories(appLayout.launchersDirectory());
                Files.writeString(launcherFile(appLayout), CONTENT);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        void verify(ApplicationLayout appLayout) {
            try {
                assertEquals(CONTENT, Files.readString(launcherFile(appLayout)));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static Path launcherFile(ApplicationLayout appLayout) {
            return appLayout.launchersDirectory().resolve("my-launcher");
        }

        private final static String CONTENT = "this is the launcher";

        static final TestLauncher INSTANCE = new TestLauncher();
    }


    private final static ApplicationLayout TEST_LAYOUT_1 = ApplicationLayout.build()
            .launchersDirectory("")
            .appDirectory("")
            .runtimeDirectory("runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private final static ApplicationLayout TEST_LAYOUT_2 = ApplicationLayout.build()
            .launchersDirectory("launchers")
            .appDirectory("app")
            .runtimeDirectory("foo/runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private final static Path TEST_INSTALL_DIR = Path.of("Acme/My app");
}
