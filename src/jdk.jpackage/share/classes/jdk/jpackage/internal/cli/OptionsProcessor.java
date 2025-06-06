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

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static jdk.jpackage.internal.cli.Option.fromOptionSpecPredicate;
import static jdk.jpackage.internal.cli.StandardOption.currentPlatformOption;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.ConvertedOptionsBuilder;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.OptionsBuilder;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.util.Result;

/**
 * Processes jpackage command line.
 */
final class OptionsProcessor {

    OptionsProcessor(OptionsBuilder optionsBuilder, CliBundlingEnvironment bundlingEnv) {
        this.optionsBuilder = Objects.requireNonNull(optionsBuilder);
        this.bundlingEnv = Objects.requireNonNull(bundlingEnv);
    }

    record ValidatedOptions(Options options, BundlingOperationDescriptor bundlingOperation) {
        ValidatedOptions {
            Objects.requireNonNull(options);
            Objects.requireNonNull(bundlingOperation);
        }

        String bundleTypeName() {
            return bundlingOperation.bundleType();
        }
    }

    Result<ValidatedOptions> validate() {
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
                .convertedOptions().map(ConvertedOptionsBuilder::create).map(convertedOptions -> {
                    return new ValidatedOptions(convertedOptions, analyzer.orElseThrow().bundlingOperation());
                });
    }

    Collection<? extends Exception> runBundling(ValidatedOptions validatedOptions) {
        final List<Exception> errors = new ArrayList<>();

        try {
            errors.addAll(bundlingEnv.configurationErrors(validatedOptions.bundlingOperation()));
        } catch (NoSuchElementException ex) {
            // Unknown bundling operation.
            errors.add(new JPackageException(I18N.format("ERR_InvalidInstallerType", validatedOptions.bundleTypeName())));
        }

        final var validatedAddLaunchersResult = validateAdditionalLaunchers(validatedOptions.options());
        errors.addAll(validatedAddLaunchersResult.errors());

        final var validatedFaResult = validateFileAssociations(validatedOptions.options());
        errors.addAll(validatedFaResult.errors());

        if (Result.allHaveValues(validatedAddLaunchersResult, validatedFaResult)) {
            final Map<OptionIdentifier, Object> extra = new HashMap<>();
            extra.put(StandardOption.ADDITIONAL_LAUNCHERS.id(),
                    validatedAddLaunchersResult.value().orElseGet(List::of));
            extra.put(StandardOption.FA.id(),
                    validatedFaResult.value().orElseGet(List::of));
            extra.put(StandardOption.BUNDLING_OPERATION_DESCRIPTOR.id(),
                    validatedOptions.bundlingOperation());

            var cmdline = Options.concat(Options.of(extra), validatedOptions.options());

            bundlingEnv.createBundle(validatedOptions.bundlingOperation(), cmdline);
        }

        return errors;
    }

