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

import static jdk.jpackage.internal.cli.OptionSpec.formatOptionNameForCommandLine;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class JOptSimpleBuilderTest {

    public record TestSpec(Map<OptionValue<?>, Object> options, List<String> args) {
        public TestSpec {
            Objects.requireNonNull(options);
            Objects.requireNonNull(args);
        }

        void test() {
            final var parser = createParser(options.keySet());

            final var cmdline = parser.apply(args);

            for (final var e : options.entrySet()) {
                final var optionValue = e.getKey();
                final var expectedValue = e.getValue();
                final var actualValue = optionValue.getFrom(cmdline);
                if (expectedValue.getClass().isArray()) {
                    assertArrayEquals((Object[])expectedValue, (Object[])actualValue);
                } else {
                    assertEquals(expectedValue, actualValue);
                }
            }
        }

        static class Builder {

            TestSpec create() {
                return new TestSpec(options, args);
            }

            Builder addOptionValue(OptionValue<?> option, Object expectedValue) {
                options.put(option, expectedValue);
                return this;
            }

            Builder addArgs(String...v) {
                return addArgs(List.of(v));
            }

            Builder addArgs(Collection<String> v) {
                args.addAll(v);
                return this;
            }

            private final Map<OptionValue<?>, Object> options = new HashMap<>();
            private final List<String> args = new ArrayList<>();
        }
    }

    enum ShortNameTestCase {
        LONG(hasLongOption().and(hasShortOption().negate()), addLongValue()),
        SHORT(hasLongOption().negate().and(hasShortOption()), addShortValue()),
        LONG_AND_SHORT(hasLongOption().and(hasShortOption()), addLongValue(), addShortValue()),
        SHORT_AND_LONG(hasLongOption().and(hasShortOption()), addShortValue(), addLongValue()),
        NONE(hasLongOption().negate().and(hasShortOption().negate()));

        @SafeVarargs
        ShortNameTestCase(Predicate<Options> validator, Consumer<List<String>>... optionInitializers) {
            this.optionInitializer = args -> {
                for (final var optionInitializer : optionInitializers) {
                    optionInitializer.accept(args);
                }
            };
            this.validator = validator;
        }

        void run() {
            final var parser = createParser(OV);
            final List<String> args = new ArrayList<>();
            optionInitializer.accept(args);
            assertTrue(validator.test(parser.apply(args)));
        }

        private static Predicate<Options> hasLongOption() {
            return cmdline -> {
                return cmdline.contains(LONG_NAME);
            };
        }

        private static Predicate<Options> hasShortOption() {
            return cmdline -> {
                return cmdline.contains(SHORT_NAME);
            };
        }

        private static Consumer<List<String>> addLongValue() {
            return args -> {
                args.addAll(List.of(formatOptionNameForCommandLine(LONG_NAME), FOO));
            };
        }

        private static Consumer<List<String>> addShortValue() {
            return args -> {
                args.addAll(List.of(formatOptionNameForCommandLine(SHORT_NAME), BAR));
            };
        }

        private final Consumer<List<String>> optionInitializer;
        private final Predicate<Options> validator;

        private final static String LONG_NAME = "input";
        private final static String SHORT_NAME = "i";

        private final static OptionValue<String> OV = build(LONG_NAME).shortName(SHORT_NAME).ofString();

        private final static String FOO = "foo";
        private final static String BAR = "bar";
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortName(ShortNameTestCase testCase) {
        testCase.run();
    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec spec) {
        spec.test();
    }

    private static Collection<TestSpec> test() {
        final var pwd = Path.of("").toAbsolutePath();
        return Stream.of(
                build().addOptionValue(
                        build("input").shortName("i").ofDirectory(),
                        pwd
                ).addArgs(
                        "--input", "", "-i", pwd.toString()
                ),

                build().addOptionValue(
                        build("arguments").ofStringList(),
                        List.of("", "a", "b", "c", "", "de")
                ).addArgs(
                        "--arguments", " a b  c", "--arguments", " de"
                ),

                build().addOptionValue(
                        build("arguments").withoutValueSeparator().ofStringList(),
                        List.of("a b c", "de")
                ).addArgs(
                        "--arguments", "a b c", "--arguments", "de"
                ),

                build().addOptionValue(
                        build("arguments").valueSeparator(";+").ofStringList(),
                        List.of("a b", "c", "de")
                ).addArgs(
                        "--arguments", "a b;;c", "--arguments", "de;"
                ),

                build().addOptionValue(build("foo").ofString(), "--foo").addArgs("--foo", "--foo"),
                build().addOptionValue(build("foo").ofStringArray().create(), new String[] { "--foo" }).addArgs("--foo", "--foo"),

                build().addOptionValue(build("foo").noValue(), true).addOptionValue(build("bar").noValue(), false).addArgs("--foo")
        ).map(TestSpec.Builder::create).toList();
    }

    private static OptionSpecBuilder build(String optionName) {
        return new OptionSpecBuilder().name(optionName).scope(new BundlingOperationOptionScope() {});
    }

    @SafeVarargs
    private static Function<List<String>, Options> createParser(OptionValue<?>... options) {
        return createParser(List.of(options));
    }

    private static Function<List<String>, Options> createParser(Iterable<OptionValue<?>> options) {
        return JOptSimpleBuilder.createParser(StreamSupport.stream(options.spliterator(), false)
                .map(OptionValue::asOption).map(Optional::orElseThrow).toList());
    }

    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }
}
