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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.OptionsBuilder;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.ConvertedOptionsBuilder;

/**
 * Processes jpackage command line.
 */
final class OptionsProcessor {

    OptionsProcessor(OptionsBuilder optionsBuilder, BundlingEnvironment bundlingEnv) {
        this.optionsBuilder = Objects.requireNonNull(optionsBuilder);
        this.bundlingEnv = Objects.requireNonNull(bundlingEnv);
    }

    Result<Options> validate() {
        // Parse the command line. The result is Options container of strings.
        final var untypedCmdline = optionsBuilder.create();

        // Create command line structure analyzer.
        final var analyzer = Result.create(() -> new OptionsAnalyzer(untypedCmdline, bundlingEnv));
        if (analyzer.hasErrors()) {
            return analyzer.mapErrors();
        }

        // Validate command line structure.
        final var structureErrors = analyzer.orElseThrow().findErrors();
        if (!structureErrors.isEmpty()) {
            return Result.ofErrors(structureErrors);
        }

        return optionsBuilder
                // Command line structure is valid.
                // Run value converters that will convert strings into objects (e.g.: String -> Path)
                .convertedOptions().map(ConvertedOptionsBuilder::create);
    }

    static Result<Options> readAdditionalLauncherProperties(Path file, Collection<Option> knownOptions) {
        final var props = new Properties();
        try (var in = Files.newBufferedReader(file)) {
            props.load(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        // Convert the property file into command line arguments.
        // Silently ignore unknown properties.
        final var args = knownOptions.stream().map(knownOption -> {
            return knownOption.getSpec().names().stream().map(optionName -> {
                return Optional.ofNullable(props.getProperty(optionName.name())).map(stringOptionValue -> {
                    return Stream.of(optionName.formatForCommandLine(), stringOptionValue);
                }).orElse(null);
            }).flatMap(x -> x);
        }).flatMap(x -> x).filter(Objects::nonNull).toArray(String[]::new);

        // Feed the contents of the property file as a command line arguments to the command line parser.
        return new JOptSimpleOptionsBuilder().options(knownOptions).create()
                .apply(args)
                .flatMap(OptionsBuilder::convertedOptions)
                .map(ConvertedOptionsBuilder::create);
    }

    private final JOptSimpleOptionsBuilder.OptionsBuilder optionsBuilder;
    private final BundlingEnvironment bundlingEnv;
}
