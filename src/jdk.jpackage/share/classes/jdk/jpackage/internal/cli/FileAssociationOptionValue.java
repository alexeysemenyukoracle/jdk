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

import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;
import static jdk.jpackage.internal.cli.OptionValueExceptionFactory.UNREACHABLE_EXCEPTION_FACTORY;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.model.JPackageException;

/**
 * jpackage command line options
 */
public final class FileAssociationOptionValue {

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITHOUT_CONTEXT =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.NONE).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_VALUE_AND_OPTION_NAME =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME).create();

    private static final OptionValueExceptionFactory<JPackageException> ERROR_WITH_OPTION_NAME_AND_VALUE =
            OptionValueExceptionFactory.build(JPackageException::new).formatArgumentsTransformer(StandardArgumentsMapper.NAME_AND_VALUE).create();


    public final static OptionValue<List<String>> EXTENSIONS = stringOption("extension").toArray("(,|\\s)+").create(toList());
    
    public final static OptionValue<List<String>> CONTENT_TYPE = stringOption("mime-type").toArray("(,|\\s)+").create(toList());

    public final static OptionValue<String> DESCRIPTION = stringOption("description").create();

    public final static OptionValue<Path> ICON = fileOption("icon").create();

    //
    // Mac options
    //
    public final static OptionValue<String> MAC_CFBUNDLETYPEROLE = stringOption("mac.CFBundleTypeRole").create();

    public final static OptionValue<String> MAC_LSHANDLERRANK = stringOption("mac.LSHandlerRank").create();
    
    public final static OptionValue<String> MAC_NSSTORETYPEKEY = stringOption("mac.NSPersistentStoreTypeKey").create();
    
    public final static OptionValue<String> MAC_NSDOCUMENTCLASS = stringOption("mac.NSDocumentClass").create();
    
    public final static OptionValue<Boolean> MAC_LSTYPEISPACKAGE = booleanOption("mac.LSTypeIsPackage").create();
    
    public final static OptionValue<Boolean> MAC_LSDOCINPLACE = booleanOption("mac.LSSupportsOpeningDocumentsInPlace").create();
    
    public final static OptionValue<Boolean> MAC_UIDOCBROWSER = booleanOption("mac.UISupportsDocumentBrowser").create();
    
    public final static OptionValue<List<String>> MAC_NSEXPORTABLETYPES = stringOption("mac.NSExportableTypes").toArray("(,|\\s)+").create(toList());
    
    public final static OptionValue<List<String>> MAC_UTTYPECONFORMSTO = stringOption("mac.UTTypeConformsTo").toArray("(,|\\s)+").create(toList());

    /**
     * Returns all options with option specs defined in {@link FileAssociationOptionValue} class.
     *
     * @return all defined options
     */
    static Set<Option> options() {
        return Option.getPublicOptionsWithSpecs(FileAssociationOptionValue.class);
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .description("")
                .scope(scopeFromOptionName(name))
                .exceptionFactory(UNREACHABLE_EXCEPTION_FACTORY)
                .exceptionFormatString("");
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).converter(identityConv());
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

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).defaultValue(Boolean.FALSE);
    }

    private static Set<? extends OptionScope> scopeFromOptionName(String name) {
        if (name.startsWith("mac.")) {
            return Set.of(StandardBundlingOperation.CREATE_MAC_PKG);
        } else {
            return StandardBundlingOperation.CREATE_NATIVE;
        }
    }
}
