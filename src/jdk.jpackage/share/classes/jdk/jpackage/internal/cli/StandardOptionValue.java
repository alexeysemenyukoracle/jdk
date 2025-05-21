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
import static jdk.jpackage.internal.cli.OptionSpecBuilder2.pathSeparator;
import static jdk.jpackage.internal.cli.OptionSpecBuilder2.toList;
import static jdk.jpackage.internal.cli.OptionValueExceptionFactory.UNREACHABLE_EXCEPTION_FACTORY;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_BUNDLE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_PKG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_NATIVE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.MAC_SIGNING;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.fromOptionName;
import static jdk.jpackage.internal.cli.StandardValidator.isDirectoryEmptyOrNonExistant;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.PackageType;

public final class StandardOptionValue {

    private static final OptionValueExceptionFactory<ConfigException> ERROR_WITH_VALUE =
            OptionValueExceptionFactory.build(ConfigException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE).create();

    private static final OptionValueExceptionFactory<ConfigException> ERROR_WITH_VALUE_AND_OPTION_NAME =
            OptionValueExceptionFactory.build(ConfigException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME).create();

    private static final OptionValueExceptionFactory<ConfigException> ERROR_WITH_OPTION_NAME_AND_VALUE =
            OptionValueExceptionFactory.build(ConfigException::new).formatArgumentsTransformer(StandardArgumentsMapper.NAME_AND_VALUE).create();

    public final static OptionValue<PackageType> TYPE = option("type", PackageType.class).shortName("t")
            .converterExceptionFactory(ERROR_WITH_VALUE).converterExceptionFormatString("ERR_InvalidInstallerType")
            .converter(value -> {
                Objects.requireNonNull(value);
                return Stream.of(StandardBundlingOperation.values()).filter(bundlingOperation -> {
                    return bundlingOperation.packageTypeValue().equals(value);
                }).map(StandardBundlingOperation::packageType).findFirst().orElseThrow(IllegalArgumentException::new);
            }).create();

    public final static OptionValue<Path> INPUT = directoryOption("input").scope(CREATE_APP_IMAGE).shortName("i").create();

    public final static OptionValue<Path> DEST = directoryOption("dest").shortName("d").create();

    public final static OptionValue<String> DESCRIPTION = stringOption("description").create();

    public final static OptionValue<String> VENDOR = stringOption("vendor").create();

    public final static OptionValue<String> APPCLASS = stringOption("main-class").create();

    public final static OptionValue<String> NAME = stringOption("name").shortName("n").create();

    public final static OptionValue<Boolean> VERBOSE = booleanOption("verbose").create();

    public final static OptionValue<Path> RESOURCE_DIR = directoryOption("resource-dir").enhanceScope(CREATE_BUNDLE).enhanceScope(MAC_SIGNING).create();

    public final static OptionValue<List<String>> ARGUMENTS = stringOption("arguments").toArray(stringListTokenizer()).create(toList());

    public final static OptionValue<List<String>> JLINK_OPTIONS = stringOption("jlink-options").toArray(stringListTokenizer()).create(toList());

    public final static OptionValue<Path> ICON = pathOption("icon").create();

    public final static OptionValue<String> COPYRIGHT = stringOption("copyright").create();

    public final static OptionValue<Path> LICENSE_FILE = pathOption("license-file").create();

    public final static OptionValue<String> VERSION = stringOption("app-version").create();

    public final static OptionValue<String> ABOUT_URL = urlOption("about-url").create();

    public final static OptionValue<List<String>> JAVA_OPTIONS = stringOption("java-options").toArray(stringListTokenizer()).create(toList());

    public final static OptionValue<List<Path>> APP_CONTENT = pathOption("app-content").toArray(pathSeparator()).create(toList());

    public final static OptionValue<List<Path>> FILE_ASSOCIATIONS = pathOption("file-associations").toArray(pathSeparator()).create(toList());

    private final static class IllegalAddLauncherSyntaxException extends IllegalArgumentException {

        IllegalAddLauncherSyntaxException() {
        }

