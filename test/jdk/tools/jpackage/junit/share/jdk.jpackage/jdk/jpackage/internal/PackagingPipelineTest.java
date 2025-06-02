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

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.CopyAppImageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.PackagingPipeline.PrimaryTaskID;
import jdk.jpackage.internal.PackagingPipeline.TaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.RuntimeBuilder;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.CompositeProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(env, app);
        }));

        List<TaskID> expectedActions = new ArrayList<>();
        if (app.runtimeBuilder().isPresent()) {
            expectedActions.add(BuildApplicationTaskID.RUNTIME);
        }
        expectedActions.addAll(List.of(BuildApplicationTaskID.LAUNCHERS, BuildApplicationTaskID.CONTENT));

        assertEquals(expectedActions, executedTaskActions);
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
                    PackagingPipeline.copyAppImage(srcEnv.appImageDirLayout(), cfg.env().appImageDirLayout(), false);
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

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(dstEnv, dstApp);
        }));

        assertEquals(List.of(PrimaryTaskID.BUILD_APPLICATION_IMAGE), executedTaskActions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCreatePackage(boolean overrideLayout, @TempDir Path workDir) throws ConfigException, PackagerException, IOException {

        final var outputDir = workDir.resolve("bundles");
        final var pkg = buildPackage(createApp(TEST_LAYOUT_1, TestRuntimeBuilder.INSTANCE)).create();
        final var env = buildEnv(workDir.resolve("build")).appImageDirFor(pkg).create();

        final var builder = buildPipeline();
        if (overrideLayout) {
            builder.appImageLayoutForPackaging(_ -> TEST_LAYOUT_2);
        }

        // Will create an app image in `env.appImageDir()` directory
        // with `pkg.packageLayout()` layout or `TEST_LAYOUT_2`.
        // Will convert the created app image into a package.
        builder.create().execute(env, pkg, outputDir);

        final String expected;
        if (overrideLayout) {
            expected = createTestPackageFileContents(TEST_LAYOUT_2.resolveAt(env.appImageDir()));
        } else {
            expected = createTestPackageFileContents(env.appImageDirLayout());
        }

        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));

        assertEquals(expected, actual);
        System.out.println(String.format("testCreatePackage(%s):\n---\n%s\n---", overrideLayout, actual));

        var executedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(env, pkg, outputDir);
        }));

        assertEquals(List.of(BuildApplicationTaskID.RUNTIME, BuildApplicationTaskID.LAUNCHERS,
                BuildApplicationTaskID.CONTENT, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT,
                PrimaryTaskID.PACKAGE), executedTaskActions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCreateRuntimeInstaller(boolean transformLayout, @TempDir Path workDir) throws ConfigException, PackagerException, IOException {

        // Create a runtime image in `env.appImageDir()` directory.
        final var env = buildEnv(workDir.resolve("build"))
                .appImageLayout(RuntimeLayout.DEFAULT)
                .appImageDir(workDir.resolve("rt"))
                .create();
        TestRuntimeBuilder.INSTANCE.create(env.appImageDirLayout());

        final var pkgBuilder = buildPackage(createApp(RuntimeLayout.DEFAULT));
        if (transformLayout) {
            // Use a custom package app image layout with the default installation directory.
            pkgBuilder.packageLayout(TEST_LAYOUT_2.resolveAt(pkgBuilder.create().relativeInstallDir()).emptyRootDirectory());
        }

        createAndVerifyPackage(buildPipeline(), pkgBuilder.create(), env, workDir.resolve("bundles"),
                String.format("testCreateRuntimeInstaller(%s)", transformLayout),
                CopyAppImageTaskID.COPY, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT, PrimaryTaskID.PACKAGE);
    }

    private enum ExternalAppImageMode {
        COPY_FROM_BUILD_ENV_APP_IMAGE,
        COPY,
        TRANSFORM_FROM_BUILD_ENV_APP_IMAGE,
        TRANSFORM;

        final static Set<ExternalAppImageMode> ALL_TRANSFORM = Set.of(
                TRANSFORM_FROM_BUILD_ENV_APP_IMAGE, TRANSFORM);

        final static Set<ExternalAppImageMode> ENV_APP_IMAGE = Set.of(
                COPY_FROM_BUILD_ENV_APP_IMAGE, TRANSFORM_FROM_BUILD_ENV_APP_IMAGE);
    }

    @ParameterizedTest
    @EnumSource(ExternalAppImageMode.class)
    void testCreatePackageFromExternalAppImage(ExternalAppImageMode mode, @TempDir Path workDir) throws ConfigException, PackagerException, IOException {

        final var appLayout = TEST_LAYOUT_1.resolveAt(Path.of("boom")).emptyRootDirectory();

        final BuildEnv env;
        final Path predefinedAppImage;
        if (ExternalAppImageMode.ENV_APP_IMAGE.contains(mode)) {
            // External app image is stored in the build env app image directory.
            env = setupBuildEnvForExternalAppImage(workDir);
            predefinedAppImage = env.appImageDir();
        } else {
            // External app image is stored outside of the build env app image directory
            // and should have the same layout as the app's app image layout.
            env = buildEnv(workDir.resolve("build"))
                    .appImageDir(workDir)
                    // Always need some app image layout.
                    .appImageLayout(new AppImageLayout.Stub(Path.of("")))
                    .create();
            final var externalAppImageLayout = appLayout.resolveAt(workDir.resolve("app-image"));
            TestRuntimeBuilder.INSTANCE.create(externalAppImageLayout);
            TestLauncher.INSTANCE.create(externalAppImageLayout);
            predefinedAppImage = externalAppImageLayout.rootDirectory();
        }

        final var pkgBuilder = buildPackage(createApp(appLayout)).predefinedAppImage(predefinedAppImage);
        if (ExternalAppImageMode.ALL_TRANSFORM.contains(mode)) {
            // Use a custom package app image layout with the default installation directory.
            pkgBuilder.packageLayout(TEST_LAYOUT_2.resolveAt(pkgBuilder.create().relativeInstallDir()).emptyRootDirectory());
        }

        createAndVerifyPackage(buildPipeline(), pkgBuilder.create(), env, workDir.resolve("bundles"),
                String.format("testCreatePackageFromExternalAppImage(%s)", mode),
                CopyAppImageTaskID.COPY, PackageTaskID.RUN_POST_IMAGE_USER_SCRIPT, PrimaryTaskID.PACKAGE);
    }

    @Test
    void testCreatePackageFromExternalAppImageWithoutExternalAppImage(@TempDir Path workDir) throws ConfigException, PackagerException, IOException {

        final var env = setupBuildEnvForExternalAppImage(workDir);
        final var pkg = buildPackage(createApp(TEST_LAYOUT_1)).create();
        final var pipeline = buildPipeline().create();

        assertThrowsExactly(UnsupportedOperationException.class, () -> pipeline.execute(env, pkg, workDir));
    }

    private static BuildEnv setupBuildEnvForExternalAppImage(Path workDir) throws ConfigException {
        // Create an app image in `env.appImageDir()` directory.
        final var env = buildEnv(workDir.resolve("build"))
                .appImageLayout(TEST_LAYOUT_1.resolveAt(Path.of("a/b/c")).emptyRootDirectory())
                .appImageDir(workDir.resolve("app-image"))
                .create();
        TestRuntimeBuilder.INSTANCE.create(env.appImageDirLayout());
        TestLauncher.INSTANCE.create((ApplicationLayout)env.appImageDirLayout());

        return env;
    }

    private static void createAndVerifyPackage(PackagingPipeline.Builder builder, Package pkg,
            BuildEnv env, Path outputDir, String logMsgHeader, TaskID... expectedExecutedTaskActions) throws PackagerException, IOException {
        Objects.requireNonNull(logMsgHeader);

        final var startupParameters = builder.createStartupParameters(env, pkg, outputDir);

        assertNotSameAppImageDirs(env, startupParameters.packagingEnv());

        // Will create an app image in `startupParameters.packagingEnv().appImageDir()` directory
        // with `pkg.packageLayout()` layout using an app image (runtime image) from `env.appImageDir()` as input.
        // Will convert the created app image into a package.
        // Will not overwrite the contents of `env.appImageDir()` directory.
        builder.create().execute(startupParameters);

        final var expected = createTestPackageFileContents(
                pkg.packageLayout().resolveAt(startupParameters.packagingEnv().appImageDir()));

        final var actual = Files.readString(outputDir.resolve(pkg.packageFileNameWithSuffix()));

        assertEquals(expected, actual);
        System.out.println(String.format("%s:\n---\n%s\n---", logMsgHeader, actual));

        var actualExecutedTaskActions = dryRun(builder, toConsumer(_ -> {
            builder.create().execute(startupParameters);
        }));

        assertEquals(List.of(expectedExecutedTaskActions), actualExecutedTaskActions);
    }


    public static List<PackagingPipeline.TaskID> dryRun(PackagingPipeline.Builder builder,
            Consumer<PackagingPipeline.Builder> callback) {

        List<PackagingPipeline.TaskID> executedTaskActions = new ArrayList<>();
        builder.configuredTasks().filter(PackagingPipeline.Builder.TaskBuilder::hasAction).forEach(taskBuilder -> {
            var taskId = taskBuilder.task();
            taskBuilder.action(() -> {
                executedTaskActions.add(taskId);
            }).add();
        });

        callback.accept(builder);

        return executedTaskActions;
    }

    private static Application createApp(AppImageLayout appImageLayout) {
        return createApp(appImageLayout, Optional.empty());
    }

    private static Application createApp(AppImageLayout appImageLayout, RuntimeBuilder runtimeBuilder) {
        return createApp(appImageLayout, Optional.of(runtimeBuilder));
    }

    private static Application createApp(AppImageLayout appImageLayout, Optional<RuntimeBuilder> runtimeBuilder) {
        Objects.requireNonNull(appImageLayout);
        Objects.requireNonNull(runtimeBuilder);
        if (appImageLayout.isResolved()) {
            throw new IllegalArgumentException();
        }

        return new Application.Stub("foo", "My app", "1.0", "Acme", "copyright",
                Optional.empty(), List.of(), appImageLayout, runtimeBuilder, List.of(), Map.of());
    }


    private static final class PackageBuilder {
        PackageBuilder(Application app) {
            this.app = Objects.requireNonNull(app);
        }

        Package create() {
            var pkg = new Package.Stub(app, new PackageType() {}, "the-package",
                    "My package", "1.0", Optional.empty(), Optional.empty(),
                    Optional.ofNullable(predefinedAppImage), TEST_INSTALL_DIR);

            if (pkgLayout != null) {
                return CompositeProxy.build()
                        // Need this because PackageWithCustomPackageLayout and
                        // PackageWithCustomPackageLayoutMixin interfaces are package-private.
                        .invokeTunnel(CompositeProxyTunnel.INSTANCE)
                        .create(PackageWithCustomPackageLayout.class, pkg,
                                new PackageWithCustomPackageLayoutMixin.Stub(pkgLayout));
            } else {
                return pkg;
            }
        }

        PackageBuilder packageLayout(AppImageLayout v) {
            pkgLayout = v;
            return this;
        }

        PackageBuilder predefinedAppImage(Path v) {
            predefinedAppImage = v;
            return this;
        }


        private AppImageLayout pkgLayout;
        private Path predefinedAppImage;
        private final Application app;
    }


    private static PackageBuilder buildPackage(Application app) {
        return new PackageBuilder(app);
    }

    private static BuildEnvBuilder buildEnv(Path rootDir) {
        return new BuildEnvBuilder(rootDir);
    }

    private static PackagingPipeline.Builder buildPipeline() {
        return PackagingPipeline.buildStandard()
                // Disable building the app image file (.jpackage.xml) as we don't have launchers in the test app.
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
            return walk.sorted().map(path -> {
                var relativePath = root.relativize(path);
                if (Files.isRegularFile(path)) {
                    return String.format("%s[%s]", relativePath, toSupplier(() -> Files.readString(path)).get());
                } else {
                    return relativePath.toString();
                }
            }).collect(Collectors.joining("\n"));
        }
    }

    private static void assertNotSameAppImageDirs(BuildEnv a, BuildEnv b) {
        assertNotEquals(a.appImageDir(), b.appImageDir());
        assertEquals(a.buildRoot(), b.buildRoot());
        assertEquals(a.configDir(), b.configDir());
        assertEquals(a.resourceDir(), b.resourceDir());
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


    interface PackageWithCustomPackageLayout extends Package, PackageWithCustomPackageLayoutMixin {
        @Override
        AppImageLayout packageLayout();
    }

    interface PackageWithCustomPackageLayoutMixin {
        AppImageLayout packageLayout();

        record Stub(AppImageLayout packageLayout) implements PackageWithCustomPackageLayoutMixin {
        }
    }


    private final static ApplicationLayout TEST_LAYOUT_1 = ApplicationLayout.build()
            .launchersDirectory("launchers")
            .appDirectory("")
            .runtimeDirectory("runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private final static ApplicationLayout TEST_LAYOUT_2 = ApplicationLayout.build()
            .launchersDirectory("q/launchers")
            .appDirectory("")
            .runtimeDirectory("qqq/runtime")
            .appModsDirectory("")
            .contentDirectory("")
            .desktopIntegrationDirectory("")
            .create();

    private final static Path TEST_INSTALL_DIR = Path.of("Acme/My app");
}
