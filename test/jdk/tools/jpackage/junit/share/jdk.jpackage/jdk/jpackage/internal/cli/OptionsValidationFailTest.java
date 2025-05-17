/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundleCreator;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.BundlingOperation;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JUnitAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Run jpackage jtreg ErrorTest test with the custom jpackage tool provider.
 * <p>
 * ErrorTest is comprised of test cases that pass faulty command line arguments to jpackage and expect it to fail.
 * It is natural to reuse these test cases to test jpackage command line validation.
 *
 * <p>
 * The scenario breaks down into two components:
 * <ol>
 *  <li>Load ErrorTest jtreg test
 *  <li>Setup custom jpackage tool provider
 * </ol>
 *
 * <h1>1. Load ErrorTest jtreg test</h1>
 * <p>
 * ErrorTest and its nested classes reside in the unnamed module together with
 * the classes from the "jdk.jpackage.test" package.
 * <p>
 * There is no straightforward way to access classes in the unnamed package outside their module.
 * However, these classes (class files) can be accessed as resources. So the workaround
 * is to read class files of classes in the unnamed package and load them using a custom class loader.
 * <p>
 * jpackage jtreg tests are in the unnamed package according to jtreg recommendations, see
 * https://openjdk.org/jtreg/faq.html#how-should-i-organize-tests-libraries-and-other-test-related-files
 */
public class OptionsValidationFailTest {

    @BeforeAll
    public static void setCustomJPackageToolProvider() {
        JPackageCommand.useToolProviderByDefault(new ToolProvider() {

            @Override
            public String name() {
                return "jpackage-mockup";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                // Parse command line
                final var options = JOptSimpleBuilder.createParser().apply(List.of(args));

                // Validate parsed options
                final var validationError = new OptionsProcessor(options, new BundlingEnvironment() {

                    @Override
                    public BundlingOperation defaultOperation() {
                        // TODO Auto-generated method stub
                        return null;
                    }

                    @Override
                    public Set<BundlingOperation> supportedOperations() {
                        return StandardBundlingOperation.ofPlatform(OperatingSystem.current()).collect(Collectors.toSet());
                    }

                    @Override
                    public BundleCreator<?> getBundleCreator(BundlingOperation op) {
                        throw new UnsupportedOperationException();
                    }

                }).validate().orElseThrow();

                out.append(validationError.getMessage());
                validationError.printStackTrace(err);

                return -1;
            }
        });
    }

    @AfterAll
    public static void resetJPackageToolProvider() {
        JPackageCommand.useToolProviderByDefault();
    }

    @TestFactory
    Stream<DynamicTest> getTestCasesFromErrorTest() throws Throwable {
        final var jpackageTestsUnnamedModule = JUnitAdapter.class.getModule();

        final var testClassloader = new InMemoryClassLoader(Stream.of(
                "ErrorTest",
                "ErrorTest$ArgumentGroup",
                "ErrorTest$PackageTypeSpec",
                "ErrorTest$TestSpec$Builder",
                "ErrorTest$TestSpec",
                "ErrorTest$Token",
                "ErrorTest$UnsupportedPlatformOption"
        ).collect(Collectors.toMap(x -> x, className -> {
            try (final var in = jpackageTestsUnnamedModule.getResourceAsStream(className + ".class")) {
                final var buffer= new ByteArrayOutputStream();
                in.transferTo(buffer);
                return buffer.toByteArray();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        })));

        final var thisModule = getClass().getModule();
        if (thisModule.isNamed()) {
            for (final var m : List.of(testClassloader.getUnnamedModule(), jpackageTestsUnnamedModule)) {
                thisModule.addOpens("jdk.jpackage.internal", m);
            }
        }

        return JUnitAdapter.createJPackageTests(testClassloader, "--jpt-run=ErrorTest.test");
    }

    private static final class InMemoryClassLoader extends ClassLoader {
        InMemoryClassLoader(Map<String, byte[]> classes) {
            super(InMemoryClassLoader.class.getClassLoader());
            this.classes = Objects.requireNonNull(classes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            final var classBytes = classes.get(name);
            if (classBytes != null) {
                return defineClass(name, classBytes, 0, classBytes.length);
            } else {
                return getParent().loadClass(name);
            }
        }

        private final Map<String, byte[]> classes;
    }

    static {
        // Initialize JUnitAdapter class to set the value of the "test.src" property
        // when the test is executed by somebody else but not jtreg.
        new JUnitAdapter();
    }
}
