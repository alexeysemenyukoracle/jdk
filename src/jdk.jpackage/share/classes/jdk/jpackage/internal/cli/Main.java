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

import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Log;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.KnownExceptionType;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * Main jpackage entry point.
 */
public final class Main {

    private Main() {
    }

    public static int main(String args[]) {

        final var bundlingEnv = ServiceLoader.load(CliBundlingEnvironment.class,
                CliBundlingEnvironment.class.getClassLoader()).findFirst().orElseThrow();

        final var parseResult = Utils.buildParser(OperatingSystem.current(), bundlingEnv).create().apply(args);

        return new Runner().run(() -> {
            final var optionsProcessor = new OptionsProcessor(parseResult.orElseThrow(), bundlingEnv);

            final var validationResult = optionsProcessor.validate();

            final var bundlingResult = validationResult.map(optionsProcessor::runBundling);

            if (bundlingResult.hasValue()) {
                return bundlingResult.orElseThrow();
            } else {
                return bundlingResult.errors();
            }
        });
    }


    static final class Runner {

        int run(Supplier<? extends Collection<? extends Exception>> r) {
            final var exceptions = runIt(r);
            exceptions.forEach(this::reportError);
            if (exceptions.isEmpty()) {
                return 0;
            } else {
                return 1;
            }
        }

        private void reportError(Throwable t) {
            if (t instanceof ConfigException cfgEx) {
                printError(cfgEx, Optional.ofNullable(cfgEx.getAdvice()));
            } else if (t instanceof KnownExceptionType) {
                printError(t);
            } else if (t instanceof ExceptionBox ex) {
                reportError(ex.getCause());
            } else if (t instanceof UncheckedIOException ex) {
                reportError(ex.getCause());
            } else {
//                throw new AssertionError("Internal error", t);
                printError(t);
            }
        }

        private void printError(Throwable t) {
            printError(t, Optional.empty());
        }

        private void printError(Throwable t, Optional<String> advice) {
            Log.fatalError(I18N.format("message.error-header", t.getMessage()));
            advice.ifPresent(v -> Log.fatalError(I18N.format("message.advice-header", v)));
            Log.verbose(t);
        }

        private static Collection<? extends Exception> runIt(Supplier<? extends Collection<? extends Exception>> r) {
            try {
                return r.get();
            } catch (RuntimeException ex) {
                return List.of(ex);
            }
        }
    }
}