    /**
     * Loads property file and processes properties as a command line.
     * <p>
     * Unrecognized options will be silently ignored.
     *
     * @param file             the source property file
     * @param options          the recognized options
     * @param optionSpecMapper optional option spec mapper
     * @return {@link Options} instance containing validated property values or the list
     *         of errors occured during option values processing
     * @throws UncheckedIOException if an I/O error occurs
     */
    static Result<Options> processPropertyFile(Path file, Collection<Option> options,
            Optional<UnaryOperator<OptionSpec<?>>> optionSpecMapper) {
        final var props = new Properties();
        try (var in = Files.newBufferedReader(file)) {
            props.load(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        // Convert the property file into command line arguments.
        // Silently ignore unknown properties.
        final var args = options.stream().map(option -> {
            return option.getSpec().names().stream().map(optionName -> {
                return Optional.ofNullable(props.getProperty(optionName.name())).map(stringOptionValue -> {
                    return Stream.of(optionName.formatForCommandLine(), stringOptionValue);
                }).orElse(null);
            }).flatMap(x -> x);
        }).flatMap(x -> x).filter(Objects::nonNull).toArray(String[]::new);

        // Feed the contents of the property file as a command line arguments to the command line parser.
        final var builder = new JOptSimpleOptionsBuilder().options(options);

        optionSpecMapper.ifPresent(builder::optionSpecMapper);

        final var result = builder.optionSpecMapper(StandardOption::mapLauncherPropertyOptionSpec).create().apply(args)
                .flatMap(OptionsBuilder::convertedOptions)
                .map(ConvertedOptionsBuilder::create);

        return result.map(cmdline -> {
            return cmdline.copyWithDefaultValue(StandardOption.SOURCE_PROPERY_FILE, file);
        });
    }

    private Result<List<Options>> validateAdditionalLaunchers(Options cmdline) {
        final var currentPlatformOptions = filterForCurrentPlatform(StandardOption.launcherOptions());

        final var addLaunchers = StandardOption.ADD_LAUNCHER.findIn(cmdline).orElseGet(List::of).stream().map(addLauncher -> {
            final var result = processPropertyFile(addLauncher.propertyFile(), currentPlatformOptions,
                    Optional.of(StandardOption::mapLauncherPropertyOptionSpec));
            return Map.entry(addLauncher.name(), result);
        }).toList();

        final List<Exception> errors = new ArrayList<>();

        // Accumulate errors occurred during processing the property files.
        addLaunchers.stream().map(Map.Entry::getValue).map(Result::errors).forEach(errors::addAll);

        // Count additional launcher names.
        final var names = addLaunchers.stream().collect(groupingBy(Map.Entry::getKey, counting()));

        names.entrySet().stream().filter(e -> {
            // Filter duplicated names.
            return e.getValue() > 1;
        }).map(e -> {
            return new JPackageException(I18N.format("error.add-launcher-duplicate-name", e.getKey()));
        }).forEach(errors::add);

        if (errors.isEmpty()) {
            return Result.ofValue(addLaunchers.stream().map(e -> {
                var addLauncherOptionValues = e.getValue().orElseThrow();

                //
                // For additional launcher:
                //  - Override name.
                //  - Ignore icon configured for the app/main launcher.
                //  - If the additional launcher is modular, delete non-modular options of the main launcher.
                //  - If the additional launcher is non-modular, delete modular options of the main launcher.
                //  - Combine other option values with the main option values.
                //

                List<OptionValue<?>> excludes = new ArrayList<>();
                excludes.add(StandardOption.ICON);
                if (StandardOption.MODULE.containsIn(addLauncherOptionValues)) {
                    excludes.add(StandardOption.MAIN_JAR);
                }
                if (StandardOption.MAIN_JAR.containsIn(addLauncherOptionValues)) {
                    excludes.add(StandardOption.MODULE);
                }

                return Options.concat(
                        Options.of(Map.of(StandardOption.NAME.id(), e.getKey())),
                        addLauncherOptionValues,
                        cmdline.copyWithoutValues(excludes));
            }).toList());
        } else {
            return Result.ofErrors(errors);
        }
    }

    private Result<List<Options>> validateFileAssociations(Options cmdline) {
        final var currentPlatformOptions = filterForCurrentPlatform(StandardFaOption.options());

        final var fas = StandardOption.FILE_ASSOCIATIONS.findIn(cmdline).orElseGet(List::of).stream().map(fa -> {
            return processPropertyFile(fa, currentPlatformOptions, Optional.empty());
        }).toList();

        // Accumulate errors occurred during processing the property files.
        final var errors = fas.stream().map(Result::errors).flatMap(Collection::stream).toList();

        if (errors.isEmpty()) {
            return Result.ofValue(fas.stream().map(Result::orElseThrow).toList());
        } else {
            return Result.ofErrors(errors);
        }
    }

    private static Collection<Option> filterForCurrentPlatform(Collection<Option> options) {
        return options.stream().filter(fromOptionSpecPredicate(currentPlatformOption())).toList();
    }

    private final JOptSimpleOptionsBuilder.OptionsBuilder optionsBuilder;
    private final CliBundlingEnvironment bundlingEnv;
}
