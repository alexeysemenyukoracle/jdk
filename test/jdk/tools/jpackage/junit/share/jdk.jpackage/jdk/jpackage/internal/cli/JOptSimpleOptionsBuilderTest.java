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
import static jdk.jpackage.test.JUnitUtils.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import jdk.jpackage.internal.cli.OptionValueConverter.ConverterException;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.cli.TestUtils.OptionFailure;
import jdk.jpackage.internal.cli.TestUtils.TestException;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.test.JUnitUtils.ExceptionAnalizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class JOptSimpleOptionsBuilderTest {

    enum ParserMode {
        PARSE,
        CONVERT
    }

    public record TestSpec(Map<OptionValue<?>, ExpectedValue<?>> options, List<String> args) {
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
                    actualValue = cmdline.find(optionValue.getOption()).orElseThrow();
                } else {
                    actualValue = optionValue.getFrom(cmdline);
                }
                expectedValue.assertIt(actualValue);
            }
        }


        static final class Builder {

            TestSpec create() {
                return new TestSpec(options, args);
            }

            Builder optionStringValue(OptionValue<?> option, String expectedValue) {
                options.put(option, new ExpectedValue<>(expectedValue, Assertions::assertEquals));
                return this;
             }

            <T> Builder optionValue(OptionValue<T> option, T expectedValue) {
               return optionValue(option, expectedValue, defaultAsserter());
            }

            <T> Builder optionValue(OptionValue<T> option, T expectedValue, BiConsumer<T, T> asserter) {
                options.put(option, new ExpectedValue<>(expectedValue, asserter));
                return this;
            }

            Builder args(String...v) {
                return args(List.of(v));
            }

            Builder args(Collection<String> v) {
                args.addAll(v);
                return this;
            }

            private static <T> BiConsumer<T, T> defaultAsserter() {
                return (expected, actual) -> {
                    if (expected.getClass().isArray()) {
                        assertArrayEquals(expected, actual);
                    } else {
                        assertEquals(expected, actual);
                    }
                };
            }

            private final Map<OptionValue<?>, ExpectedValue<?>> options = new HashMap<>();
            private final List<String> args = new ArrayList<>();
        }


        private record ExpectedValue<T>(T value, BiConsumer<T, T> asserter) {
            ExpectedValue {
                Objects.requireNonNull(value);
                Objects.requireNonNull(asserter);
            }

            @SuppressWarnings("unchecked")
            void assertIt(Object actual) {
                asserter.accept(value, (T)actual);
            }
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

        private static final OptionName LONG_NAME = OptionName.of("input");
        private static final OptionName SHORT_NAME = OptionName.of("i");

        private static final String FOO = "foo";
        private static final String BAR = "bar";
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
        spec.test(ParserMode.CONVERT);
    }

    @ParameterizedTest
    @MethodSource
    public void testStringVector(TestSpec spec) {
        spec.test(ParserMode.CONVERT);
    }

    @Test
    public void testConversionErrors(@TempDir Path tmpDir) {

        final var dirOption = pathOption("dir").shortName("r").toArray(",");

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
                .test();
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
                .test();
    }

    @Test
    public void testConverterError() {

        final var scalarException = new RuntimeException("Scalar error");
        final var arrayException = new RuntimeException("Array error");

        final Function<RuntimeException, Consumer<OptionSpecBuilder<String>>> mutatorCreator = ex -> {
            return builder -> {
                builder.converter(_ -> {
                    throw ex;
                });
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
                .test();

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--arr=foo")
                .expectAnyInternalException(arrayException)
                .test();

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--arr=bar", "--val=57")
                .expectAnyInternalException(scalarException, arrayException)
                .test();

        cfg.clearArgs().cleareExpectedInternalExceptions()
                .args("--val=57", "--arr=bar")
                .expectAnyInternalException(scalarException, arrayException)
                .test();
    }

    @Test
    public void testArrayUnrecoverableMergeFailure() {

        new FaultyParserArgsConfig()
                .arrayOptions(pathOption("path").toArray(",").mergePolicy(MergePolicy.USE_FIRST))
                .args("--path=*,foo", "--path=bar")
                .expectError("path", StringToken.of("*,foo", "*"))
                .expectedErrorsExactMatch(false).test();

        new FaultyParserArgsConfig()
                .arrayOptions(pathOption("path").toArray(",").mergePolicy(MergePolicy.USE_LAST))
                .args("--path=bar", "--path=foo,*")
                .expectError("path", StringToken.of("foo,*", "*"))
                .expectedErrorsExactMatch(false).test();
    }

    @Test
    public void testScalarUnrecoverableMergeFailure() {

        new FaultyParserArgsConfig()
                .options(pathOption("path").mergePolicy(MergePolicy.USE_FIRST))
                .args("--path=*", "--path=bar")
                .expectError("path", StringToken.of("*", "*"))
                .test();

        new FaultyParserArgsConfig()
                .options(pathOption("path").mergePolicy(MergePolicy.USE_LAST))
                .args("--path=bar", "--path=*")
                .expectError("path", StringToken.of("*", "*"))
                .test();
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
            builder.optionStringValue(intOption, mergeStringValues(List.of("10", "45"), mergePolicy));
            builder.optionStringValue(floatOption, "1000");
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
            builder.optionStringValue(monthOption,
                    mergeStringValues(List.of("June", "July+July", "August"), mergePolicy));
            builder.optionStringValue(intOption,
                    mergeStringValues(List.of("", "10:333", "45", ""), mergePolicy));
            builder.optionStringValue(floatOption, "1000");
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
            builder.optionStringValue(aOption, mergeScalarValues(List.of("true"), mergePolicy));
            builder.optionStringValue(bOption, mergeScalarValues(List.of("true", "true"), mergePolicy));
        } else {
            builder.optionValue(aOption, mergeScalarValues(List.of(true), mergePolicy));
            builder.optionValue(bOption, mergeScalarValues(List.of(true, true), mergePolicy));
        }

        builder.create().test(parserMode);
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

    @Test
    public void test_optionSpecMapper() {
        var mappedOption = stringOption("foo").create();
        var unmappedOption = stringOption("bar").create();
        var unusedOption = stringOption("baz").create();

        final var parse = new JOptSimpleOptionsBuilder().optionSpecMapper(optionSpec -> {
            if (optionSpec.name().equals(mappedOption.getSpec().name())) {
                return optionSpec.copyWithConverter(OptionValueConverter.<String>build()
                        .exceptionFactory(UNREACHABLE_EXCEPTION_FACTORY)
                        .formatString("")
                        .converter(ValueConverter.create(str -> {
                            return str.toUpperCase();
                        }, String.class)).create());
            } else {
                return optionSpec;
            }
        }).optionValues(mappedOption, unmappedOption, unusedOption).create();

        var optionValues = parse.apply(new String[] {"--foo=Value", "--bar", "Value"})
                .orElseThrow().convertedOptions().orElseThrow().create();

        assertEquals("VALUE", mappedOption.getFrom(optionValues));
        assertEquals("Value", unmappedOption.getFrom(optionValues));
        assertFalse(unusedOption.containsIn(optionValues));
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
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .exceptionFormatString("")
                                .converter(Integer::valueOf).toArray(",").create(),
                        new Integer[] {34}
                ).args("-x", "34,A", "-x", "f"),

                // Test the last array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).toArray(",").create(toList()),
                        List.of(78)
                ).args("-x", "1,3,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .exceptionFormatString("")
                                .converter(Integer::valueOf).toArray(",").create(toList()),
                        List.of(78)
                ).args("-x", "1,ZZZ,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).toArray(",").create(toList()),
                        List.of(35)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).toArray(TestUtils.splitOrEmpty(",")).create(toList()),
                        List.of()
                ).args("-x", "1,3,78", "-x", ""),

                // Test the first array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .exceptionFormatString("")
                                .converter(Integer::valueOf).toArray(",").create(toList()),
                        List.of(1)
                ).args("-x", "1,ZZZ,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .converter(Integer::valueOf).toArray(",").create(toList()),
                        List.of(1)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .converter(Integer::valueOf).toArray(TestUtils.splitOrEmpty(",")).create(toList()),
                        List.of()
                ).args("-x", "", "-x", "1,23,78"),

                // Test array value is a scalar for parser.
                build().optionValue(
                        option("arr", int[].class).converter(str -> {
                            return Stream.of(str.split(",")).map(Integer::valueOf).mapToInt(Integer::intValue).toArray();
                        }).create(),
                        new int[] {1, 45, 67}
                ).args("--arr=1,45,67"),

                // Test that parser can handle multi-dimensional arrays.
                build().optionValue(
                        option("arr", int[].class).converter(str -> {
                            if (str.isEmpty()) {
                                return new int[0];
                            } else {
                                return Stream.of(str.split(",")).map(Integer::valueOf).mapToInt(Integer::intValue).toArray();
                            }
                        }).toArray(":").create(toList()),
                        List.of(new int[] {1, 45, 67}, new int[0], new int[] {3}, new int[] {56}, new int[] {77, 82}),
                        (expected, actual) -> {
                            assertEquals(expected.size(), actual.size());
                            for (int i = 0; i != expected.size(); i++) {
                                assertArrayEquals(expected.get(i), actual.get(i));
                            }
                        }
                ).args("--arr=1,45,67::3:56", "--arr=77,82")

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
                .scope(new BundlingOperationOptionScope() {
                    @Override
                    public BundlingOperationDescriptor descriptor() {
                        throw new AssertionError();
                    }})
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

    private static Function<List<String>, Options> createParser(ParserMode mode, OptionValue<?>... options) {
        return createParser(mode, List.of(options));
    }

    private static Function<List<String>, Options> createParser(ParserMode mode, Iterable<OptionValue<?>> options) {
        Objects.requireNonNull(mode);
        final var parse = new JOptSimpleOptionsBuilder().options(StreamSupport.stream(options.spliterator(), false)
                .map(OptionValue::getOption).toList()).create();
        return args -> {
            final var builder = parse.apply(args.toArray(String[]::new)).orElseThrow();
            switch (mode) {
                case PARSE -> {
                    return builder.create();
                }
                case CONVERT -> {
                    return builder.convertedOptions().orElseThrow().create();
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

        void test() {

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

            final Supplier<Result<?>> createCmdline = parser.apply(args.toArray(String[]::new)).orElseThrow()::convertedOptions;

            if (expectedInternalExceptions.isEmpty()) {
                assertFalse(createCmdline.get().hasValue());
            } else {
                final var ex = assertThrowsExactly(ConverterException.class, () -> createCmdline.get());
                assertTrue(expectedInternalExceptions.stream().anyMatch(v -> {
                    return v == ex.getCause();
                }));
            }

            final var actualErrorWithoutExceptions = actualErrors.stream().map(OptionFailure::withoutException).toList();
            if (expectedErrorsExactMatch) {
                TestUtils.assertOptionFailuresEquals(expectedErrors, actualErrorWithoutExceptions);
            } else {
                TestUtils.assertOptionFailuresContains(expectedErrors, actualErrorWithoutExceptions);
            }
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

        FaultyParserArgsConfig expectedErrorsExactMatch(boolean v) {
            expectedErrorsExactMatch = v;
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
        private boolean expectedErrorsExactMatch = true;
    }


    private static final String FORMAT_STRING_ILLEGAL_PATH = "The value '%s' provided for parameter %s is not a valid path";

    private static final String FORMAT_STRING_NOT_DIRECTORY = "The value '%s' provided for parameter %s is not a directory path";

    private static final OptionValueExceptionFactory<? extends RuntimeException> ERROR_WITH_VALUE_AND_OPTION_NAME = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME)
            .messageFormatter(String::format)
            .create();
}