        private static final long serialVersionUID = 1L;
    }

    public final static OptionValue<List<AdditionalLauncher>> ADD_LAUNCHER = option("add-launcher", AdditionalLauncher.class)
            .converterExceptionFactory(new OptionValueExceptionFactory<>() {
                @Override
                public ConfigException create(OptionName optionName, String optionValue, String formatString) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ConfigException create(OptionName optionName, String optionValue, String formatString, Throwable cause) {
                    if (cause instanceof IllegalAddLauncherSyntaxException) {
                        return ERROR_WITH_VALUE_AND_OPTION_NAME.create(optionName, optionValue, "error.paramater-add-launcher-malformed");
                    } else {
                        return ERROR_WITH_VALUE_AND_OPTION_NAME.create(optionName, optionValue, "error.paramater-add-launcher-not-path", cause);
                    }
                }
            }).converter(value -> {
                var components = value.split("=", 2);
                if (components.length != 2) {
                    throw new IllegalAddLauncherSyntaxException();
                }
                return new AdditionalLauncher(components[0], StandardValueConverter.pathConv().convert(components[1]));
            }).toOptionValueBuilder().to(List::of).defaultValue(List.of()).create();

    public final static OptionValue<Path> TEMP_ROOT = pathOption("temp")
            .validatorExceptionFactory(ERROR_WITH_VALUE)
            .validatorExceptionFormatString("ERR_BuildRootInvalid")
            .validator(isDirectoryEmptyOrNonExistant())
            .create();

    public final static OptionValue<Path> INSTALL_DIR = pathOption("install-dir").create();

    public final static OptionValue<Path> PREDEFINED_APP_IMAGE = directoryOption("app-image").enhanceScope(CREATE_NATIVE).enhanceScope(SIGN_MAC_APP_IMAGE).create();

    public final static OptionValue<Path> PREDEFINED_RUNTIME_IMAGE = directoryOption("runtime-image").create();

    public final static OptionValue<Path> MAIN_JAR = pathOption("main-jar").create();

    public final static OptionValue<String> MODULE = stringOption("module").shortName("m").create();

    public final static OptionValue<List<String>> ADD_MODULES = stringOption("add-modules").toArray(",").create(toList());

    public final static OptionValue<List<Path>> MODULE_PATH = pathOption("module-path").toArray(pathSeparator()).create(toList());

    public final static OptionValue<Boolean> LAUNCHER_AS_SERVICE = booleanOption("launcher-as-service").create();

    //
    // Linux-specific
    //

    public final static OptionValue<String> LINUX_RELEASE = stringOption("linux-app-release").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> LINUX_BUNDLE_NAME = stringOption("linux-package-name").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> LINUX_DEB_MAINTAINER = stringOption("linux-deb-maintainer").create();

    public final static OptionValue<String> LINUX_CATEGORY = stringOption("linux-app-category").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> LINUX_RPM_LICENSE_TYPE = stringOption("linux-rpm-license-type").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> LINUX_PACKAGE_DEPENDENCIES = stringOption("linux-package-deps").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> LINUX_SHORTCUT_HINT = booleanOption("linux-shortcut").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> LINUX_MENU_GROUP = stringOption("linux-menu-group").scope(limitScope(CREATE_NATIVE)).create();

    //
    // MacOS-specific
    //

    public final static OptionValue<List<Path>> MAC_DMG_CONTENT = pathOption("mac-dmg-content").toArray(pathSeparator()).create(toList());

    public final static OptionValue<Boolean> MAC_SIGN = booleanOption("mac-sign").scope(MAC_SIGNING).shortName("s").create();

    public final static OptionValue<Boolean> MAC_APP_STORE = booleanOption("mac-app-store").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_CATEGORY = stringOption("mac-app-category").create();

    public final static OptionValue<String> MAC_BUNDLE_NAME = stringOption("mac-package-name").create();

    public final static OptionValue<String> MAC_BUNDLE_IDENTIFIER = stringOption("mac-package-identifier").create();

