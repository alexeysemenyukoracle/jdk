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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
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
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class JOptSimpleOptionsBuilderTest {

    enum ParserMode {
        PARSE,
        CONVERT,
        VALIDATE
    }

    public record TestSpec(Map<OptionValue<?>, Object> options, List<String> args) {
        public TestSpec {
            Objects.requireNonNull(options);
            Objects.requireNonNull(args);
        }

        void test(ParserMode parserMode) {
            final var parser = createParser(parserMode, options.keySet());

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

        void run(ParserMode parserMode) {
            final var parser = createParser(parserMode, OV);
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
                args.addAll(List.of(LONG_NAME.formatForCommandLine(), FOO));
            };
        }

        private static Consumer<List<String>> addShortValue() {
            return args -> {
                args.addAll(List.of(SHORT_NAME.formatForCommandLine(), BAR));
            };
        }

        private final Consumer<List<String>> optionInitializer;
        private final Predicate<Options> validator;

        private final static OptionName LONG_NAME = OptionName.of("input");
        private final static OptionName SHORT_NAME = OptionName.of("i");

        private final static OptionValue<String> OV = build(LONG_NAME.name()).shortName(SHORT_NAME.name()).ofString();

        private final static String FOO = "foo";
        private final static String BAR = "bar";
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortName(ShortNameTestCase testCase) {
        List.of(ParserMode.values()).forEach(testCase::run);
    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec spec) {
        spec.test(ParserMode.VALIDATE);
    }

    @ParameterizedTest
    @MethodSource
    public void testStringVector(TestSpec spec) {
        List.of(ParserMode.CONVERT, ParserMode.VALIDATE).forEach(spec::test);
    }

    @Test
    public void testConversionVsValidation(@TempDir Path tmpDir) {
        final var nonExistentDir = tmpDir.resolve("non-existent");

        final var testSpec = build().addOptionValue(build("dir").ofDirectory(), nonExistentDir).addArgs("--dir", nonExistentDir.toString()).create();

        testSpec.test(ParserMode.CONVERT);

        final var ex = assertThrowsExactly(TestException.class, () -> testSpec.test(ParserMode.VALIDATE));

        assertEquals(String.format(FORMAT_STRING_NOT_DIRECTORY, nonExistentDir, "--dir"), ex.getMessage());
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
                        build("arguments").convert().split("\\s+").toStringArray().toOptionValueBuilder().to(List::of).create(),
                        List.of("", "a", "b", "c", "", "de")
                ).addArgs(
                        "--arguments", " a b  c", "--arguments", " de"
                ),

                build().addOptionValue(
                        build("arguments").convert().split(";+").toStringArray().toOptionValueBuilder().to(List::of).create(),
                        List.of("a b", "c", "de")
                ).addArgs(
                        "--arguments", "a b;;c", "--arguments", "de;"
                ),

                build().addOptionValue(build("foo").ofString(), "--foo").addArgs("--foo", "--foo"),

                build().addOptionValue(build("foo").noValue(), true).addOptionValue(build("bar").noValue(), false).addArgs("--foo")
        ).map(TestSpec.Builder::create).toList();
    }

    private static Collection<TestSpec> testStringVector() {
        final var args = List.of("--foo", "1 22 333", "--foo", "44 44");
        return Stream.of(
                build().addOptionValue(
                        build("foo").convert().nosplit().toStringArray().createOptionValue(),
                        new String[] { "1 22 333", "44 44" }
                ).addArgs(args),

                build().addOptionValue(
                        build("foo").convert().nosplit().toStringArray().toOptionValueBuilder().to(List::of).create(),
                        List.of("1 22 333", "44 44")
                ).addArgs(args),

                build().addOptionValue(
                        build("foo").convert().split("\\s+").toStringArray().createOptionValue(),
                        new String[] { "1", "22", "333", "44", "44" }
                ).addArgs(args),

                build().addOptionValue(
                        build("foo").convert().split("\\s+").toStringArray().toOptionValueBuilder().to(List::of).create(),
                        List.of("1", "22", "333", "44", "44")
                ).addArgs(args)
        ).map(TestSpec.Builder::create).toList();
    }

    private static OptionSpecBuilder<String> build(String optionName) {
        final var builder = OptionSpecBuilder.create().name(optionName).scope(new BundlingOperationOptionScope() {});

        builder.setConverterBuilder(Path.class, () -> {
            return exceptionsForPathValues(OptionValueConverter.build(Path.class));
        });
        builder.setConverterBuilder(Path[].class, () -> {
            return exceptionsForPathValues(OptionValueConverter.build(Path[].class));
        });

        builder.setValidatorBuilder(StandardValidator.IS_DIRECTORY, () -> {
            return Validator.build(Path.class, ERROR_WITH_VALUE_AND_OPTION_NAME).formatString(FORMAT_STRING_NOT_DIRECTORY);
        });
        builder.setValidatorBuilder(StandardValidator.IS_URL, () -> {
            return Validator.build(String.class, ERROR_WITH_VALUE_AND_OPTION_NAME).formatString(FORMAT_STRING_NOT_URL);
        });

        return builder;
    }

    @SafeVarargs
    private static Function<List<String>, Options> createParser(ParserMode mode, OptionValue<?>... options) {
        return createParser(mode, List.of(options));
    }

    private static Function<List<String>, Options> createParser(ParserMode mode, Iterable<OptionValue<?>> options) {
        Objects.requireNonNull(mode);
        final var parse = new JOptSimpleOptionsBuilder().options(StreamSupport.stream(options.spliterator(), false)
                .map(OptionValue::asOption).map(Optional::orElseThrow).toList()).create();
        return args -> {
            final var builder = parse.apply(args.toArray(String[]::new)).orElseThrow();
            switch (mode) {
                case PARSE -> {
                    return builder.create();
                }
                case CONVERT -> {
                    return builder.convertedOptions().orElseThrow().create();
                }
                case VALIDATE -> {
                    return builder.convertedOptions().orElseThrow().validatedOptions().orElseThrow().create();
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        };
    }

    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }

    private static <T> OptionValueConverter.Builder<T> exceptionsForPathValues(OptionValueConverter.Builder<T> builder) {
        return builder.formatString(FORMAT_STRING_ILLEGAL_PATH).exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME);
    }


    final static class TestException extends RuntimeException {

        TestException(String msg, Throwable cause) {
            super(msg, cause);
        }

        private static final long serialVersionUID = 1L;
    }



    private final static String FORMAT_STRING_ILLEGAL_PATH = "The value '%s' provided for parameter %s is not a valid path";

    private final static String FORMAT_STRING_NOT_DIRECTORY = "The value '%s' provided for parameter %s is not a directory path";

    private final static String FORMAT_STRING_NOT_URL = "The value '%s' provided for parameter %s is not a URL";

    private static final OptionValueExceptionFactory<? extends RuntimeException> ERROR_WITH_VALUE_AND_OPTION_NAME = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME)
            .messageFormatter(String::format)
            .create();
}
