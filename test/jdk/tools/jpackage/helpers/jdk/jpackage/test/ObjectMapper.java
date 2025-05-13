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
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;

public final class ObjectMapper {
    ObjectMapper(Predicate<String> classFilter, Predicate<List<String>> methodFilter,
            Predicate<String> leafClassFilter, Map<Method, Function<Object, Object>> substitutes) {
        this.classFilter = Objects.requireNonNull(classFilter);
        this.methodFilter = Objects.requireNonNull(methodFilter);
        this.leafClassFilter = Objects.requireNonNull(leafClassFilter);
        this.substitutes = Objects.requireNonNull(substitutes);
    }

    public static Builder blank() {
        return new Builder().allowAllLeafClasses(false).filterLeafClasses().excludeSome(Stream.of(
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
                .filterMethods().excludeSome(OBJECT_METHODS).apply()
                .filterLeafClasses().excludeSome(Stream.of(
                        Path.class,
                        Path.of("").getClass()
                ).map(Class::getName).toList()).apply()
                .mutate(configureOptionalType());
    }

    public static Consumer<Builder> configureOptionalType() {
        return builder -> {
            // Filter all but "get()" methods of "Optional" class.
            builder.filterMethods(Optional.class).exclude("get").apply();
            // Substitute "Optional.get()" with the function that will return "null" if the value is "null".
            builder.subst(Optional.class, "get", obj -> {
                var opt = (Optional<?>)obj;
                if (opt.isPresent()) {
                    return opt.get();
                } else {
                    return null;
                }
            });
        };
    }

    public static String lookupFullMethodName(Method m) {
        return lookupFullMethodName(m.getDeclaringClass(), m.getName());
    }

    public static String lookupMethodName(Method m) {
        return lookupMethodName(m.getName());
    }

    public static String lookupFullMethodName(Class<?> c, String m) {
        return Objects.requireNonNull(c).getName() + lookupMethodName(m);
    }

    public static String lookupMethodName(String m) {
        return "#" + Objects.requireNonNull(m);
    }

    public Object map(Object obj) {
        if (obj != null) {
            return mapObject(obj).orElseGet(Map::of);
        } else {
            return null;
        }
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

        if (obj instanceof Map<?, ?> map) {
            return Optional.of(mapMap(map));
        }

        return Optional.of(getMethods(obj).map(m -> {
            final Object propertyValue;
            final var subst = substitutes.get(m);
            if (subst != null) {
                propertyValue = subst.apply(obj);
            } else {
                propertyValue = toFunction(m::invoke).apply(obj);
            }
            return mapObject(propertyValue).map(o -> {
                return Map.entry(m.getName(), o);
            }).orElse(null);
        }).filter(Objects::nonNull).collect(toMutableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private Collection<Object> mapIterable(Iterable<?> col) {
        final List<Object> list = new ArrayList<>();
        for (var obj : col) {
            mapObject(obj).ifPresent(list::add);
        }
        return list;
    }

    private Map<Object, Object> mapMap(Map<?, ?> map) {
        return map.entrySet().stream().collect(toMutableMap(e -> {
            return mapObject(e.getKey()).orElse(NULL);
        }, e -> {
            return mapObject(e.getValue()).orElse(NULL);
        }));
    }

    private boolean filter(Class<?> type) {
        return classFilter.test(type.getName());
    }

    private boolean filter(Method m) {
        return methodFilter.test(List.of(lookupMethodName(m), lookupFullMethodName(m)));
    }

    private Stream<Method> getMethods(Object obj) {
        return getMethods(obj.getClass()).filter(this::filter);
    }

    private static Stream<Method> getMethods(Class<?> type) {
        final var methods = Stream.of(type.getMethods());
        final var interfaceMethods = Stream.of(type.getInterfaces()).map(Class::getMethods).flatMap(x -> Stream.of(x));
        return Stream.concat(interfaceMethods, methods).collect(
                toMap(Method::getName, x -> x, (x, y) -> x)).values().stream().filter(ObjectMapper::defaultFilter);
    }

    private static boolean defaultFilter(Method m) {
        if (Modifier.isStatic(m.getModifiers()) || (m.getParameterCount() > 0) || void.class.equals(m.getReturnType())) {
            return false;
        }
        return true;
    }

    private static <T, K, U>
    Collector<T, ?, Map<K,U>> toMutableMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, (x , y) -> {
            throw new UnsupportedOperationException(
                    String.format("Enries with the same key and different values [%s] and [%s]", x, y));
        }, HashMap::new);
    }

    public static final class Builder {

        private Builder() {
            allowAllClasses();
            allowAllLeafClasses();
            allowAllMethods();
        }

        public ObjectMapper create() {
            return new ObjectMapper(
                    classFilter.createPredicate(),
                    methodFilter.createMultiPredicate(),
                    leafClassFilter.createPredicate(),
                    Map.copyOf(substitutes));
        }

        public class NamePredicateBuilder {

            NamePredicateBuilder(Filter sink) {
                this.sink = Objects.requireNonNull(sink);
            }

            public Builder apply() {
                sink.addAll(excludes);
                return Builder.this;
            }

            public final NamePredicateBuilder exclude(String... v) {
                return excludeSome(List.of(v));
            }

            public final NamePredicateBuilder excludeSome(Collection<String> v) {
                excludes.addAll(v);
                return this;
            }

            protected Set<String> getExcludes() {
                return excludes;
            }

            private final Filter sink;
            private final Set<String> excludes = new HashSet<>();
        }

        public final class MethodPredicateBuilder extends NamePredicateBuilder {

            MethodPredicateBuilder(Class<?> type, Filter sink) {
                super(sink);
                this.type = Objects.requireNonNull(type);
            }

            @Override
            public Builder apply() {
                var all = getExcludes().stream().map(v -> {
                    return lookupFullMethodName(type, v);
                }).collect(toSet());

                var excludes = ObjectMapper.getMethods(type).filter(m -> {
                    return !OBJECT_METHODS.contains(ObjectMapper.lookupMethodName(m));
                }).map(ObjectMapper::lookupFullMethodName).filter(Predicate.not(all::contains)).toList();

                return filterMethods().excludeSome(excludes).apply();
            }

            private final Class<?> type;
        }

        public Builder allowAllClasses(boolean v) {
            classFilter.negate(!v);
            return this;
        }

        public Builder allowAllClasses() {
            return allowAllClasses(true);
        }

        public Builder allowAllMethods(boolean v) {
            leafClassFilter.negate(!v);
            return this;
        }

        public Builder allowAllMethods() {
            return allowAllMethods(true);
        }

        public Builder allowAllLeafClasses(boolean v) {
            methodFilter.negate(!v);
            return this;
        }

        public Builder allowAllLeafClasses() {
            return allowAllLeafClasses(true);
        }

        public NamePredicateBuilder filterClasses() {
            return new NamePredicateBuilder(classFilter);
        }

        public NamePredicateBuilder filterMethods(Class<?> type) {
            return new MethodPredicateBuilder(type, methodFilter);
        }

        public NamePredicateBuilder filterMethods() {
            return new NamePredicateBuilder(methodFilter);
        }

        public NamePredicateBuilder filterLeafClasses() {
            return new NamePredicateBuilder(leafClassFilter);
        }

        public Builder subst(Method target, Function<Object, Object> substitute) {
            substitutes.put(Objects.requireNonNull(target), Objects.requireNonNull(substitute));
            return this;
        }

        public Builder subst(Class<?> targetClass, String targetMethodName, Function<Object, Object> substitute) {
            var method = toSupplier(() -> targetClass.getMethod(targetMethodName)).get();
            return subst(method, substitute);
        }

        public Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        private static final class Filter {
            Predicate<List<String>> createMultiPredicate() {
                if (items.isEmpty()) {
                    return v -> true;
                } else if (negate) {
                    return v -> {
                        return v.stream().noneMatch(items::contains);
                    };
                } else {
                    return v -> {
                        return v.stream().anyMatch(items::contains);
                    };
                }
            }

            Predicate<String> createPredicate() {
                if (items.isEmpty()) {
                    return v -> true;
                } else if (negate) {
                    return Predicate.not(items::contains);
                } else {
                    return items::contains;
                }
            }

            void addAll(Collection<String> v) {
                items.addAll(v);
            }

            void negate(boolean v) {
                negate = v;
            }

            private boolean negate;
            private final Set<String> items = new HashSet<>();
        }

        private final Filter classFilter = new Filter();
        private final Filter methodFilter = new Filter();
        private final Filter leafClassFilter = new Filter();
        private final Map<Method, Function<Object, Object>> substitutes = new HashMap<>();
    }

    private final Predicate<String> classFilter;
    private final Predicate<List<String>> methodFilter;
    private final Predicate<String> leafClassFilter;
    private final Map<Method, Function<Object, Object>> substitutes;

    private static final Set<String> OBJECT_METHODS =
            Stream.of(Object.class.getMethods()).map(ObjectMapper::lookupMethodName).collect(toSet());

    private static final Object NULL = new Object();
}
