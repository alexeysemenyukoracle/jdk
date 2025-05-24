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

import static jdk.jpackage.internal.cli.OptionSpecBuilder.pathSeparator;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;
import static jdk.jpackage.internal.cli.OptionValueExceptionFactory.UNREACHABLE_EXCEPTION_FACTORY;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.cli.TestUtils.OptionFailure;
import jdk.jpackage.internal.cli.TestUtils.TestException;
import jdk.jpackage.internal.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        LONG(hasLongOption().and(hasShortOption().negate()), addLongOption()),
        SHORT(hasLongOption().negate().and(hasShortOption()), addShortOption()),
        LONG_AND_SHORT(hasLongOption().and(hasShortOption()), addLongOption(), addShortOption()),
        SHORT_AND_LONG(hasLongOption().and(hasShortOption()), addShortOption(), addLongOption()),
        NONE(hasLongOption().negate().and(hasShortOption().negate()))
        ;

        @SafeVarargs
        ShortNameTestCase(Predicate<Options> validator, BiConsumer<Type, List<String>>... optionInitializers) {
            this.optionInitializer = (type, args) -> {
                for (final var optionInitializer : optionInitializers) {
                    optionInitializer.accept(type, args);
                }
            };
            this.validator = validator;
        }

        void run(Type type, ParserMode parserMode) {
            final var parser = createParser(parserMode, type.optionValue);
            final List<String> args = new ArrayList<>();
            optionInitializer.accept(type, args);
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

        private static BiConsumer<Type, List<String>> addLongOption() {
            return (type, args) -> {
                args.add(LONG_NAME.formatForCommandLine());
                type.valueInitializer.accept(FOO, args);
            };
        }

        private static BiConsumer<Type, List<String>> addShortOption() {
            return (type, args) -> {
                args.add(SHORT_NAME.formatForCommandLine());
                type.valueInitializer.accept(BAR, args);
            };
        }

        private enum Type {
            STRING(stringOption(LONG_NAME.name()).shortName(SHORT_NAME.name()).create(), (optionValue, args) -> {
                args.add(optionValue);
            }),
            BOOLEAN(booleanOption(LONG_NAME.name()).shortName(SHORT_NAME.name()).create(), (optionValue, args) -> {}),
            ;

            Type(OptionValue<?> optionValue, BiConsumer<String, List<String>> valueInitializer) {
                this.optionValue = Objects.requireNonNull(optionValue);
                this.valueInitializer = Objects.requireNonNull(valueInitializer);
            }

            final OptionValue<?> optionValue;
            final BiConsumer<String, List<String>> valueInitializer;
        }

        private final BiConsumer<Type, List<String>> optionInitializer;
        private final Predicate<Options> validator;

        private final static OptionName LONG_NAME = OptionName.of("input");
        private final static OptionName SHORT_NAME = OptionName.of("i");

        private final static String FOO = "foo";
        private final static String BAR = "bar";
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortNameString(ShortNameTestCase testCase) {
        for (final var parserMode : ParserMode.values()) {
            testCase.run(ShortNameTestCase.Type.STRING, parserMode);
        }
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortNameBoolean(ShortNameTestCase testCase) {
        for (final var parserMode : List.of(ParserMode.CONVERT)) {
            testCase.run(ShortNameTestCase.Type.BOOLEAN, parserMode);
        }
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testConversionVsValidation(boolean asArray, @TempDir Path tmpDir) {
        final var nonExistentDir = tmpDir.resolve("non-existent");

        final TestSpec.Builder builder = build().addArgs("--dir", nonExistentDir.toString());
        if (asArray) {
            builder.addOptionValue(directoryOption("dir").toArray().create(), new Path[] { nonExistentDir });
        } else {
            builder.addOptionValue(directoryOption("dir").create(), nonExistentDir);
        }

        final var testSpec = builder.create();

        testSpec.test(ParserMode.CONVERT);

        final var ex = assertThrowsExactly(TestException.class, () -> testSpec.test(ParserMode.VALIDATE));

        assertEquals(String.format(FORMAT_STRING_NOT_DIRECTORY, nonExistentDir, "--dir"), ex.getMessage());
    }

    @Test
    public void testConversionErrors(@TempDir Path tmpDir) {

        final var dirOption = directoryOption("dir").shortName("r").toArray(",");

        final var urlOption = stringOption("url").converter(str -> {
            try {
                new URI(str);
                return str;
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        });

        final var lruOption = option("lru", URI.class).converter(str -> {
            try {
                return new URI(str);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        });

        new FaultyParserArgsConfig()
                .options(urlOption, lruOption)
                .arrayOptions(dirOption)
                .args("--dir=*,foo,,bar", "-r", "file", "-r", "file,*")
                .args("--url=http://foo", "--url=:foo")
                .args("--lru=:bar", "--lru=http://bar")
                .expectError("dir", StringToken.of("*,foo,,bar", "*"))
                .expectError("r", StringToken.of("file,*", "*"))
                .expectError("url", ":foo")
                .expectError("lru", ":bar")
                .test(ParserMode.CONVERT);
    }

    @Test
    public void testValidationErrors() {

        final var numberArrayOption = option("number", Integer.class)
                .shortName("n")
                .validator((Predicate<Integer>)(v -> v > 0))
                .toArray(",")
                .converter(Integer::valueOf);

        new FaultyParserArgsConfig()
                .arrayOptions(numberArrayOption)
                .args("--number=56,23", "--number=2,-34,-45", "-n", "2,-17,0,56")
                .expectError("number", StringToken.of("2,-34,-45", "-34"))
                .expectError("number", StringToken.of("2,-34,-45", "-45"))
                .expectError("n", StringToken.of("2,-17,0,56", "-17"))
                .expectError("n", StringToken.of("2,-17,0,56", "0"))
                .test(ParserMode.VALIDATE);
    }

    @ParameterizedTest
    @MethodSource
    public void testUnrecognizedOptionMapping(String expectedUnrecognizedOption, String[] args) {
        final var parse = new JOptSimpleOptionsBuilder()
                .unrecognizedOptionHandler(optionName -> {
                    return new TestException("Unrecognized=" + OptionName.of(optionName).formatForCommandLine());
                })
                .create();

        final var expectedExceptions = List.of(new TestException("Unrecognized=" + expectedUnrecognizedOption));
        final var actualExceptions = parse.apply(args).errors();

        assertEquals(
                expectedExceptions.stream().map(Exception::getMessage).sorted().toList(),
                actualExceptions.stream().map(Exception::getMessage).sorted().toList());
    }

    private static Collection<Object[]> testUnrecognizedOptionMapping() {
        return List.<Object[]>of(
                unrecognizedOptionMappingTestCase("-a", "-a"),
                unrecognizedOptionMappingTestCase("-a", "--a"),
                unrecognizedOptionMappingTestCase("-f", "-foo"),
                // Two unrecognizable options, only the first one will be reported
                unrecognizedOptionMappingTestCase("--foo", "--foo", "-z"),
                unrecognizedOptionMappingTestCase("-z", "-z", "--foo")
        );
    }

    private static Object[] unrecognizedOptionMappingTestCase(String expectedUnrecognizedOption, String... args) {
        Objects.requireNonNull(expectedUnrecognizedOption);
        return new Object[] { expectedUnrecognizedOption, args };
    }

    private static Collection<TestSpec> test() {
        final var pwd = Path.of("").toAbsolutePath();
        return Stream.of(
                build().addOptionValue(
                        directoryOption("input").shortName("i").create(),
                        pwd
                ).addArgs("--input", "", "-i", pwd.toString()),

                build().addOptionValue(
                        directoryOption("dir").toArray(pathSeparator()).create(),
                        new Path[] { pwd, Path.of(".") }
                ).addArgs("--dir=" + pwd.toString() + pathSeparator() + "."),

                build().addOptionValue(
                        stringOption("arguments").toArray("\\s+").create(toList()),
                        List.of("", "a", "b", "c", "", "de")
                ).addArgs("--arguments", " a b  c", "--arguments", " de"),

                build().addOptionValue(
                        stringOption("arguments").toArray(";+").create(toList()),
                        List.of("a b", "c", "de")
                ).addArgs("--arguments", "a b;;c", "--arguments", "de;"),

                build().addOptionValue(stringOption("foo").create(), "--foo").addArgs("--foo", "--foo"),

                build().addArgs("--foo")
                        .addOptionValue(booleanOption("foo").create(), true)
                        .addOptionValue(booleanOption("bar").create(), false)
        ).map(TestSpec.Builder::create).toList();
    }

    private static Collection<TestSpec> testStringVector() {
        final var args = List.of("--foo", "1 22 333", "--foo", "44 44");
        return Stream.of(
                build().addOptionValue(
                        stringOption("foo").toArray().create(),
                        new String[] { "1 22 333", "44 44" }
                ).addArgs(args),

                build().addOptionValue(
                        stringOption("foo").toArray().create(toList()),
                        List.of("1 22 333", "44 44")
                ).addArgs(args),

                build().addOptionValue(
                        stringOption("foo").toArray("\\s+").create(),
                        new String[] { "1", "22", "333", "44", "44" }
                ).addArgs(args),

                build().addOptionValue(
                        stringOption("foo").toArray("\\s+").create(toList()),
                        List.of("1", "22", "333", "44", "44")
                ).addArgs(args)
        ).map(TestSpec.Builder::create).toList();
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .scope(new BundlingOperationOptionScope() {})
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
                .converterExceptionFormatString(FORMAT_STRING_ILLEGAL_PATH);
    }

    private static OptionSpecBuilder<Path> directoryOption(String name) {
        return pathOption(name)
                .validator(StandardValidator.IS_DIRECTORY)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString(FORMAT_STRING_NOT_DIRECTORY);
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).defaultValue(Boolean.FALSE);
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


    private static final class FaultyParserArgsConfig {

        void test(ParserMode parserMode) {

            final Collection<OptionFailure> actualErrors = new ArrayList<>();

            final List<OptionValue<?>> optionValues = new ArrayList<>();

            optionSpecBuilders.stream().map(builder -> {
                configureExceptions(builder);
                return builder.exceptionFactory(TestUtils.recordExceptions(actualErrors));
            }).map(OptionSpecBuilder::create).forEach(optionValues::add);

            arrayOptionSpecBuilders.stream().map(builder -> {
                configureExceptions(builder.outer());
                return builder.exceptionFactory(TestUtils.recordExceptions(actualErrors));
            }).map(OptionSpecBuilder<?>.ArrayOptionSpecBuilder::create).forEach(optionValues::add);

            final var parser = new JOptSimpleOptionsBuilder().optionValues(optionValues).create();

            final Result<?> cmdline;
            switch (parserMode) {
                case CONVERT -> {
                    cmdline = parser.apply(args.toArray(String[]::new)).orElseThrow().convertedOptions();
                }
                case VALIDATE -> {
                    cmdline = parser.apply(args.toArray(String[]::new))
                            .orElseThrow().convertedOptions().orElseThrow().validatedOptions();
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }

            assertFalse(cmdline.hasValue());

            TestUtils.assertOptionFailuresEquals(expectedErrors,
                    actualErrors.stream().map(OptionFailure::withoutException).toList());
        }

        FaultyParserArgsConfig args(Collection<String> v) {
            args.addAll(v);
            return this;
        }

        FaultyParserArgsConfig args(String... v) {
            return args(List.of(v));
        }

        FaultyParserArgsConfig options(Collection<OptionSpecBuilder<?>> v) {
            optionSpecBuilders.addAll(v);
            return this;
        }

        FaultyParserArgsConfig options(OptionSpecBuilder<?>... v) {
            return options(List.of(v));
        }

        FaultyParserArgsConfig arrayOptions(Collection<OptionSpecBuilder<?>.ArrayOptionSpecBuilder> v) {
            arrayOptionSpecBuilders.addAll(v);
            return this;
        }

        FaultyParserArgsConfig arrayOptions(OptionSpecBuilder<?>.ArrayOptionSpecBuilder... v) {
            return arrayOptions(List.of(v));
        }

        FaultyParserArgsConfig expectErrors(Collection<OptionFailure> v) {
            expectedErrors.addAll(v);
            return this;
        }

        FaultyParserArgsConfig expectErrors(OptionFailure... v) {
            return expectErrors(List.of(v));
        }

        FaultyParserArgsConfig expectError(String optionName, String optionValue) {
            return expectError(optionName, StringToken.of(optionValue));
        }

        FaultyParserArgsConfig expectError(String optionName, StringToken optionValue) {
            return expectErrors(new OptionFailure(optionName, optionValue));
        }

        private static void configureExceptions(OptionSpecBuilder<?> builder) {
            builder.exceptionFactory(factory -> {
                if (factory == null || factory == UNREACHABLE_EXCEPTION_FACTORY) {
                    builder.exceptionFormatString("Option value [%s] of option %s");
                    factory = ERROR_WITH_VALUE_AND_OPTION_NAME;
                }
                return factory;
            });
        }

        private final List<String> args = new ArrayList<>();
        private final Collection<OptionSpecBuilder<?>> optionSpecBuilders = new ArrayList<>();
        private final Collection<OptionSpecBuilder<?>.ArrayOptionSpecBuilder> arrayOptionSpecBuilders = new ArrayList<>();
        private final Collection<OptionFailure> expectedErrors = new ArrayList<>();
    }


    private final static String FORMAT_STRING_ILLEGAL_PATH = "The value '%s' provided for parameter %s is not a valid path";

    private final static String FORMAT_STRING_NOT_DIRECTORY = "The value '%s' provided for parameter %s is not a directory path";

    private static final OptionValueExceptionFactory<? extends RuntimeException> ERROR_WITH_VALUE_AND_OPTION_NAME = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME)
            .messageFormatter(String::format)
            .create();
}
