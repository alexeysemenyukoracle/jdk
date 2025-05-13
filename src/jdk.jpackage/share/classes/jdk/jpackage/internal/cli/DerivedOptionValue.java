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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.model.BundlingOperation;
import jdk.jpackage.internal.model.ExternalApplication;

public final class DerivedOptionValue {

    public final static OptionValue<BundlingOperation> BUNDLING_OPERATION = create();

    public final static OptionValue<ExternalApplication> EXTERNAL_APP = create();

    public final static OptionValue<Boolean> IS_RUNTIME_INSTALLER = create(cmdline -> {
        return StandardBundlingOperation.CREATE_BUNDLE.contains(BUNDLING_OPERATION.getFrom(cmdline))
                && StandardOptionValue.PREDEFINED_RUNTIME_IMAGE.containsIn(cmdline)
                && !StandardOptionValue.PREDEFINED_APP_IMAGE.containsIn(cmdline)
                && !StandardOptionValue.MAIN_JAR.containsIn(cmdline)
                && !StandardOptionValue.MODULE.containsIn(cmdline);
    });

    private static <T> OptionValue<T> create() {
        return OptionValue.create();
    }

    private static <T> OptionValue<T> create(Function<Options, T> getter) {
        Objects.requireNonNull(getter);
        return new OptionValue<>() {
            @Override
            public OptionIdentifier id() {
                return id;
            }

            @Override
            public Optional<T> findIn(Options cmdline) {
                return Optional.of(getter.apply(cmdline));
            }

            private final OptionIdentifier id = OptionIdentifier.createUnique();
        };
    }
}
