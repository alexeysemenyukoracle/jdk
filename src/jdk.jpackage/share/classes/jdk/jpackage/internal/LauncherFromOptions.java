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

import static jdk.jpackage.internal.cli.StandardOptionValue.APPCLASS;
import static jdk.jpackage.internal.cli.StandardOptionValue.ARGUMENTS;
import static jdk.jpackage.internal.cli.StandardOptionValue.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardOptionValue.FA;
import static jdk.jpackage.internal.cli.StandardOptionValue.ICON;
import static jdk.jpackage.internal.cli.StandardOptionValue.INPUT;
import static jdk.jpackage.internal.cli.StandardOptionValue.JAVA_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOptionValue.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardOptionValue.MAIN_JAR;
import static jdk.jpackage.internal.cli.StandardOptionValue.MODULE;
import static jdk.jpackage.internal.cli.StandardOptionValue.MODULE_PATH;
import static jdk.jpackage.internal.cli.StandardOptionValue.NAME;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_RUNTIME_IMAGE;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.FileAssociationGroup.FileAssociationException;
import jdk.jpackage.internal.FileAssociationGroup.FileAssociationNoExtensionsException;
import jdk.jpackage.internal.FileAssociationGroup.FileAssociationNoMimesException;
import jdk.jpackage.internal.cli.FileAssociationOptionValue;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.CustomLauncherIcon;
import jdk.jpackage.internal.model.DefaultLauncherIcon;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherIcon;

record LauncherFromOptions(Optional<BiFunction<FileAssociation, Options, FileAssociation>> faExtension) {

    LauncherFromOptions {
        Objects.requireNonNull(faExtension);
    }

    LauncherFromOptions() {
        this(Optional.empty());
    }

    Launcher create(Options optionValues) {
        final var builder = new LauncherBuilder().defaultIconResourceName(defaultIconResourceName());

        DESCRIPTION.copyInto(optionValues, builder::description);
        builder.icon(toLauncherIcon(ICON.findIn(optionValues).orElse(null)));
        LAUNCHER_AS_SERVICE.copyInto(optionValues, builder::isService);
        NAME.copyInto(optionValues, builder::name);

        if (PREDEFINED_APP_IMAGE.findIn(optionValues).isEmpty()) {
            final var startupInfoBuilder = new LauncherStartupInfoBuilder2();
            
            INPUT.copyInto(optionValues, startupInfoBuilder::inputDir);
            ARGUMENTS.copyInto(optionValues, startupInfoBuilder::defaultParameters);
            JAVA_OPTIONS.copyInto(optionValues, startupInfoBuilder::javaOptions);
            MAIN_JAR.copyInto(optionValues, startupInfoBuilder::mainJar);
            APPCLASS.copyInto(optionValues, startupInfoBuilder::mainClassName);
            MODULE.copyInto(optionValues, startupInfoBuilder::moduleName);
            MODULE_PATH.copyInto(optionValues, startupInfoBuilder::modulePath);
            PREDEFINED_RUNTIME_IMAGE.copyInto(optionValues, startupInfoBuilder::predefinedRuntimeImage);
            
            builder.startupInfo(startupInfoBuilder.create());
        }

        final var faOptionValuesList = FA.findIn(optionValues).orElseGet(List::of);

        final var faGroups = IntStream.range(0, faOptionValuesList.size()).mapToObj(idx -> {
            final var faOptionValues = faOptionValuesList.get(idx);

            final var faGroupBuilder = FileAssociationGroup.build();

            if (OperatingSystem.current() == OperatingSystem.MACOS) {
                FileAssociationOptionValue.DESCRIPTION.copyInto(faOptionValues, faGroupBuilder::description);
            } else {
                faGroupBuilder.description(FileAssociationOptionValue.DESCRIPTION.findIn(faOptionValues).orElseGet(() -> {
                    return String.format("%s association", builder.create().name());
                }));
            }

            FileAssociationOptionValue.ICON.copyInto(faOptionValues, faGroupBuilder::icon);
            FileAssociationOptionValue.EXTENSIONS.copyInto(faOptionValues, faGroupBuilder::extensions);
            FileAssociationOptionValue.CONTENT_TYPE.copyInto(faOptionValues, faGroupBuilder::mimeTypes);

            final var faID = idx + 1;

            final FileAssociationGroup faGroup;
            try {
                faGroup = faGroupBuilder.create();
            } catch (FileAssociationNoMimesException ex) {
                throw I18N.buildConfigException()
                        .message("error.no-content-types-for-file-association", faID)
                        .advice("error.no-content-types-for-file-association.advice", faID)
                        .create();
            } catch (FileAssociationNoExtensionsException ex) {
                // TODO: Must do something about this condition!
                throw new AssertionError();
            } catch (FileAssociationException ex) {
                // Should never happen
                throw new UnsupportedOperationException(ex);
            }

            if (faExtension.isPresent()) {
                return new FileAssociationGroup(faGroup.items().stream().map(fa -> {
                    return faExtension.get().apply(fa, faOptionValues);
                }).toList());
            } else {
                return faGroup;
            }
        }).toList();

        return builder.faGroups(faGroups).create();
    }

    private static LauncherIcon toLauncherIcon(Path launcherIconPath) {
        if (launcherIconPath == null) {
            return DefaultLauncherIcon.INSTANCE;
        } else if (launcherIconPath.toString().isEmpty()) {
            return null;
        } else {
            return CustomLauncherIcon.create(launcherIconPath);
        }
    }

    private static String defaultIconResourceName() {
        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                return "JavaApp.ico";
            }
            case LINUX -> {
                return "JavaApp.png";
            }
            case MACOS -> {
                return "JavaApp.icns";
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }
}
