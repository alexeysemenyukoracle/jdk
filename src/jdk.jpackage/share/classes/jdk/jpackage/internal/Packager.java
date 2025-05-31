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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import jdk.jpackage.internal.PackagingPipeline.StartupParameters;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOptionValue;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.Package;

final class Packager {

    Packager(BiConsumer<StartupParameters, PackagingPipeline.Builder> pipelineConfigurer) {
        this.pipelineConfigurer = Objects.requireNonNull(pipelineConfigurer);
    }

    private Packager(Packager other) {
        pipelineConfigurer = other.pipelineConfigurer;
        optionValues = other.optionValues;
        pkg = other.pkg;
        app = other.app;
        env = other.env;
        outputDir = other.outputDir;
    }

    Packager optionValues(Options v) {
        optionValues = v;
        return this;
    }

    Packager pkg(Package v) {
        pkg = v;
        return this;
    }

    Packager app(Application v) {
        app = v;
        return this;
    }

    Packager env(BuildEnv v) {
        env = v;
        return this;
    }

    Packager outputDir(Path v) {
        outputDir = v;
        return this;
    }

    Path execute(PackagingPipeline.Builder pipelineBuilder) {
        if (env == null) {
            Objects.requireNonNull(optionValues);
            var copy = new Packager(this);
            try (var tempDir = new TempDirectory(optionValues)) {
                copy.env(copy.optionValues(tempDir.optionValues()).createBuildEnv());
                return copy.execute(pipelineBuilder);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        } else if (pkg != null) {
            return createPackage(pipelineBuilder);
        } else {
            return createApplication(pipelineBuilder);
        }
    }

    private Path createPackage(PackagingPipeline.Builder pipelineBuilder) {
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(env);
        Objects.requireNonNull(pipelineConfigurer);
        
        final var validatedOutputDir = validatedOutputDir();

        final var startupParameters = pipelineBuilder.createStartupParameters(env, pkg, validatedOutputDir);

        pipelineConfigurer.accept(startupParameters, pipelineBuilder);

        pipelineBuilder.create().execute(startupParameters);

        return validatedOutputDir.resolve(pkg.packageFileNameWithSuffix());
    }

    private Path createApplication(PackagingPipeline.Builder pipelineBuilder) {
        Objects.requireNonNull(app);
        Objects.requireNonNull(env);
        Objects.requireNonNull(pipelineConfigurer);

        final var startupParameters = pipelineBuilder.createStartupParameters(env, app);

        pipelineConfigurer.accept(startupParameters, pipelineBuilder);

        pipelineBuilder.create().execute(startupParameters);

        return validatedOutputDir.resolve(pkg.packageFileNameWithSuffix());
    }

    private Path validatedOutputDir() {
        return Optional.ofNullable(outputDir).or(() -> {
            return StandardOptionValue.DEST.findIn(optionValues);
        }).orElseGet(() -> { 
            return Path.of("."); 
        });
    }

    private BuildEnv createBuildEnv() {
        if (pkg != null) {
            return BuildEnvFromOptions.create(optionValues, pkg);
        } else {
            return BuildEnvFromOptions.create(optionValues, app);
        }
    }

    private final BiConsumer<StartupParameters, PackagingPipeline.Builder> pipelineConfigurer;
    private Options optionValues;
    private Package pkg;
    private Application app;
    private BuildEnv env;
    private Path outputDir;
}
