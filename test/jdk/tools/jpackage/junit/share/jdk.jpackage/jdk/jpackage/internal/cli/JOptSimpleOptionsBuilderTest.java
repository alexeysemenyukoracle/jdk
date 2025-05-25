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
import static jdk.jpackage.internal.cli.TestUtils.arrayElements;
import jdk.jpackage.internal.util.ExceptionAnalizer;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
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
                final Object actualValue;
                if (parserMode.equals(ParserMode.PARSE)) {
                    actualValue = cmdline.find(optionValue.asOption().orElseThrow()).orElseThrow();
                } else {
                    actualValue = optionValue.getFrom(cmdline);
                }
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

            Builder optionValue(OptionValue<?> option, Object expectedValue) {
                options.put(option, expectedValue);
                return this;
            }

            Builder args(String...v) {
                return args(List.of(v));
            }

            Builder args(Collection<String> v) {
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

        final TestSpec.Builder builder = build().args("--dir", nonExistentDir.toString());
        if (asArray) {
            builder.optionValue(directoryOption("dir").toArray().create(), new Path[] { nonExistentDir });
        } else {
            builder.optionValue(directoryOption("dir").create(), nonExistentDir);
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
        }).mergePolicy(MergePolicy.USE_FIRST);

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
    @EnumSource(names = {"CONVERT", "VALIDATE"})
    public void testConverterError(ParserMode parserMode) {

        final var scalarException = new RuntimeException("Scalar error");
        final var arrayException = new RuntimeException("Array error");

        final Function<RuntimeException, Consumer<OptionSpecBuilder<String>>> mutatorCreator = ex -> {
            return builder -> {
                switch (parserMode) {
                    case CONVERT -> {
                        builder.converter(_ -> {
                            throw ex;
                        });
                    }
                    case VALIDATE -> {
                        builder.validator(new Predicate<String>() {
                            @Override
                            public boolean test(String v) {
                                throw ex;
                            }
                        });
                    }
                    default -> {
                        throw new IllegalArgumentException();
                    }
                }
            };
        };

        final var scalarOption = stringOption("val").mutate(mutatorCreator.apply(scalarException));

        final var arrayOption = stringOption("arr").mutate(mutatorCreator.apply(arrayException)).toArray();

        final var cfg = new FaultyParserArgsConfig()
                .options(scalarOption, stringOption("good"))
                .arrayOptions(arrayOption);

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--val=10")
                .expectAnyInternalException(scalarException)
                .test(parserMode);

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--arr=foo")
                .expectAnyInternalException(arrayException)
                .test(parserMode);

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--arr=bar", "--val=57")
                .expectAnyInternalException(scalarException, arrayException)
                .test(parserMode);

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--val=57", "--arr=bar")
                .expectAnyInternalException(scalarException, arrayException)
                .test(parserMode);
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyScalar(MergePolicy mergePolicy, ParserMode parserMode) {

        if (mergePolicy == MergePolicy.CONCATENATE) {
            assertThrowsExactly(IllegalArgumentException.class,
                    option("foo", Object.class).mergePolicy(mergePolicy)::create);
            return;
        }

        final var strOption = stringOption("str").mergePolicy(mergePolicy).create();

        final var monthOption = stringOption("month").shortName("m")
                .mergePolicy(mergePolicy).create();

        final var intOption = option("int", Integer.class).shortName("i")
                .converter(Integer::valueOf).mergePolicy(mergePolicy).create();

        final var floatOption = option("d", Double.class)
                .converter(Double::valueOf).mergePolicy(mergePolicy).create();

        final var builder = build()
                .args("-d", "1000", "--month=June", "--int=10", "-m", "July", "-i", "45", "-m", "August")
                .args("--str=", "--str=A");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionValue(strOption, mergeStringValues(List.of("", "A"), mergePolicy));
            builder.optionValue(monthOption, mergeStringValues(List.of("June", "July", "August"), mergePolicy));
            builder.optionValue(intOption, mergeStringValues(List.of("10", "45"), mergePolicy));
            builder.optionValue(floatOption, "1000");
        } else {
            builder.optionValue(strOption, mergeScalarValues(List.of("", "A"), mergePolicy));
            builder.optionValue(monthOption, mergeScalarValues(List.of("June", "July", "August"), mergePolicy));
            builder.optionValue(intOption, mergeScalarValues(List.of(10, 45), mergePolicy));
            builder.optionValue(floatOption, Double.valueOf(1000));
        }

        builder.create().test(parserMode);
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyArray(MergePolicy mergePolicy, ParserMode parserMode) {

        final var monthOption = stringOption("month").shortName("m")
                .mergePolicy(mergePolicy)
                .toArray(Pattern.quote("+"))
                .create();

        final var intOption = option("int", Integer.class).shortName("i")
                .converter(Integer::valueOf)
                .mergePolicy(mergePolicy)
                .toArray(TestUtils.splitOrEmpty(":"))
                .create();

        final var floatOption = option("d", Double.class)
                .converter(Double::valueOf)
                .mergePolicy(mergePolicy)
                .toArray(Pattern.quote("|"))
                .create();

        final var builder = build().args("-d", "1000", "--int=", "--month=June", "--int=10:333", "-m", "July+July", "-i", "45", "-m", "August", "--int=");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionValue(monthOption,
                    mergeStringValues(List.of("June", "July+July", "August"), mergePolicy));
            builder.optionValue(intOption,
                    mergeStringValues(List.of("", "10:333", "45", ""), mergePolicy));
            builder.optionValue(floatOption, "1000");
        } else {
            builder.optionValue(monthOption,
                    mergeArrayValues(arrayElements(String.class, List.of("June", "July", "July", "August")), mergePolicy));
            builder.optionValue(intOption,
                    mergeArrayValues(arrayElements(Integer.class, Arrays.asList(null, 10, 333, 45, null)), mergePolicy));
            builder.optionValue(floatOption, new Double[] {Double.valueOf(1000)});
        }

        builder.create().test(parserMode);
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyNoValue(MergePolicy mergePolicy, ParserMode parserMode) {

        if (mergePolicy == MergePolicy.CONCATENATE) {
            assertThrowsExactly(IllegalArgumentException.class, booleanOption("foo").mergePolicy(mergePolicy)::create);
            return;
        }

        final var aOption = booleanOption("a").mergePolicy(mergePolicy).create();
        final var bOption = booleanOption("boo").shortName("b").mergePolicy(mergePolicy).create();

        final var builder = build().args("-a", "-boo", "-b");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionValue(aOption, mergeScalarValues(List.of("true"), mergePolicy));
            builder.optionValue(bOption, mergeScalarValues(List.of("true", "true"), mergePolicy));
        } else {
            builder.optionValue(aOption, mergeScalarValues(List.of(true), mergePolicy));
            builder.optionValue(bOption, mergeScalarValues(List.of(true, true), mergePolicy));
        }

        builder.create().test(parserMode);
    }

    private static <T> T mergeScalarValues(List<T> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.getFirst();
            }
            case USE_LAST -> {
                return values.getLast();
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static <T> T[] mergeArrayValues(List<T[]> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.getFirst();
            }
            case USE_LAST -> {
                return values.getLast();
            }
            case CONCATENATE -> {
                return values.stream().map(Stream::of).flatMap(x -> x).toList().toArray(values.getFirst());
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static String mergeStringValues(List<String> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.getFirst();
            }
            case USE_LAST -> {
                return values.getLast();
            }
            case CONCATENATE -> {
                return values.stream().collect(Collectors.joining("\0"));
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static List<Object[]> testMergePolicy() {
        final List<Object[]> data = new ArrayList<>();
        for (var mergePolicy : MergePolicy.values()) {
            for (var parserMode : ParserMode.values()) {
                data.add(new Object[] { mergePolicy, parserMode });
            }
        }
        return data;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testNoUnrecognizedOptionMapping(boolean onlyUnrecognizedOption) {

        final var option = option("x", Object.class).converter(_ -> {
            throw new RuntimeException();
        }).create();

        final List<String> args = new ArrayList<>();

        if (!onlyUnrecognizedOption) {
            args.addAll(List.of("-x", "foo"));
        }
        args.add("--unrecognized");

        final var result = new JOptSimpleOptionsBuilder().optionValues(option).create().apply(args.toArray(String[]::new));

        assertFalse(result.hasValue());
        assertEquals(1, result.errors().size());

        assertTrue(new ExceptionAnalizer().isInstanceOf(jdk.internal.joptsimple.OptionException.class).analize(result.errors().iterator().next()));
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
                build().optionValue(
                        directoryOption("input").shortName("i").create(),
                        pwd
                ).args("--input", "", "-i", pwd.toString()),

                build().optionValue(
                        directoryOption("dir").toArray(pathSeparator()).create(),
                        new Path[] { pwd, Path.of(".") }
                ).args("--dir=" + pwd.toString() + pathSeparator() + "."),

                build().optionValue(
                        stringOption("arguments").toArray("\\s+").create(toList()),
                        List.of("", "a", "b", "c", "", "de")
                ).args("--arguments", " a b  c", "--arguments", " de"),

                build().optionValue(
                        stringOption("arguments").toArray(";+").create(toList()),
                        List.of("a b", "c", "de")
                ).args("--arguments", "a b;;c", "--arguments", "de;"),

                build().optionValue(stringOption("foo").create(), "--foo").args("--foo", "--foo"),

                build().args("--foo")
                        .optionValue(booleanOption("foo").create(), true)
                        .optionValue(booleanOption("bar").create(), false),

                build().args("-x", "").optionValue(stringOption("x").create(), ""),
                build().args("-x", "").optionValue(stringOption("x").toArray().create(), new String[] {""}),
                build().args("-x", "", "-x", "").optionValue(stringOption("x").toArray().create(), new String[] {"", ""}),

                // Test merging order.
                build().optionValue(
                        stringOption("x").shortName("y").toArray().create(toList()),
                        List.of("10", "RR", "P", "Z")
                ).args("-x", "10", "-y", "RR", "-x", "P", "-y", "Z"),

                // Test converters are not executed on discarded invalid values (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).create(),
                        100
                ).args("-x", "a", "-x", "100"),
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(",").mergePolicy(MergePolicy.USE_FIRST).create(),
                        new Integer[] {34}
                ).args("-x", "34,A", "-x", "f"),

                // Test the last array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(",").mergePolicy(MergePolicy.USE_LAST).create(toList()),
                        List.of(78)
                ).args("-x", "1,3,78"),
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(",").mergePolicy(MergePolicy.USE_LAST).create(toList()),
                        List.of(35)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(TestUtils.splitOrEmpty(",")).mergePolicy(MergePolicy.USE_LAST).create(toList()),
                        List.of()
                ).args("-x", "1,3,78", "-x", ""),

                // Test the first array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(",").mergePolicy(MergePolicy.USE_FIRST).create(toList()),
                        List.of(1)
                ).args("-x", "1,3,78"),
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(",").mergePolicy(MergePolicy.USE_FIRST).create(toList()),
                        List.of(1)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).toArray(TestUtils.splitOrEmpty(",")).mergePolicy(MergePolicy.USE_FIRST).create(toList()),
                        List.of()
                ).args("-x", "", "-x", "1,3,78")

        ).map(TestSpec.Builder::create).toList();
    }

    private static Collection<TestSpec> testStringVector() {
        final var args = List.of("--foo", "1 22 333", "--foo", "44 44");
        return Stream.of(
                build().optionValue(
                        stringOption("foo").toArray().create(),
                        new String[] { "1 22 333", "44 44" }
                ).args(args),

                build().optionValue(
                        stringOption("foo").toArray().create(toList()),
                        List.of("1 22 333", "44 44")
                ).args(args),

                build().optionValue(
                        stringOption("foo").toArray("\\s+").create(),
                        new String[] { "1", "22", "333", "44", "44" }
                ).args(args),

                build().optionValue(
                        stringOption("foo").toArray("\\s+").create(toList()),
                        List.of("1", "22", "333", "44", "44")
                ).args(args)
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

            final Supplier<Result<?>> createCmdline;
            switch (parserMode) {
                case CONVERT -> {
                    createCmdline = parser.apply(args.toArray(String[]::new)).orElseThrow()::convertedOptions;
                }
                case VALIDATE -> {
                    createCmdline = parser.apply(args.toArray(String[]::new))
                            .orElseThrow().convertedOptions().orElseThrow()::validatedOptions;
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }

            if (!expectedInternalExceptions.isEmpty()) {
                final Class<? extends Exception> exceptionWrapperType;
                switch (parserMode) {
                    case CONVERT -> {
                        exceptionWrapperType = OptionValueConverter.ConverterException.class;
                    }
                    case VALIDATE -> {
                        exceptionWrapperType = Validator.ValidatorException.class;
                    }
                    default -> {
                        throw new UnsupportedOperationException();
                    }
                }

                final var ex = assertThrowsExactly(exceptionWrapperType, () -> createCmdline.get());
                assertTrue(expectedInternalExceptions.stream().anyMatch(v -> {
                    return v == ex.getCause();
                }));
            } else {
                assertFalse(createCmdline.get().hasValue());
            }

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

        FaultyParserArgsConfig clearArgs() {
            args.clear();
            return this;
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

        FaultyParserArgsConfig expectAnyInternalException(Collection<Exception> v) {
            expectedInternalExceptions.addAll(v);
            return this;
        }

        FaultyParserArgsConfig expectAnyInternalException(Exception... v) {
            return expectAnyInternalException(List.of(v));
        }

        FaultyParserArgsConfig cleareExpectedInternalExceptions() {
            expectedInternalExceptions.clear();
            return this;
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
        private List<Exception> expectedInternalExceptions = new ArrayList<>();
    }


    private final static String FORMAT_STRING_ILLEGAL_PATH = "The value '%s' provided for parameter %s is not a valid path";

    private final static String FORMAT_STRING_NOT_DIRECTORY = "The value '%s' provided for parameter %s is not a directory path";

    private static final OptionValueExceptionFactory<? extends RuntimeException> ERROR_WITH_VALUE_AND_OPTION_NAME = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME)
            .messageFormatter(String::format)
            .create();
}
