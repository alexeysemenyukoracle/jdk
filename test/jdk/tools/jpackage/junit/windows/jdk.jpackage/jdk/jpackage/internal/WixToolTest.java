/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixTool.ToolInfo;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMock;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class WixToolTest {

    @ParameterizedTest
    @MethodSource
    void test(TestSpec spec, @TempDir Path workDir) throws IOException {
        spec.run(workDir);
    }

    public static Collection<Object[]> test() {
        List<TestSpec.Builder> builders = new ArrayList<>();

        Stream.of(
                // Simple WiX3
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722")),
                // Simple WiX3 with FIPS
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo").fips())
                        .tool(tool("foo").candle("3.14.1.8722").fips())
                        .tool(tool("foo").light("3.14.1.8722")),
                // Simple WiX4+
                TestSpec.build()
                        .expect(toolset().version("5.0.2+aa65968c").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").wix("5.0.2+aa65968c")),
                // WiX3 with light and candle from different directories and non-existent directory
                TestSpec.build()
                        .expect(toolset().version("3.11.2").put(WixTool.Candle3, "foo").put(WixTool.Light3, "bar"))
                        .lookupDir("buz")
                        .tool(tool("foo").candle("3.11.2"))
                        .tool(tool("bar").light("3.11.2"))
                        .tool(tool("bar").candle("3.11.1"))
                        .tool(tool("foo").light("3.11.1")),
                // WiX3, WiX4+ same directory
                TestSpec.build()
                        .expect(toolset().version("5.0.2+aa65968c").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722"))
                        .tool(tool("foo").wix("5.0.2+aa65968c")),
                // WiX3 (good), WiX4+ (bad version)
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722"))
                        .tool(tool("foo").wix("Blah-blah-blah"))
        ).forEach(builders::add);

        for (var oldLightStatus : ToolStatus.values()) {
            for (var oldCandleStatus : ToolStatus.values()) {
                for (var newLightStatus : ToolStatus.values()) {
                    for (var newCandleStatus : ToolStatus.values()) {
                        boolean newGood = ToolStatus.isAllGood(newLightStatus, newCandleStatus);
                        if (!ToolStatus.isAllGood(oldLightStatus, oldCandleStatus) && !newGood) {
                            continue;
                        }

                        var builder = TestSpec.build();
                        if (newGood) {
                            builder.expect(toolset().version("3.14").put(WixToolsetType.Wix3, "new"));
                        } else {
                            builder.expect(toolset().version("3.11").put(WixToolsetType.Wix3, "old"));
                        }

                        oldCandleStatus.map(tool("old").candle("3.11")).ifPresent(builder::tool);
                        oldLightStatus.map(tool("old").light("3.11")).ifPresent(builder::tool);

                        newCandleStatus.map(tool("new").candle("3.14")).ifPresent(builder::tool);
                        newLightStatus.map(tool("new").light("3.14")).ifPresent(builder::tool);

                        builders.add(builder);
                    }
                }
            }
        }

        return builders.stream().map(TestSpec.Builder::create).map(spec -> {
            return new Object[] {spec};
        }).toList();
    }

    private enum ToolStatus {
        GOOD,
        MISSING,
        UNEXPECTED_STDOUT,
        ;

        static boolean isAllGood(ToolStatus... status) {
            return Stream.of(status).allMatch(Predicate.isEqual(GOOD));
        }

        Optional<CommandMockSpec> map(WixToolMock builder) {
            switch (this) {
                case MISSING -> {
                    return Optional.empty();
                }
                case UNEXPECTED_STDOUT -> {
                    var mock = builder.create();
                    return Optional.of(new CommandMockSpec(
                            mock.name(),
                            mock.mockName(),
                            CommandActionSpecs.build().stdout("Blah-Blah-Blah").exit().create()));
                }
                case GOOD -> {
                }
            }

            return Optional.of(builder.create());
        }
    }

    record TestSpec(WixToolset expected, List<Path> lookupDirs, Collection<CommandMockSpec> mocks) {
        TestSpec {
            Objects.requireNonNull(expected);

            if (lookupDirs.isEmpty() || mocks.isEmpty()) {
                throw new IllegalArgumentException();
            }

            lookupDirs.forEach(WixToolTest::assertIsRelative);

            // Ensure tool paths are unique.
            mocks.stream().map(CommandMockSpec::name).collect(toMap(x -> x, x -> x));
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            tokens.add(expected.toString());
            tokens.add(String.format("lookupDirs=%s", lookupDirs));
            tokens.add(mocks.toString());
            return String.join(", ", tokens);
        }

        void run(Path workDir) {
            var scriptBuilder = Script.build().commandMockBuilderMutator(CommandMock.Builder::repeatInfinitely);
            mocks.stream().map(mockSpec -> {
                return new CommandMockSpec(workDir.resolve(mockSpec.name()), mockSpec.mockName(), mockSpec.actions());
            }).forEach(scriptBuilder::map);

            scriptBuilder.map(_ -> true, CommandMock.ioerror("non-existent"));

            var script = scriptBuilder.createLoop();

            Consumer<Globals> globalsMutator = MockUtils.buildJPackage()
                    .script(script)
                    .listener(System.out::println)
                    .createGlobalsMutator();

            Globals.main(() -> {
                globalsMutator.accept(Globals.instance());

                var toolset = WixTool.createToolset(() -> {
                    return lookupDirs.stream().map(workDir::resolve).toList();
                }, false);

                assertEquals(resolveAt(expected, workDir), toolset);
                return 0;
            });
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            TestSpec create() {
                return new TestSpec(
                        expected,
                        Stream.concat(
                                lookupDirs.stream(),
                                tools.stream().map(CommandMockSpec::name).map(Path::getParent)
                        ).distinct().toList(),
                        tools);
            }

            Builder expect(WixToolset v) {
                expected = v;
                return this;
            }

            Builder expect(WixToolsetBuilder builder) {
                return expect(builder.create());
            }

            Builder lookupDir(String v) {
                return lookupDir(Path.of(v));
            }

            Builder lookupDir(Path v) {
                lookupDirs.add(Objects.requireNonNull(v));
                return this;
            }

            Builder tool(CommandMockSpec v) {
                tools.add(Objects.requireNonNull(v));
                return this;
            }

            Builder tool(WixToolMock v) {
                tools.add(v.create());
                return this;
            }

            private WixToolset expected;
            private List<Path> lookupDirs = new ArrayList<>();
            private List<CommandMockSpec> tools = new ArrayList<>();
        }
    }

    private final static class WixToolMock {

        CommandMockSpec create() {
            Objects.requireNonNull(type);
            Objects.requireNonNull(dir);
            Objects.requireNonNull(version);

            CommandActionSpec action;
            switch (type) {
                case Candle3 -> {
                    action = candleAction(fips, version);
                }
                case Light3 -> {
                    action = lightAction(version);
                }
                case Wix4 -> {
                    action = wixAction(version);
                }
                default -> {
                    throw ExceptionBox.reachedUnreachable();
                }
            }

            var toolPath = dir.resolve(type.fileName());
            var mockName = PathUtils.replaceSuffix(toolPath, "");

            return new CommandMockSpec(toolPath, mockName, CommandActionSpecs.build().action(action).create());
        }

        WixToolMock fips(Boolean v) {
            fips = v;
            return this;
        }

        WixToolMock fips() {
            return fips(true);
        }

        WixToolMock dir(Path v) {
            dir = v;
            return this;
        }

        WixToolMock type(WixTool v) {
            type = v;
            return this;
        }

        WixToolMock version(String v) {
            version = v;
            return this;
        }

        WixToolMock candle(String version) {
            return type(WixTool.Candle3).version(version);
        }

        WixToolMock light(String version) {
            return type(WixTool.Light3).version(version);
        }

        WixToolMock wix(String version) {
            return type(WixTool.Wix4).version(version);
        }

        private static CommandActionSpec candleAction(boolean fips, String version) {
            Objects.requireNonNull(version);
            var sb = new StringBuilder();
            sb.append(version);
            if (fips) {
                sb.append("; fips");
            }
            return CommandActionSpec.create(sb.toString(), context -> {
                if (List.of("-?").equals(context.args())) {
                    if (fips) {
                        context.err().println("error CNDL0308 : The Federal Information Processing Standard (FIPS) appears to be enabled on the machine");
                        return Optional.of(308);
                    }
                } else if (!List.of("-fips").equals(context.args())) {
                    throw context.unexpectedArguments();
                }

                var out = context.out();
                List.of(
                        "Windows Installer XML Toolset Compiler version " + version,
                        "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                        "",
                        " usage:  candle.exe [-?] [-nologo] [-out outputFile] sourceFile [sourceFile ...] [@responseFile]"
                ).forEach(out::println);

                return Optional.of(0);
            });
        }

        private static CommandActionSpec lightAction(String version) {
            Objects.requireNonNull(version);
            return CommandActionSpec.create(version, context -> {
                if (List.of("-?").equals(context.args())) {
                    var out = context.out();
                    List.of(
                            "Windows Installer XML Toolset Linker version " + version,
                            "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                            "",
                            " usage:  light.exe [-?] [-b bindPath] [-nologo] [-out outputFile] objectFile [objectFile ...] [@responseFile]"
                    ).forEach(out::println);
                    return Optional.of(0);
                } else {
                    throw context.unexpectedArguments();
                }
            });
        }

        private static CommandActionSpec wixAction(String version) {
            Objects.requireNonNull(version);
            return CommandActionSpec.create(version, context -> {
                if (List.of("--version").equals(context.args())) {
                    context.out().println(version);
                    return Optional.of(0);
                } else {
                    throw context.unexpectedArguments();
                }
            });
        }

        private Path dir;
        private WixTool type;
        private String version;
        private boolean fips;
    }

    private final static class WixToolsetBuilder {

        WixToolset create() {
            return new WixToolset(tools.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
                ToolInfo toolInfo = new WixTool.DefaultToolInfo(e.getValue(), version);
                if (e.getKey() == WixTool.Candle3) {
                    toolInfo = new WixTool.DefaultCandleInfo(toolInfo, fips);
                }
                return toolInfo;
            })));
        }

        WixToolsetBuilder version(String v) {
            version = v;
            return this;
        }

        WixToolsetBuilder put(WixTool tool, String path) {
            return put(tool, Path.of(path));
        }

        WixToolsetBuilder put(WixTool tool, Path path) {
            tools.put(Objects.requireNonNull(tool), path.resolve(tool.fileName()));
            return this;
        }

        WixToolsetBuilder put(WixToolsetType type, Path path) {
            type.getTools().forEach(tool -> {
                put(tool, path);
            });
            return this;
        }

        WixToolsetBuilder put(WixToolsetType type, String path) {
            return put(type, Path.of(path));
        }

        WixToolsetBuilder fips(boolean v) {
            fips = true;
            return this;
        }

        WixToolsetBuilder fips() {
            return fips(true);
        }

        private Map<WixTool, Path> tools = new HashMap<>();
        private boolean fips;
        private String version;
    }

    private static WixToolsetBuilder toolset() {
        return new WixToolsetBuilder();
    }

    private static WixToolMock tool() {
        return new WixToolMock();
    }

    private static WixToolMock tool(Path dir) {
        return tool().dir(dir);
    }

    private static WixToolMock tool(String dir) {
        return tool(Path.of(dir));
    }

    private static WixToolset resolveAt(WixToolset toolset, Path root) {
        return new WixToolset(toolset.tools().entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            var toolInfo = e.getValue();

            assertIsRelative(toolInfo.path());

            ToolInfo newToolInfo = new WixTool.DefaultToolInfo(root.resolve(toolInfo.path()), toolInfo.version());
            if (toolInfo instanceof WixTool.CandleInfo candleInfo) {
                newToolInfo = new WixTool.DefaultCandleInfo(newToolInfo, candleInfo.fips());
            }
            return newToolInfo;
        })));
    }

    private static void assertIsRelative(Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException();
        }
    }
}

