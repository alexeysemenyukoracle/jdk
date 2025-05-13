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
package jdk.jpackage.test;

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class ObjectMapper {
    ObjectMapper(Predicate<String> classFilter, Predicate<List<String>> methodFilter, Predicate<String> leafClassFilter) {
        this.classFilter = Objects.requireNonNull(classFilter);
        this.methodFilter = Objects.requireNonNull(methodFilter);
        this.leafClassFilter = Objects.requireNonNull(leafClassFilter);
    }

    public static Builder blank() {
        return new Builder().filterLeafClasses().allowAll(false).excludeSome(Stream.of(
                Object.class,
                String.class,
                boolean.class, Boolean.class,
                byte.class, Byte.class,
                char.class, Character.class,
                short.class, Short.class,
                int.class, Integer.class,
                long.class, Long.class,
                float.class, Float.class,
                double.class, Double.class,
                void.class, Void.class
        ).map(Class::getName).toList()).apply();
    }

    public static Builder defaults() {
        return blank()
                .filterMethods().allowAll().excludeSome(Stream.of(
                        Object.class.getMethods()
                ).map(ObjectMapper::lookupMethodName).toList()).apply()
                .filterLeafClasses().allowAll(false).excludeSome(Stream.of(
                        Path.class,
                        Path.of("").getClass()
                ).map(Class::getName).toList()).apply();
    }

    public Object map(Object obj) {
        return mapObject(obj).orElseGet(Map::of);
    }

    public Optional<Object> mapObject(Object obj) {
        if (obj == null) {
            return Optional.empty();
        }

        if (leafClassFilter.test(obj.getClass().getName())) {
            return Optional.of(obj);
        }

        if (!filter(obj.getClass())) {
            return Optional.empty();
        }

        if (obj instanceof Iterable<?> col) {
            return Optional.of(mapIterable(col));
        }

        return Optional.of(getMethods(obj).map(m -> {
            return mapObject(toFunction(m::invoke).apply(obj)).map(o -> {
                return Map.entry(m.getName(), o);
            }).orElse(null);
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private Collection<Object> mapIterable(Iterable<?> col) {
        final List<Object> list = new ArrayList<>();
        for (var obj : col) {
            mapObject(obj).ifPresent(list::add);
        }
        return list;
    }

    boolean filter(Class<?> type) {
        return classFilter.test(type.getName());
    }

    boolean filter(Method m) {
        if (Modifier.isStatic(m.getModifiers()) || (m.getParameterCount() > 0) || void.class.equals(m.getReturnType())) {
            return false;
        }
        return methodFilter.test(List.of(lookupMethodName(m), lookupFullMethodName(m)));
    }

    private Stream<Method> getMethods(Object obj) {
        final var methods = Stream.of(obj.getClass().getMethods()).filter(this::filter);
        final var interfaceMethods = Stream.of(obj.getClass().getInterfaces()).map(Class::getMethods).flatMap(x -> Stream.of(x)).filter(this::filter);
        return Stream.concat(interfaceMethods, methods).collect(toMap(Method::getName, x -> x, (x, y) -> x)).values().stream();
    }

    private static String lookupFullMethodName(Method m) {
        return lookupFullMethodName(m.getDeclaringClass(), m.getName());
    }

    private static String lookupMethodName(Method m) {
        return lookupMethodName(m.getName());
    }

    private static String lookupFullMethodName(Class<?> c, String m) {
        return Objects.requireNonNull(c).getName() + lookupMethodName(m);
    }

    private static String lookupMethodName(String m) {
        return "#" + Objects.requireNonNull(m);
    }

    public static final class Builder {

        public ObjectMapper create() {
            return new ObjectMapper(classFilter.createPredicate(), methodFilter.createMultiPredicate(), leafClassFilter.createPredicate());
        }

        public final class NamePredicateBuilder {

            NamePredicateBuilder(Filter sink) {
                this.sink = Objects.requireNonNull(sink);
            }

            public Builder apply() {
                if (allowAll) {
                    sink.excludeAll(excludes);
                } else {
                    sink.includeAll(excludes);
                }
                return Builder.this;
            }

            public NamePredicateBuilder allowAll(boolean v) {
                allowAll = v;
                return this;
            }

            public NamePredicateBuilder allowAll() {
                return allowAll(true);
            }

            public NamePredicateBuilder exclude(String... v) {
                return excludeSome(List.of(v));
            }

            public NamePredicateBuilder excludeSome(Collection<String> v) {
                excludes.addAll(v);
                return this;
            }

            private final Filter sink;
            private final Set<String> excludes = new HashSet<>();
            boolean allowAll;
        }

        public NamePredicateBuilder filterClasses() {
            return new NamePredicateBuilder(classFilter);
        }

        public NamePredicateBuilder filterMethods() {
            return new NamePredicateBuilder(methodFilter);
        }

        public NamePredicateBuilder filterLeafClasses() {
            return new NamePredicateBuilder(leafClassFilter);
        }

        private final static class Filter {
            Predicate<List<String>> createMultiPredicate() {
                if (excludes.isEmpty() && includes.isEmpty()) {
                    return v -> true;
                } else if (excludes.isEmpty()) {
                    return v -> {
                        return v.stream().anyMatch(includes::contains);
                    };
                } else {
                    return v -> {
                        return v.stream().noneMatch(excludes::contains);
                    };
                }
            }

            Predicate<String> createPredicate() {
                if (excludes.isEmpty() && includes.isEmpty()) {
                    return v -> true;
                } else if (excludes.isEmpty()) {
                    return includes::contains;
                } else {
                    return Predicate.not(excludes::contains);
                }
            }

            void includeAll(Collection<String> v) {
                includes.addAll(v);
                excludes.removeAll(v);
            }

            void excludeAll(Collection<String> v) {
                excludes.addAll(v);
                includes.removeAll(v);
            }

            private final Set<String> includes = new HashSet<>();
            private final Set<String> excludes = new HashSet<>();
        }

        private final Filter classFilter = new Filter();
        private final Filter methodFilter = new Filter();
        private final Filter leafClassFilter = new Filter();
    }

    private final Predicate<String> classFilter;
    private final Predicate<List<String>> methodFilter;
    private final Predicate<String> leafClassFilter;
}
