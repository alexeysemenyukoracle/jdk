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

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.pathSeparator;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;
import static jdk.jpackage.internal.cli.OptionValueExceptionFactory.UNREACHABLE_EXCEPTION_FACTORY;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_BUNDLE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_PKG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_NATIVE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.fromOptionName;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.uuidConv;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.KnownExceptionType;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.util.SetBuilder;

/**
 * jpackage command line options
 */
public final class StandardOptionValue {

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITHOUT_CONTEXT =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.NONE).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE_AND_OPTION_NAME =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_OPTION_NAME_AND_VALUE =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.NAME_AND_VALUE).create();

    private final static Set<OperatingSystem> SUPPORTED_OS = Set.of(
            OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.MACOS);


    /**
     * Scope of options configuring a launcher.
     */
    interface LauncherProperty<T> extends OptionScope {
        OptionSpec<T> optionSpecForPropertyFile();
    }


    /**
     * Modes in which bundling operations don't involve building of an app image.
     */
    private static final Set<BundlingOperationModifier> NOT_BUILDING_APP_IMAGE = Set.of(
            // jpackage will not build an app image when bundling runtime native package
            BundlingOperationModifier.BUNDLE_RUNTIME,
            // jpackage will not build an app image if predefined app image is supplied
            BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE);

    private final static Set<OptionScope> MAC_SIGNING = new SetBuilder<OptionScope>()
            .add(StandardBundlingOperation.MAC_SIGNING)
            .add(NOT_BUILDING_APP_IMAGE)
            .create();


    public final static OptionValue<Boolean> HELP = auxilaryOption("help").shortName("h").create();

    public final static OptionValue<Boolean> VERSION = auxilaryOption("version").create();

    public final static OptionValue<Boolean> VERBOSE = auxilaryOption("verbose").create();

    public final static OptionValue<PackageType> TYPE = option("type", PackageType.class).shortName("t")
            .converterExceptionFactory(ERROR_WITH_VALUE).converterExceptionFormatString("ERR_InvalidInstallerType")
            .converter(str -> {
                Objects.requireNonNull(str);
                return Stream.of(StandardBundlingOperation.values()).filter(bundlingOperation -> {
                    return bundlingOperation.packageTypeValue().equals(str);
                }).map(StandardBundlingOperation::packageType).findFirst().orElseThrow(IllegalArgumentException::new);
            }).create();

    public final static OptionValue<Path> INPUT = directoryOption("input").shortName("i")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public final static OptionValue<Path> DEST = directoryOption("dest").shortName("d")
            .valuePattern("destination")
            .validator(StandardValidator.IS_DIRECTORY_OR_NON_EXISTENT)
            .create();

    public final static OptionValue<String> DESCRIPTION = stringOption("description")
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<String> VENDOR = stringOption("vendor").create();

    public final static OptionValue<String> APPCLASS = stringOption("main-class")
            .valuePattern("class-name")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<String> NAME = stringOption("name").shortName("n").create();

    public final static OptionValue<Path> RESOURCE_DIR = directoryOption("resource-dir")
            .inScope(CREATE_BUNDLE).inScope(MAC_SIGNING)
            .validatorExceptionFormatString("message.resource-dir-does-not-exist")
            .validatorExceptionFactory(ERROR_WITH_OPTION_NAME_AND_VALUE)
            .create();

    public final static OptionValue<List<String>> ARGUMENTS = stringOption("arguments").toArray(stringListTokenizer())
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherArrayProperty(String.class))
            .create(toList());

    public final static OptionValue<List<String>> JLINK_OPTIONS = stringOption("jlink-options")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .toArray(stringListTokenizer())
            .create(toList());

    public final static OptionValue<Path> ICON = fileOption("icon").mutate(launcherProperty()).create();

    public final static OptionValue<String> COPYRIGHT = stringOption("copyright").create();

    public final static OptionValue<Path> LICENSE_FILE = fileOption("license-file")
            .validator(StandardValidator.IS_EXISTENT_NOT_DIRECTORY)
            .validatorExceptionFormatString("ERR_LicenseFileNotExit")
            .validatorExceptionFactory(ERROR_WITHOUT_CONTEXT)
            .create();

    public final static OptionValue<String> APP_VERSION = stringOption("app-version").create();

    public final static OptionValue<String> ABOUT_URL = urlOption("about-url")
            .scope(CREATE_NATIVE).inScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public final static OptionValue<List<String>> JAVA_OPTIONS = stringOption("java-options")
            .toArray(stringListTokenizer())
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherArrayProperty(String.class))
            .create(toList());

    public final static OptionValue<List<Path>> APP_CONTENT = pathOption("app-content").toArray(pathSeparator())
            .valuePattern("additional-content")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .create(toList());

    final static OptionValue<List<Path>> FILE_ASSOCIATIONS = fileOption("file-associations")
            .toArray(pathSeparator())
            .outOfScope(BundlingOperationModifier.BUNDLE_RUNTIME)
            .create(toList());

    final static OptionValue<List<AdditionalLauncher>> ADD_LAUNCHER = createAddLauncherOption("add-launcher");

    public final static OptionValue<Path> TEMP_ROOT = directoryOption("temp")
            .validatorExceptionFactory(ERROR_WITH_VALUE)
            .validatorExceptionFormatString("ERR_BuildRootInvalid")
            .validator(StandardValidator.IS_DIRECTORY_EMPTY_OR_NON_EXISTENT)
            .create();

    public final static OptionValue<Path> INSTALL_DIR = pathOption("install-dir")
            .valuePattern("path")
            .create();

    public final static OptionValue<Path> PREDEFINED_APP_IMAGE = directoryOption("app-image")
            .scope(CREATE_NATIVE).inScope(SIGN_MAC_APP_IMAGE).inScope(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE)
            .validatorExceptionFactory(ERROR_WITH_VALUE)
            .validatorExceptionFormatString("ERR_AppImageNotExist")
            .create();

    public final static OptionValue<Path> PREDEFINED_RUNTIME_IMAGE = directoryOption("runtime-image")
            .outOfScope(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE)
            .validatorExceptionFactory(ERROR_WITH_OPTION_NAME_AND_VALUE)
            .validatorExceptionFormatString("message.runtime-image-dir-does-not-exist")
            .create();

    public final static OptionValue<Path> MAIN_JAR = pathOption("main-jar")
            .valuePattern("main-jar")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<String> MODULE = stringOption("module").shortName("m")
            .valuePattern("module-name[/main-class]")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<List<String>> ADD_MODULES = stringOption("add-modules").toArray(",")
            .valuePattern("module-name")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .create(toList());

    public final static OptionValue<List<Path>> MODULE_PATH = pathOption("module-path").toArray(pathSeparator())
            .valuePattern("module-name")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .create(toList());

    public final static OptionValue<Boolean> LAUNCHER_AS_SERVICE = booleanOption("launcher-as-service")
            .scope(nativeBundling())
            .mutate(launcherProperty())
            .create();

    //
    // Linux-specific
    //

    public final static OptionValue<String> LINUX_RELEASE = stringOption("linux-app-release").scope(nativeBundling()).create();

    public final static OptionValue<String> LINUX_BUNDLE_NAME = stringOption("linux-package-name").scope(nativeBundling()).create();

    public final static OptionValue<String> LINUX_DEB_MAINTAINER = stringOption("linux-deb-maintainer")
            .valuePattern("email-address")
            .create();

    public final static OptionValue<String> LINUX_CATEGORY = stringOption("linux-app-category").scope(nativeBundling()).create();

    public final static OptionValue<String> LINUX_RPM_LICENSE_TYPE = stringOption("linux-rpm-license-type").scope(nativeBundling()).create();

    public final static OptionValue<String> LINUX_PACKAGE_DEPENDENCIES = stringOption("linux-package-deps").scope(nativeBundling()).create();

    public final static OptionValue<Boolean> LINUX_SHORTCUT_HINT = booleanOption("linux-shortcut")
            .scope(nativeBundling())
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<String> LINUX_MENU_GROUP = stringOption("linux-menu-group")
            .valuePattern("menu-group-name")
            .scope(nativeBundling()).create();

    //
    // MacOS-specific
    //

    public final static OptionValue<List<Path>> MAC_DMG_CONTENT = pathOption("mac-dmg-content")
            .valuePattern("additional-content")
            .toArray(pathSeparator()).create(toList());

    public final static OptionValue<Boolean> MAC_SIGN = booleanOption("mac-sign").scope(MAC_SIGNING).shortName("s").create();

    public final static OptionValue<Boolean> MAC_APP_STORE = booleanOption("mac-app-store").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_CATEGORY = stringOption("mac-app-category").create();

    public final static OptionValue<String> MAC_BUNDLE_NAME = stringOption("mac-package-name").create();

    public final static OptionValue<String> MAC_BUNDLE_IDENTIFIER = stringOption("mac-package-identifier").create();

    public final static OptionValue<String> MAC_BUNDLE_SIGNING_PREFIX = stringOption("mac-package-signing-prefix").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_SIGNING_KEY_NAME = stringOption("mac-signing-key-user-name").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_APP_IMAGE_SIGN_IDENTITY = stringOption("mac-app-image-sign-identity").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_INSTALLER_SIGN_IDENTITY = stringOption("mac-installer-sign-identity")
            .scope(CREATE_MAC_PKG).inScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public final static OptionValue<Path> MAC_SIGNING_KEYCHAIN = pathOption("mac-signing-keychain")
            .valuePattern("keychain-name")
            .scope(MAC_SIGNING).create();

    public final static OptionValue<Path> MAC_ENTITLEMENTS = fileOption("mac-entitlements").scope(MAC_SIGNING).create();

    //
    // Windows-specific
    //

    public final static OptionValue<String> WIN_HELP_URL = urlOption("win-help-url").scope(nativeBundling()).create();

    public final static OptionValue<String> WIN_UPDATE_URL = urlOption("win-update-url").scope(nativeBundling()).create();

    public final static OptionValue<Boolean> WIN_MENU_HINT = booleanOption("win-menu")
            .scope(nativeBundling())
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<String> WIN_MENU_GROUP = stringOption("win-menu-group")
            .valuePattern("menu-group-name")
            .scope(nativeBundling()).create();

    public final static OptionValue<Boolean> WIN_SHORTCUT_HINT = booleanOption("win-shortcut")
            .scope(nativeBundling())
            .mutate(launcherProperty())
            .create();

    public final static OptionValue<Boolean> WIN_SHORTCUT_PROMPT = booleanOption("win-shortcut-prompt").scope(nativeBundling()).create();

    public final static OptionValue<Boolean> WIN_PER_USER_INSTALLATION = booleanOption("win-per-user-install").scope(nativeBundling()).create();

    public final static OptionValue<Boolean> WIN_INSTALLDIR_CHOOSER = booleanOption("win-dir-chooser").scope(nativeBundling()).create();

    public final static OptionValue<UUID> WIN_UPGRADE_UUID = uuidOption("win-upgrade-uuid").scope(nativeBundling()).create();

    public final static OptionValue<Boolean> WIN_CONSOLE_HINT = booleanOption("win-console")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(launcherProperty())
            .create();


    //
    // Synthetic options
    //

    /**
     * Processed additional launcher property files.
     * <p>
     * Items in the list are in the order "--add-launcher" options appeared on the
     * command line. Every item in the list has {@link #SOURCE_PROPERY_FILE} option
     * with the value set to the source property file and {@link #NAME} option with
     * the value set to the additional launcher name.
     */
    public final static OptionValue<List<Options>> ADDITIONAL_LAUNCHERS = OptionValue.create();

    /**
     * Processed file association property files.
     * <p>
     * Items in the list are in the order "--file-associations" options appeared on
     * the command line. Every item in the list has {@link #SOURCE_PROPERY_FILE}
     * option with the value set to the source property file.
     */
    public final static OptionValue<List<Options>> FA = OptionValue.create();

    public final static OptionValue<Path> SOURCE_PROPERY_FILE = OptionValue.create();

    /**
     * Returns options configuring a launcher.
     *
     * @return the options configuring a launcher
     */
    static Set<Option> launcherOptions() {
        return options().stream().filter(option -> {
            return option.getSpec().scope().stream().anyMatch(LauncherProperty.class::isInstance);
        }).collect(toSet());
    }

    static <T> OptionSpec<T> mapLauncherPropertyOptionSpec(OptionSpec<T> optionSpec) {
        return optionSpec.scope().stream()
                .filter(LauncherProperty.class::isInstance)
                .map(LauncherProperty.class::cast)
                .findFirst().map(LauncherProperty<T>::optionSpecForPropertyFile).orElse(optionSpec);
    }

    /**
     * Returns all options with option specs defined in {@link StandardOptionValue} class.
     *
     * @return all defined options
     */
    static Set<Option> options() {
        return Option.getPublicOptionsWithSpecs(StandardOptionValue.class);
    }

    /**
     * Returns a {@link Predicate} that returns {@code true} if the given option
     * spec denotes an option supported on all platforms.
     *
     * @return the predicate
     */
    static Predicate<OptionSpec<?>> sharedOption() {
        return optionSpec -> {
            final var optionSupportedOSs = StandardBundlingOperation.narrow(optionSpec.scope().stream())
                    .map(StandardBundlingOperation::os).collect(Collectors.toSet());
            return optionSupportedOSs.equals(SUPPORTED_OS);
        };
    }

    /**
     * Returns a {@link Predicate} that returns {@code true} if the given option
     * spec denotes an option supported on the current platform.
     *
     * @see {@link OperatingSystem#current()}
     *
     * @return the predicate
     */
    static Predicate<OptionSpec<?>> currentPlatformOption() {
        return optionSpec -> {
            return StandardBundlingOperation.narrow(optionSpec.scope().stream())
                    .filter(StandardBundlingOperation.currentPlatform()).findFirst().isPresent();
        };
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .description("help.option.description." + name)
                .scope(fromOptionName(name))
                .scope(scope -> {
                    if (Collections.disjoint(scope, CREATE_NATIVE)) {
                        // Not a native bundling scope.
                        return scope;
                    } else {
                        scope = new HashSet<>(scope);
                        scope.addAll(List.of(BundlingOperationModifier.values()));
                        return scope;
                    }
                })
                .exceptionFactory(UNREACHABLE_EXCEPTION_FACTORY)
                .exceptionFormatString("");
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).converter(identityConv());
    }

    private static OptionSpecBuilder<UUID> uuidOption(String name) {
        return option(name, UUID.class)
                .converter(uuidConv())
                .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .converterExceptionFormatString("error.paramater-not-uuid");
    }

    private static OptionSpecBuilder<Path> pathOption(String name) {
        return option(name, Path.class)
                .converter(pathConv())
                .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .converterExceptionFormatString("error.paramater-not-path");
    }

    private static OptionSpecBuilder<Path> fileOption(String name) {
        return pathOption(name)
                .valuePattern("file path")
                .validator(StandardValidator.IS_EXISTENT_NOT_DIRECTORY)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.paramater-not-file");
    }

    private static OptionSpecBuilder<Path> directoryOption(String name) {
        return pathOption(name)
                .valuePattern("directory path")
                .validator(StandardValidator.IS_DIRECTORY)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.paramater-not-directory");
    }

    private static OptionSpecBuilder<String> urlOption(String name) {
        return stringOption(name)
                .valuePattern("url")
                .validator(StandardValidator.IS_URL)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.paramater-not-url");
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).defaultValue(Boolean.FALSE);
    }

    private static OptionSpecBuilder<Boolean> auxilaryOption(String name) {
        return booleanOption(name).inScope(MAC_SIGNING).inScope(NOT_BUILDING_APP_IMAGE);
    }

    private static UnaryOperator<Set<OptionScope>> nativeBundling() {
        return scope -> {
            return new SetBuilder<OptionScope>()
                    .set(scope)
                    .remove(new SetBuilder<OptionScope>().set(StandardBundlingOperation.values()).remove(CREATE_NATIVE).create())
                    .create();
        };
    }

    private static <T> Consumer<OptionSpecBuilder<T>> launcherProperty() {
        return builder -> {
            builder.inScope(new LauncherScalarProperty<>(builder));
        };
    }

    private static <T> Consumer<OptionSpecBuilder<T>.ArrayOptionSpecBuilder> launcherArrayProperty(Class<? extends T> elementType) {
        return builder -> {
            builder.inScope(new LauncherArrayProperty<>(builder));
        };
    }

    static Function<String, String[]> stringListTokenizer() {
        return str -> {
            return Arguments.getArgumentList(str).toArray(String[]::new);
        };
    }

    private static OptionValue<List<AdditionalLauncher>> createAddLauncherOption(String name) {
        OptionValueConverter<Path> propertyFileConverter = fileOption(name)
                .create().getSpec().converter().orElseThrow();

        return option(name, AdditionalLauncher.class)
                .valuePattern("name=path")
                .outOfScope(NOT_BUILDING_APP_IMAGE)
                .converterExceptionFactory((optionName, optionValue, formatString, cause) -> {
                    final var theCause = cause.orElseThrow();
                    if (theCause instanceof AddLauncherSyntaxException) {
                        return ERROR_WITH_VALUE_AND_OPTION_NAME.create(optionName,
                                optionValue, "error.paramater-add-launcher-malformed", cause);
                    } else if (theCause instanceof AddLauncherInvalidPropertyFile invalidFile) {
                        return invalidFile;
                    } else if (theCause instanceof RuntimeException runtimeEx) {
                        return runtimeEx;
                    } else {
                        return new RuntimeException(theCause);
                    }
                }).converter(value -> {
                    var components = value.split("=", 2);
                    if (components.length != 2 || components[0].isEmpty() || components[1].isEmpty()) {
                        throw new AddLauncherSyntaxException();
                    }

                    final Path propertyFile;
                    try {
                        propertyFile = propertyFileConverter.convert(OptionName.of(name),
                                StringToken.of(value, components[1])).orElseThrow();
                    } catch (JPackageException ex) {
                        throw new AddLauncherInvalidPropertyFile(I18N.format(
                                "error.paramater-add-launcher-not-file", components[1], components[0]));
                    }

                    return new AdditionalLauncher(components[0], propertyFile);
                }).toArray().defaultValue(new AdditionalLauncher[0]).create(toList());
    }


    private final static class AddLauncherSyntaxException extends IllegalArgumentException implements KnownExceptionType {

        AddLauncherSyntaxException() {
        }

        private static final long serialVersionUID = 1L;
    }


    private final static class AddLauncherInvalidPropertyFile extends IllegalArgumentException implements KnownExceptionType {

        AddLauncherInvalidPropertyFile(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }


    private final class Arguments {

        //
        // This is a an extract from jdk.jpackage.internal.Arguments class copied as-is.
        //

        // regexp for parsing args (for example, for additional launchers)
        private static Pattern pattern = Pattern.compile(
              "(?:(?:([\"'])(?:\\\\\\1|.)*?(?:\\1|$))|(?:\\\\[\"'\\s]|[^\\s]))++");

        static List<String> getArgumentList(String inputString) {
            List<String> list = new ArrayList<>();
            if (inputString == null || inputString.isEmpty()) {
                 return list;
            }

            // The "pattern" regexp attempts to abide to the rule that
            // strings are delimited by whitespace unless surrounded by
            // quotes, then it is anything (including spaces) in the quotes.
            Matcher m = pattern.matcher(inputString);
            while (m.find()) {
                String s = inputString.substring(m.start(), m.end()).trim();
                // Ensure we do not have an empty string. trim() will take care of
                // whitespace only strings. The regex preserves quotes and escaped
                // chars so we need to clean them before adding to the List
                if (!s.isEmpty()) {
                    list.add(unquoteIfNeeded(s));
                }
            }
            return list;
        }

        private static String unquoteIfNeeded(String in) {
            if (in == null) {
                return null;
            }

            if (in.isEmpty()) {
                return "";
            }

            // Use code points to preserve non-ASCII chars
            StringBuilder sb = new StringBuilder();
            int codeLen = in.codePointCount(0, in.length());
            int quoteChar = -1;
            for (int i = 0; i < codeLen; i++) {
                int code = in.codePointAt(i);
                if (code == '"' || code == '\'') {
                    // If quote is escaped make sure to copy it
                    if (i > 0 && in.codePointAt(i - 1) == '\\') {
                        sb.deleteCharAt(sb.length() - 1);
                        sb.appendCodePoint(code);
                        continue;
                    }
                    if (quoteChar != -1) {
                        if (code == quoteChar) {
                            // close quote, skip char
                            quoteChar = -1;
                        } else {
                            sb.appendCodePoint(code);
                        }
                    } else {
                        // opening quote, skip char
                        quoteChar = code;
                    }
                } else {
                    sb.appendCodePoint(code);
                }
            }
            return sb.toString();
        }
    }


    private record LauncherScalarProperty<T>(OptionSpecBuilder<T> builder) implements LauncherProperty<T> {
        public LauncherScalarProperty {
            Objects.requireNonNull(builder);
        }

        @Override
        public OptionSpec<T> optionSpecForPropertyFile() {
            return builder.createOptionSpec();
        }
    }


    private record LauncherArrayProperty<T>(OptionSpecBuilder<T>.ArrayOptionSpecBuilder builder) implements LauncherProperty<T[]> {
        public LauncherArrayProperty {
            Objects.requireNonNull(builder);
        }

        @Override
        public OptionSpec<T[]> optionSpecForPropertyFile() {
            return builder.createOptionSpec();
        }
    }
}