    public final static OptionValue<String> MAC_BUNDLE_SIGNING_PREFIX = stringOption("mac-package-signing-prefix").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_SIGNING_KEY_NAME = stringOption("mac-signing-key-user-name").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_APP_IMAGE_SIGN_IDENTITY = stringOption("mac-app-image-sign-identity").scope(MAC_SIGNING).create();

    public final static OptionValue<String> MAC_INSTALLER_SIGN_IDENTITY = stringOption("mac-installer-sign-identity").scope(CREATE_MAC_PKG).create();

    public final static OptionValue<Path> MAC_SIGNING_KEYCHAIN = pathOption("mac-signing-keychain").scope(MAC_SIGNING).create();

    public final static OptionValue<Path> MAC_ENTITLEMENTS = pathOption("mac-entitlements").scope(MAC_SIGNING).create();

    //
    // Windows-specific
    //

    public final static OptionValue<String> WIN_HELP_URL = stringOption("win-help-url").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> WIN_UPDATE_URL = stringOption("win-update-url").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_MENU_HINT = booleanOption("win-menu").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> WIN_MENU_GROUP = stringOption("win-menu-group").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_SHORTCUT_HINT = booleanOption("win-shortcut").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_SHORTCUT_PROMPT = booleanOption("win-shortcut-prompt").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_PER_USER_INSTALLATION = booleanOption("win-per-user-install").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_DIR_CHOOSER = booleanOption("win-dir-chooser").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<String> WIN_UPGRADE_UUID = stringOption("win-upgrade-uuid").scope(limitScope(CREATE_NATIVE)).create();

    public final static OptionValue<Boolean> WIN_CONSOLE_HINT = booleanOption("win-console").create();

    /**
     * Options in additional launcher .propertiy files.
     */
    public final static Set<OptionValue<?>> LAUNCHER_PROPERTIES = Set.of(
            MAIN_JAR,
            APPCLASS,
            MODULE,
            DESCRIPTION,
            ICON,
            LAUNCHER_AS_SERVICE,
            WIN_CONSOLE_HINT,
            WIN_SHORTCUT_HINT,
            WIN_MENU_HINT,
            LINUX_SHORTCUT_HINT,
            ARGUMENTS,
            JAVA_OPTIONS
    );

    static Set<Option> options() {
        return Stream.of(StandardOptionValue.class.getFields()).filter(f -> {
            return Modifier.isStatic(f.getModifiers());
        }).map(f -> {
            return toFunction(f::get).apply(null);
        }).filter(OptionValue.class::isInstance)
                .map(OptionValue.class::cast)
                .map(OptionValue::id)
                .map(Option.class::cast)
                .collect(toSet());
    }

    private static <T> OptionSpecBuilder2<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder2.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .scope(fromOptionName(name))
                .exceptionFactory(UNREACHABLE_EXCEPTION_FACTORY)
                .exceptionFormatString("");
    }

    private static OptionSpecBuilder2<String> stringOption(String name) {
        return option(name, String.class).converter(identityConv());
    }

    private static OptionSpecBuilder2<Path> pathOption(String name) {
        return option(name, Path.class)
                .converter(pathConv())
                .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .converterExceptionFormatString("error.paramater-not-path");
    }

    private static OptionSpecBuilder2<Path> directoryOption(String name) {
        return pathOption(name)
                .validator(StandardValidator.isDirectory())
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.paramater-not-directory");
    }

    private static OptionSpecBuilder2<String> urlOption(String name) {
        return stringOption(name)
                .validator(StandardValidator.isUrl())
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.paramater-not-url");
    }

    private static OptionSpecBuilder2<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).defaultValue(Boolean.FALSE);
    }

    private static UnaryOperator<Set<OptionScope>> limitScope(Collection<? extends OptionScope> maxScope) {
        return scope -> {
            return scope.stream().filter(maxScope::contains).collect(toSet());
        };
    }

    static Function<String, String[]> stringListTokenizer() {
        return str -> {
            return Arguments.getArgumentList(str).toArray(String[]::new);
        };
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
}
