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
package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.JUnitUtils.StringArrayConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import jdk.jpackage.internal.util.CompositeProxy.InterfaceConflictResolver;


class CompositeProxyTest {

    static interface Smalltalk {

        default String sayHello() {
            return "Hello";
        }

        default String sayBye() {
            return "Bye";
        }
    }

    static interface ConvoMixin {

        String sayThings();

        record Stub(String sayThings) implements ConvoMixin {
        }
    }

    static interface Convo extends Smalltalk, ConvoMixin {
    }

    static interface ConvoMixinWithOverrideSayBye {

        String sayThings();

        String sayBye();

        record Stub(String sayThings, String sayBye) implements ConvoMixinWithOverrideSayBye {
        }
    }

    static interface ConvoWithOverrideSayBye extends Smalltalk, ConvoMixinWithOverrideSayBye {
        @Override
        String sayBye();
    }

    static interface ConvoWithDefaultSayHelloWithOverrideSayBye extends Smalltalk, ConvoMixinWithOverrideSayBye {
        @Override
        String sayBye();

        @Override
        default String sayHello() {
            return "Ciao";
        }

        static String saySomething() {
            return "blah";
        }
    }

    @Test
    void testSmalltalk() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(Smalltalk.class);
        });

        assertEquals(
                String.format("Type %s is not extending interfaces", Smalltalk.class.getName()),
                ex.getMessage());
    }

    @Test
    void testConvo() {
        final var otherThings = "How is your day?";
        var convo = CompositeProxy.create(Convo.class,
                new Smalltalk() {}, new ConvoMixin.Stub(otherThings));
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
        assertEquals(otherThings, convo.sayThings());
    }

    @Test
    void testConvoWithDuke() {
        final var otherThings = "How is your day?";
        var convo = CompositeProxy.create(Convo.class, new Smalltalk() {
            @Override
            public String sayHello() {
                return "Hello, Duke";
            }
        }, new ConvoMixin.Stub(otherThings));
        assertEquals("Hello, Duke", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
        assertEquals(otherThings, convo.sayThings());
    }

    @Test
    void testConvoWithCustomSayBye() {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var convo = CompositeProxy.create(ConvoWithOverrideSayBye.class, new Smalltalk() {}, mixin);

        var expectedConvo = new ConvoWithOverrideSayBye() {
            @Override
            public String sayBye() {
                return mixin.sayBye;
            }

            @Override
            public String sayThings() {
                return mixin.sayThings;
            }
        };

        assertEquals(expectedConvo.sayHello(), convo.sayHello());
        assertEquals(expectedConvo.sayBye(), convo.sayBye());
        assertEquals(expectedConvo.sayThings(), convo.sayThings());
    }

    @Test
    void testConvoWithCustomSayHelloAndSayBye() {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var convo = CompositeProxy.create(ConvoWithDefaultSayHelloWithOverrideSayBye.class, new Smalltalk() {}, mixin);

        var expectedConvo = new ConvoWithDefaultSayHelloWithOverrideSayBye() {
            @Override
            public String sayBye() {
                return mixin.sayBye;
            }

            @Override
            public String sayThings() {
                return mixin.sayThings;
            }
        };

        assertEquals("Ciao", expectedConvo.sayHello());
        assertEquals(expectedConvo.sayHello(), convo.sayHello());
        assertEquals(expectedConvo.sayBye(), convo.sayBye());
        assertEquals(expectedConvo.sayThings(), convo.sayThings());
    }

    @Test
    void testInherited() {
        interface Base {
            String doSome();
        }

        interface Next extends Base {
            String doNext();
        }

        interface Last extends Next {
        }

        var last = CompositeProxy.create(Last.class, new Next() {
            @Override
            public String doNext() {
                return "next";
            }

            @Override
            public String doSome() {
                return "some";
            }
        });

        assertEquals("next", last.doNext());
        assertEquals("some", last.doSome());
    }

    @Test
    void testNestedProxy() {
        interface AddM {
            String m();
        }

        interface AddN {
            String n();
        }

        interface A extends AddM {
        }

        interface B extends AddN  {
        }

        interface C extends A, B {
        }

        var proxyA = CompositeProxy.create(A.class, new AddM() {
            @Override
            public String m() {
                return "hello";
            }
        });
        var proxyB = CompositeProxy.create(B.class, new AddN() {
            @Override
            public String n() {
                return "bye";
            }

        });
        var proxyC = CompositeProxy.create(C.class, proxyA, proxyB);

        assertEquals("hello", proxyC.m());
        assertEquals("bye", proxyC.n());
    }

    @Test
    void testComposite() {
        interface A {
            String sayHello();
            String sayBye();
            default String talk() {
                return String.join(",", sayHello(), sayBye());
            }
        }

        interface B extends A {
            @Override
            default String sayHello() {
                return "ciao";
            }
        }

        var proxy = CompositeProxy.create(B.class, new A() {
            @Override
            public String sayHello() {
                return "hello";
            }

            @Override
            public String sayBye() {
                return "bye";
            }
        });

        assertEquals("ciao,bye", proxy.talk());
    }

    @Test
    void testBasicObjectMethods() {
        interface Foo {
        }

        var proxy = CompositeProxy.create(Foo.class);
        var proxy2 = CompositeProxy.create(Foo.class);

        assertNotEquals(proxy.toString(), proxy2.toString());
        assertNotEquals(proxy.hashCode(), proxy2.hashCode());
        assertFalse(proxy.equals(proxy2));
        assertFalse(proxy2.equals(proxy));
        assertTrue(proxy.equals(proxy));
        assertTrue(proxy2.equals(proxy2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSimpleConflictResolver(boolean withOverride) {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
        }
        interface ABWithOverride extends A, B {
            String getString();
        }

        class Foo implements A {
            public String getString() {
                return "foo";
            }
        }

        class Bar implements B {
            public String getString() {
                return "bar";
            }
        }

        var foo = new Foo();
        var bar = new Bar();

        if (withOverride) {
            var proxy = CompositeProxy.build().create(ABWithOverride.class, foo, bar);
            assertEquals("bar", proxy.getString());
        } else {
            var proxy = CompositeProxy.build().create(AB.class, foo, bar);
            assertEquals("bar", proxy.getString());
        }
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "foo,bar",
        "bar,foo",
    })
    void testSimpleInterfaceConflictResolver(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) {

        interface Foo {
            String getString();
        }

        interface Bar {
            String getString();
        }

        interface FooBar extends Foo, Bar {}

        record FooBarImpl(String getString) implements Foo, Bar {}

        record BarImpl(String getString) implements Bar {}

        var foo = new FooBarImpl("foo");
        var bar = new BarImpl("bar");

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "foo" -> foo;
                case "bar" -> bar;
                default -> { throw new AssertionError(); }
            };
        }).toArray();

        FooBar proxy = CompositeProxy.build().interfaceConflictResolver(new InterfaceConflictResolver() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T chooseImplementer(Class<T> iface, T a, T b) {
                if (iface.equals(Bar.class)) {
                    return (T)bar;
                } else {
                    return (T)foo;
                }
            }
        }).create(FooBar.class, slices);

        assertTrue(Set.of("foo", "bar").contains(proxy.getString()));
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "obj",
        "obj,obj",
        "foo",
    })
    void testNotExtendingInterfaces(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) {

        @FunctionalInterface
        interface Foo {
            String getString();
        }

        Foo foo = () -> { throw new AssertionError(); };

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "foo" -> foo;
                case "obj" -> new Object();
                default -> { throw new AssertionError(); }
            };
        }).toArray();

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(Foo.class, slices);
        });

        assertEquals(
                String.format("Type %s is not extending interfaces", Foo.class.getName()),
                ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "obj",
        "a,obj",
    })
    void testMissingImplementer(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) {

        @FunctionalInterface
        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {}

        A a = () -> { throw new AssertionError(); };

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "a" -> a;
                case "obj" -> new Object();
                default -> { throw new AssertionError(); }
            };
        }).toList();

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(AB.class, slices.toArray());
        });

        Set<String> messages;
        if (slices.contains(a)) {
            messages = Set.of(String.format("None of the slices implement %s", List.of(B.class))
            );
        } else {
            messages = Set.of(
                    String.format("None of the slices implement %s", List.of(B.class, A.class)),
                    String.format("None of the slices implement %s", List.of(A.class, B.class))
            );
        }

        assertTrue(messages.contains(ex.getMessage()));
    }

    @Test
    void testUnusedSlice() {

        interface A {
            default String getString() {
                throw new AssertionError();
            }
        }

        interface B extends A {}

        A a = new A() {};
        var obj = new Object();
        var obj2 = new Object();

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(B.class, a, obj, obj2);
        });

        var messages = Set.of(
                String.format("Unreferenced slices: %s", List.of(obj, obj2)),
                String.format("Unreferenced slices: %s", List.of(obj2, obj))
        );

        assertTrue(messages.contains(ex.getMessage()));
    }

    @ParameterizedTest
    @CsvSource({
        "'ab,ab',false",
        "'ab,ab2',false",
        "'ab,ab2',true",
        "'ab2,ab',false",
        "'ab2,ab',true",
    })
    void testAmbigousImplementers(@ConvertWith(StringArrayConverter.class) String[] slicesSpec, boolean withInterfaceConflictResolver) {

        @FunctionalInterface
        interface A {
            String getString();
        }

        @FunctionalInterface
        interface B {
            String getString();
        }

        @FunctionalInterface
        interface AB extends A, B {}

        AB ab = () -> "ab";
        AB ab2 = () -> "ab2";

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "ab" -> ab;
                case "ab2" -> ab2;
                default -> { throw new AssertionError(); }
            };
        }).toArray();

        if (Stream.of(slices).collect(Collectors.toSet()).size() == 1) {
            var proxy = CompositeProxy.create(AB.class, slices);

            assertEquals("ab", proxy.getString());
        } else if (withInterfaceConflictResolver) {
            var proxy  =CompositeProxy.build().interfaceConflictResolver(new InterfaceConflictResolver() {
                @SuppressWarnings("unchecked")
                @Override
                public <T> T chooseImplementer(Class<T> iface, T a, T b) {
                    if (iface.equals(A.class)) {
                        return (T)ab;
                    } else {
                        return (T)ab2;
                    }
                }
            }).create(AB.class, slices);

            assertTrue(Set.of("ab2", "ab").contains(proxy.getString()));
        } else {
            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(AB.class, slices);
            });

            var messages = Set.of(
                    String.format("Ambiguous choice between %s and %s for implementing %s", ab, ab2, A.class),
                    String.format("Ambiguous choice between %s and %s for implementing %s", ab2, ab, A.class),
                    String.format("Ambiguous choice between %s and %s for implementing %s", ab, ab2, B.class),
                    String.format("Ambiguous choice between %s and %s for implementing %s", ab2, ab, B.class)
            );

            assertTrue(messages.contains(ex.getMessage()));
        }
    }

    @Test
    void testNotInterface() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(Integer.class);
        });

        assertEquals(String.format("Type %s must be an interface", Integer.class.getName()), ex.getMessage());
    }

    @Test
    void testJavadocExample() {
        interface Sailboat {
            default void trimSails() {}
        }

        interface WithMain {
            void trimMain();
        }

        interface WithJib {
            void trimJib();
        }

        interface Sloop extends Sailboat, WithMain, WithJib {
            @Override
            public default void trimSails() {
                System.out.println("On the sloop:");
                trimMain();
                trimJib();
            }
        }

        interface Catboat extends Sailboat, WithMain {
            @Override
            public default void trimSails() {
                System.out.println("On the catboat:");
                trimMain();
            }
        }

        final var withMain = new WithMain() {
            @Override
            public void trimMain() {
                System.out.println("  trim the main");
            }
        };

        final var withJib = new WithJib() {
            @Override
            public void trimJib() {
                System.out.println("  trim the jib");
            }
        };

        Sloop sloop = CompositeProxy.create(Sloop.class, new Sailboat() {}, withMain, withJib);

        Catboat catboat = CompositeProxy.create(Catboat.class, new Sailboat() {}, withMain);

        sloop.trimSails();
        catboat.trimSails();
    }
}
