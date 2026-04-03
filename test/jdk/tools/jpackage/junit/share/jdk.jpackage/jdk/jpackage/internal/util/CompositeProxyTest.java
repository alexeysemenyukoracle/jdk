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

import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;
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
        var convo = CompositeProxy.create(Smalltalk.class);
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
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

    @Test
    void testAutoMethodConflictResolver() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.build().create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver2() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            String getString();
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.build().create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver3() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                return "AB";
            }
        }

        var foo = new Object() {
            public String getString() {
                throw new AssertionError();
            }
        };

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.build().create(AB.class, foo);
        });

        assertEquals(String.format("Unreferenced slices: %s", List.of(foo)), ex.getMessage());
    }

    @Test
    void testAutoMethodConflictResolver4() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                return "AB";
            }
        }

        var proxy = CompositeProxy.build().create(AB.class);
        assertEquals("AB", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver5() {

        interface A {
            default String getString() {
                throw new AssertionError();
            }
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            String getString();
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.build().create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver6() {

        interface A {
            default String getString() {
                return "A";
            }
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                return A.super.getString() + "!";
            }
        }

        var proxy = CompositeProxy.build().create(AB.class);
        assertEquals("A!", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver7() {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
            default String getString() {
                return B.super.getString() + "!";
            }
        }

        var proxy = CompositeProxy.build().create(AB.class);
        assertEquals("B!", proxy.getString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver8(boolean override) {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
        }

        if (override) {
            var foo = new Object() {
                public String getString() {
                    return "foo";
                }
            };

            var proxy = CompositeProxy.build().methodConflictResolver((_, method, _, _) -> {
                try {
                    return foo.getClass().getMethod("getString");
                } catch (Exception ex) {
                    throw toUnchecked(ex);
                }
            }).create(AB.class, foo);
            assertEquals("foo", proxy.getString());
        } else {
            var proxy = CompositeProxy.build().create(AB.class);
            assertEquals("B", proxy.getString());
        }
    }

    @Test
    void testAutoMethodConflictResolver9() {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        interface AB extends A, B {
            String getString();
        }

        var ab = CompositeProxy.build().create(AB.class, foo);
        assertEquals("foo", ab.getString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver10(boolean override) {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
            String getString();
        }

        if (override) {
            var foo = new B() {
                @Override
                public String getString() {
                    return B.super.getString() + "!";
                }
            };

            var proxy = CompositeProxy.build().create(AB.class, foo);
            assertEquals("B!", proxy.getString());
        } else {
            var proxy = CompositeProxy.build().create(AB.class, new B() {});
            assertEquals("B", proxy.getString());
        }
    }

    @Test
    void testAutoMethodConflictResolver11() {

        interface A {
            String getString();
        }

        class Foo implements A {
            @Override
            public String getString() {
                throw new AssertionError();
            }
        }

        class Bar extends Foo {
            @Override
            public String getString() {
                throw new AssertionError();
            }
        }

        class Buz extends Bar {
            @Override
            public String getString() {
                return "buz";
            }
        }

        var proxy = CompositeProxy.create(A.class, new Buz());
        assertEquals("buz", proxy.getString());
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "foo,bar",
        "bar,foo",
    })
    void testObjectConflictResolver(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) {

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

        FooBar proxy = CompositeProxy.build().objectConflictResolver((_, method, _) -> {
            if (method.getDeclaringClass().equals(Bar.class)) {
                return bar;
            } else {
                return foo;
            }
        }).create(FooBar.class, slices);

        assertTrue(Set.of("foo", "bar").contains(proxy.getString()));
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "no-foo",
        "private-foo",
        "static-foo",
        "static-foo,private-foo,no-foo",
    })
    void testMissingImplementer(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) throws NoSuchMethodException, SecurityException {

        interface A {
            void foo();
        }

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "no-foo" -> new Object();
                case "private-foo" -> new Object() {
                    void foo() {
                    }
                };
                case "static-foo" -> new Object() {
                    public static void foo() {
                    }
                };
                default -> { throw new AssertionError(); }
            };
        }).toList();

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(A.class, slices.toArray());
        });

        assertEquals(String.format("None of the slices can handle %s", A.class.getMethod("foo")), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUnusedSlice(boolean all) {

        interface A {
            default void foo() {
                throw new AssertionError();
            }
        }

        A a = new A() {};
        var obj = new Object();

        if (all) {
            var messages = Set.of(
                    String.format("Unreferenced slices: %s", List.of(a, obj)),
                    String.format("Unreferenced slices: %s", List.of(obj, a))
            );

            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(A.class, a, obj);
            });

            assertTrue(messages.contains(ex.getMessage()));
        } else {
            interface B extends A {
                void foo();
            }

            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(B.class, a, obj);
            });

            assertEquals(String.format("Unreferenced slices: %s", List.of(obj)), ex.getMessage());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "'a,b,a',false",
        "'a,b,a',true",
        "'a,b',true",
        "'b,a',true",
        "'a,b',false",
        "'b,a',false",
    })
    void testAmbigousImplementers(
            @ConvertWith(StringArrayConverter.class) String[] slicesSpec,
            boolean withObjectConflictResolver) throws NoSuchMethodException, SecurityException {

        interface A {
            String foo();
            String bar();
        }

        var a = new Object() {
            public String foo() {
                return "a-foo";
            }
            public String bar() {
                throw new AssertionError();
            }
        };

        var b = new Object() {
            public String bar() {
                return "b-bar";
            }
        };

        var ambiguousMethod = A.class.getMethod("bar");

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "a" -> a;
                case "b" -> b;
                default -> { throw new AssertionError(); }
            };
        }).toArray();

        if (withObjectConflictResolver) {
            var proxy = CompositeProxy.build().objectConflictResolver((_, _, _) -> {
                return b;
            }).create(A.class, slices);

            assertEquals("a-foo", proxy.foo());
            assertEquals("b-bar", proxy.bar());
        } else {
            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(A.class, slices);
            });

            var messages = Set.of(
                    String.format("Ambiguous choice between %s for %s", List.of(a, b), ambiguousMethod),
                    String.format("Ambiguous choice between %s for %s", List.of(b, a), ambiguousMethod)
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

        Sloop sloop = CompositeProxy.create(Sloop.class, withMain, withJib);

        Catboat catboat = CompositeProxy.create(Catboat.class, withMain);

        sloop.trimSails();
        catboat.trimSails();
    }
}
