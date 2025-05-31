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

import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOptionValue.RESOURCE_DIR;
import static jdk.jpackage.internal.cli.StandardOptionValue.TEMP_ROOT;
import static jdk.jpackage.internal.cli.StandardOptionValue.VERBOSE;

import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.Package;

final class BuildEnvFromOptions {

    static BuildEnv create(Options optionValues, Application app) {
        return create(optionValues, app, Optional.empty());
    }

    static BuildEnv create(Options optionValues, Package pkg) {
        return create(optionValues, pkg.app(), Optional.of(pkg));
    }

    private static BuildEnv create(Options optionValues, Application app, Optional<Package> pkg) {
        Objects.requireNonNull(optionValues);
        Objects.requireNonNull(app);
        Objects.requireNonNull(pkg);

        final var builder = new BuildEnvBuilder(TEMP_ROOT.getFrom(optionValues));

        RESOURCE_DIR.copyInto(optionValues, builder::resourceDir);
        VERBOSE.copyInto(optionValues, builder::verbose);

        if (app.isRuntime()) {
            builder.appImageDir(PREDEFINED_RUNTIME_IMAGE.getFrom(optionValues));
        } else if (PREDEFINED_APP_IMAGE.containsIn(optionValues)) {
            builder.appImageDir(PREDEFINED_APP_IMAGE.getFrom(optionValues));
        } else if (pkg.isPresent()) {
            builder.appImageDirForPackage();
        } else {
            builder.appImageDirFor(app);
        }

        return builder.create();
    }
}
