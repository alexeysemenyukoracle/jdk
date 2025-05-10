/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;
import static jdk.internal.util.OperatingSystem.WINDOWS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;


/**
 * Tests generation of packages with additional content in app image.
 */

/*
 * @test
 * @summary jpackage with --app-content option
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build AppContentTest
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppContentTest
 */
public class AppContentTest {

    private static final Content TEST_JAVA = new DefaultContent("apps/PrintEnv.java");
    private static final Content TEST_DUKE = new DefaultContent("apps/dukeplug.png");
    private static final Content TEST_DUKE_LINK = new SymlinkContent("dukeplugLink.txt");
    private static final Content TEST_DIR = new DefaultContent("apps");
    private static final Content TEST_BAD = new NonExistantPath();

    // On OSX `--app-content` paths will be copied into the "Contents" folder
    // of the output app image.
    // "codesign" imposes restrictions on the directory structure of "Contents" folder.
    // In particular, random files should be placed in "Contents/Resources" folder
    // otherwise "codesign" will fail to sign.
    // Need to prepare arguments for `--app-content` accordingly.
    private static final boolean copyInResources = TKit.isOSX();

    private static final String RESOURCES_DIR = "Resources";

    public static Collection<Object[]> test() {
        return Stream.of(
                build().add(TEST_JAVA).add(TEST_DUKE),
                build().add(TEST_JAVA).add(TEST_BAD),
                build().startGroup().add(TEST_JAVA).add(TEST_DUKE).endGroup().add(TEST_DIR)
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<Object[]> testSymlink() {
        return Stream.of(
                build().add(TEST_JAVA).add(TEST_DUKE_LINK)
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    @Test
    @ParameterSupplier
    @ParameterSupplier(value="testSymlink", ifNotOS = WINDOWS)
    public void test(TestSpec testSpec) throws Exception {
        testSpec.test();
    }

    public record TestSpec(List<List<Content>> content) {
        public TestSpec {
            content.stream().flatMap(List::stream).forEach(Objects::requireNonNull);
        }
        
        @Override
        public String toString() {
            return content.stream().map(group -> {
                return group.stream().map(Content::toString).collect(joining(","));
            }).collect(joining("; "));
        }

        void test() {
            final int expectedJPackageExitCode;
            if (content.stream().flatMap(List::stream).anyMatch(TEST_BAD::equals)) {
                expectedJPackageExitCode = 1;
            } else {
                expectedJPackageExitCode = 0;
            }

            new PackageTest().configureHelloApp()
                .addInitializer(cmd -> {
                    content.stream().map(group -> {
                        return Stream.of("--app-content", group.stream().map(Content::init).map(Path::toString).collect(joining(",")));
                    }).flatMap(x -> x).forEachOrdered(cmd::addArgument);
                })
                .addInstallVerifier(cmd -> {
                    final var appContentRoot = getAppContentRoot(cmd);
                    content.stream().flatMap(List::stream).forEach(content -> {
                        content.verify(appContentRoot);
                    });
                })
                .setExpectedExitCode(expectedJPackageExitCode)
                .run();
        }

        static final class Builder {
            TestSpec create() {
                return new TestSpec(groups);
            }

            final class GroupBuilder {
                GroupBuilder add(Content content) {
                    group.add(Objects.requireNonNull(content));
                    return this;
                }

                Builder endGroup() {
                    if (!group.isEmpty()) {
                        groups.add(group);
                    }
                    return Builder.this;
                }

                private final List<Content> group = new ArrayList<>();
            }

            Builder add(Content content) {
                return startGroup().add(content).endGroup();
            }

            GroupBuilder startGroup() {
                return new GroupBuilder();
            }

            private final List<List<Content>> groups = new ArrayList<>();
        }
    }
    
    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }

    private static Path getAppContentRoot(JPackageCommand cmd) {
        final Path contentDir = cmd.appLayout().contentDirectory();
        if (copyInResources) {
            return contentDir.resolve(RESOURCES_DIR);
        } else {
            return contentDir;
        }
    }

    public interface Content {
        Path init();
        default void verify(Path appContentRoot) {}
    }

    private static final class NonExistantPath implements Content {
        @Override
        public Path init() {
            return Path.of("non-existant-" + Integer.toHexString(new Random().ints(100, 200).findFirst().getAsInt()));
        }

        @Override
        public String toString() {
            return "*non-existant*";
        }
    }

    private record DefaultContent(Path path) implements Content {
        DefaultContent {
            if (path.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        DefaultContent(String path) {
            this(Path.of(path));
        }

        @Override
        public Path init() {
            final var srcPath = TKit.TEST_SRC_ROOT.resolve(path);
            if (!copyInResources) {
                return srcPath;
            } else {
                final var appContentArg = TKit.createTempDirectory("app-content").resolve(RESOURCES_DIR);
                final var dstPath = appContentArg.resolve(srcPath.getFileName());
                try {
                    Files.createDirectories(dstPath.getParent());
                    FileUtils.copyRecursive(srcPath, dstPath);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                return appContentArg;
            }
        }

        @Override
        public void verify(Path appContentRoot) {
            TKit.assertPathExists(appContentRoot.resolve(path.getFileName()), true);
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }

    private record SymlinkContent(Path path) implements Content {
        SymlinkContent {
            if (path.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        SymlinkContent(String path) {
            this(Path.of(path));
        }

        @Override
        public Path init() {
            final var basedir = TKit.createTempDirectory("app-content");
            final Path appContentArg;
            if (copyInResources) {
                appContentArg = basedir.resolve(RESOURCES_DIR);
            } else {
                appContentArg = basedir;
            }

            final var linkPath = appContentArg.resolve(linkPath());
            try {
                Files.createDirectories(linkPath.getParent());
                // Create the target file for the link.
                Files.writeString(appContentArg.resolve(linkTarget()), linkTarget().toString());
                // Create the link.
                Files.createSymbolicLink(linkPath, linkTarget().getFileName());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            return appContentArg;
        }

        @Override
        public void verify(Path appContentRoot) {
            TKit.assertSymbolicLinkExists(appContentRoot.resolve(linkPath()));
            TKit.assertFileExists(appContentRoot.resolve(linkTarget()));
        }

        @Override
        public String toString() {
            return String.format("symlink:[%s]->[%s]", path, linkTarget());
        }

        private Path linkPath() {
            return Path.of("Links").resolve(path);
        }
        
        private Path linkTarget() {
            return Path.of(linkPath().toString() + "-target");
        }
    }
}
