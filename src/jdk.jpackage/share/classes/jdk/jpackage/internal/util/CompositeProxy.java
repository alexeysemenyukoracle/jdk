/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dynamic proxy dispatching method calls to multiple objects. It is aimed at
 * creating objects from mixins. The class provides functionality similar to
 * that of <code>net.sf.cglib.proxy.Mixin</code> class from the cglib library.
 *
 * Sample usage:
 * {@snippet :
 * interface Sailboat {
 *     default void trimSails() {
 *     }
 * }
 *
 * interface WithMain {
 *     void trimMain();
 * }
 *
 * interface WithJib {
 *     void trimJib();
 * }
 *
 * interface Sloop extends Sailboat, WithMain, WithJib {
 *     @Override
 *     public default void trimSails() {
 *         System.out.println("On the sloop:");
 *         trimMain();
 *         trimJib();
 *     }
 * }
 *
 * interface Catboat extends Sailboat, WithMain {
 *     @Override
 *     public default void trimSails() {
 *         System.out.println("On the catboat:");
 *         trimMain();
 *     }
 * }
 *
 * final var withMain = new WithMain() {
 *     @Override
 *     public void trimMain() {
 *         System.out.println("  trim the main");
 *     }
 * };
 *
 * final var withJib = new WithJib() {
 *     @Override
 *     public void trimJib() {
 *         System.out.println("  trim the jib");
 *     }
 * };
 *
 * Sloop sloop = CompositeProxy.create(Sloop.class, withMain, withJib);
 *
 * Catboat catboat = CompositeProxy.create(Catboat.class, withMain);
 *
 * sloop.trimSails();
 * catboat.trimSails();
 * }
 *
 * Output:
 *
 * <pre>
 * On the sloop:
 *   trim the main
 *   trim the jib
 * On the cat:
 *   trim the main
 * </pre>
 *
 * @see Proxy
 */
public final class CompositeProxy {

    /**
     * Builder of {@link CompositeProxy} instances.
     */
    public static final class Builder {

        /**
         * Returns a proxy instance for the specified interface that dispatches method
         * invocations to the specified handlers. Uses previously configured invocation
         * tunnel and conflict resolver objects with the created proxy object.
         *
         * @param <T>           the interface type
         * @param interfaceType the interface class composite proxy instance should
         *                      implement
         * @param slices        handlers for the method calls of the interface
         * @return a new instance of {@link Proxy} implementing the given interface and
         *         dispatching the interface method invocations to the given handlers
         */
        public <T> T create(Class<T> interfaceType, Object... slices) {
            return CompositeProxy.createCompositeProxy(
                    interfaceType,
                    Optional.ofNullable(methodConflictResolver).orElse(STANDARD_METHOD_CONFLICT_RESOLVER),
                    Optional.ofNullable(objectConflictResolver).orElse(STANDARD_OBJECT_CONFLICT_RESOLVER),
                    invokeTunnel,
                    slices);
        }

        /**
         * Sets the method dispatch conflict resolver for this builder. The conflict
         * resolver is used by composite proxy to select a method call handler from
         * several candidates.
         *
         * @param v the method conflict resolver for this builder or <code>null</code>
         *          if the default conflict resolver should be used
         * @return this
         */
        public Builder methodConflictResolver(MethodConflictResolver v) {
            methodConflictResolver = v;
            return this;
        }

        /**
         * Sets the object dispatch conflict resolver for this builder. The conflict
         * resolver is used by the composite proxy to select an object from several
         * candidates.
         *
         * @param v the object conflict resolver for this builder or <code>null</code>
         *          if the default conflict resolver should be used
         * @return this
         */
        public Builder objectConflictResolver(ObjectConflictResolver v) {
            objectConflictResolver = v;
            return this;
        }

        /**
         * Sets the invocation tunnel for this builder.
         *
         * @param v the invocation tunnel for this builder or <code>null</code> if no
         *          invocation tunnel should be used
         * @return this
         */
        public Builder invokeTunnel(InvokeTunnel v) {
            invokeTunnel = v;
            return this;
        }

        private Builder() {}

        private MethodConflictResolver methodConflictResolver;
        private ObjectConflictResolver objectConflictResolver;
        private InvokeTunnel invokeTunnel;
    }

    /**
     * Method conflict resolver. Used when an instance of a class has several
     * methods that are candidates to implement some method in an interface and the
     * composite proxy needs to choose one of these methods.
     */
    @FunctionalInterface
    public interface MethodConflictResolver {

        /**
         * Returns the method of {@code obj} that should be used in a composite proxy to
         * implement an abstract {@code method}.
         *
         * @param interfaceType the interface type composite proxy instance should
         *                      implement
         * @param method        an abstract method composite proxy needs to implement
         * @param obj           object
         * @param candidates    methods from the class of the {@code obj} object with
         *                      the same signature (the name, return and parameter
         *                      types) as the {@code method}; the array is unordered and
         *                      doesn't contain duplicates
         * @return either one of items from the {@code candidates} or {@code method} if
         *         the {@code method} is the default method and it should not be
         *         overridden, or {@code null} if can't choose one
         */
        Method choose(Class<?> interfaceType, Method method, Object obj, Method[] candidates);
    }

    /**
     * Object conflict resolver. Used when several objects have methods that are
     * candidates to implement some method in an interface and the composite proxy
     * needs to choose one of these objects.
     */
    @FunctionalInterface
    public interface ObjectConflictResolver {

        /**
         * Returns the object that should be used in a composite proxy to implement
         * abstract method {@code method}.
         *
         * @param interfaceType the interface type composite proxy instance should
         *                      implement
         * @param method        abstract method
         * @param candidates    objects with a method with the same signature (the name,
         *                      return and parameter types) as the signature of the
         *                      {@code method} method; the array is unordered and
         *                      doesn't contain duplicates
         * @return either one of items from the {@code candidates} or {@code null} if
         *         can't choose one
         */
        Object choose(Class<?> interfaceType, Method method, Object[] candidates);
    }

    /**
     * Invocation tunnel. Must be used when building a composite proxy from objects
     * that implement package-private interfaces to prevent
     * {@link IllegalAccessException} exceptions being thrown by {@link Proxy}
     * instances. Must be implemented by classes from packages with package-private
     * interfaces used with {@link CompositeProxy} class.
     *
     * Assumed implementation:
     * {@snippet :
     *
     * package org.foo;
     *
     * import java.lang.reflect.InvocationHandler;
     * import java.lang.reflect.Method;
     * import jdk.jpackage.internal.util.CompositeProxy;
     *
     * final class CompositeProxyTunnel implements CompositeProxy.InvokeTunnel {
     *
     *     @Override
     *     public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
     *         return method.invoke(obj, args);
     *     }
     *
     *     @Override
     *     public Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
     *         return InvocationHandler.invokeDefault(proxy, method, args);
     *     }
     *
     *     static final CompositeProxyTunnel INSTANCE = new CompositeProxyTunnel();
     * }
     */
    public interface InvokeTunnel {
        /**
         * Processes a method invocation on an object of composite proxy and returns the result.
         *
         * @implNote Implementation should call the given method on the given object
         *           with the given arguments and return the result of the call.
         * @param obj    the object on which to invoke the method
         * @param method the method to invoke
         * @param args   the arguments to use in the method call
         * @return the result of the method call
         * @throws Throwable if the method throws
         */
        Object invoke(Object obj, Method method, Object[] args) throws Throwable;

        /**
         * Processes a default interface method invocation on a composite proxy and
         * returns the result.
         *
         * @implNote Implementation should call
         *           {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *           method on the given proxy object with the given arguments and
         *           return the result of the call.
         * @param proxy  the <code>proxy</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @param method the <code>method</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @param args   the <code>args</code> parameter for
         *               {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *               call
         * @return the result of the
         *         {@link InvocationHandler#invokeDefault(Object, Method, Object...)}
         *         call
         * @throws Throwable if the {@link InvocationHandler#invokeDefault(Object, Method, Object...)} call throws
         */
        Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable;
    }

    /**
     * Creates a new proxy builder.
     * @return a new proxy builder
     */
    public static Builder build() {
        return new Builder();
    }

    /**
     * Shortcut for
     * <code>CompositeProxy.build().create(interfaceType, slices)</code>.
     *
     * @see Builder#create(Class, Object...)
     */
    public static <T> T create(Class<T> interfaceType, Object... slices) {
        return build().create(interfaceType, slices);
    }

    private CompositeProxy() {
    }

    private static <T> T createCompositeProxy(
            Class<T> interfaceType,
            MethodConflictResolver methodConflictResolver,
            ObjectConflictResolver objectConflictResolver,
            InvokeTunnel invokeTunnel,
            Object... slices) {

        Objects.requireNonNull(interfaceType);
        Objects.requireNonNull(methodConflictResolver);
        Objects.requireNonNull(objectConflictResolver);
        Stream.of(slices).forEach(Objects::requireNonNull);

        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException(String.format("Type %s must be an interface", interfaceType.getName()));
        }

        final var uniqueSlices = Stream.of(slices).map(IdentityWrapper::new).collect(toSet());

        final var unreferencedSlicesBuilder = SetBuilder.build(uniqueSlices).emptyAllowed(true);

        final Map<Method, Handler> methodDispatch = getProxyableMethods(interfaceType).map(method -> {
            return Map.entry(method, uniqueSlices.stream().map(slice -> {
                return Map.entry(slice, getImplementerMethods(slice.value()).filter(sliceMethod -> {
                    return signatureEquals(sliceMethod, method);
                }).toList());
            }).filter(e -> {
                return !e.getValue().isEmpty();
            }).toList());
        }).flatMap(e -> {
            final Method method = e.getKey();
            final List<Map.Entry<IdentityWrapper<Object>, List<Method>>> slicesWithMethods = e.getValue();

            final Map.Entry<IdentityWrapper<Object>, List<Method>> sliceWithMethods;
            switch (slicesWithMethods.size()) {
                case 0 -> {
                    if (!method.isDefault()) {
                        throw new IllegalArgumentException(String.format("None of the slices can handle %s", method));
                    } else {
                        return Optional.ofNullable(createHandlerForDefaultMethod(method, invokeTunnel)).map(handler -> {
                            return Map.entry(method, handler);
                        }).stream();
                    }
                }
                case 1 -> {
                    sliceWithMethods = slicesWithMethods.getFirst();
                }
                default -> {
                    var candidates = slicesWithMethods.stream().map(sliceEntry -> {
                        return sliceEntry.getKey().value();
                    }).toList();

                    var candidate = objectConflictResolver.choose(interfaceType, method, candidates.toArray());
                    if (candidate == null) {
                        throw new IllegalArgumentException(String.format(
                                "Ambiguous choice between %s for %s", candidates, method));
                    }

                    var candidateIdentity = IdentityWrapper.wrapIdentity(candidate);

                    if (candidates.stream().map(IdentityWrapper::new).noneMatch(Predicate.isEqual(candidateIdentity))) {
                        throw new UnsupportedOperationException();
                    }

                    sliceWithMethods = slicesWithMethods.stream().filter(v -> {
                        return candidateIdentity.equals(v.getKey());
                    }).findFirst().orElseThrow();
                }
            }

            final var slice = sliceWithMethods.getKey().value();
            final var sliceMethods = sliceWithMethods.getValue();
            final Handler handler;
            if (sliceMethods.size() == 1 && !method.isDefault()) {
                unreferencedSlicesBuilder.remove(sliceWithMethods.getKey());
                handler = createHandlerForMethod(slice, sliceMethods.getFirst(), invokeTunnel);
            } else {
                var candidate = Optional.ofNullable(methodConflictResolver.choose(
                        interfaceType,
                        method,
                        slice,
                        sliceMethods.toArray(Method[]::new)
                )).orElseGet(() -> {
                    if (sliceMethods.size() == 1) {
                        return method;
                    } else {
                        return null;
                    }
                });
                if (candidate == null) {
                    throw new IllegalArgumentException(String.format(
                            "Ambiguous choice between %s in %s", sliceMethods, slice));
                } else if (candidate == method && method.isDefault()) {
                    handler = createHandlerForDefaultMethod(candidate, invokeTunnel);
                } else if (sliceMethods.contains(candidate)) {
                    unreferencedSlicesBuilder.remove(sliceWithMethods.getKey());
                    handler = createHandlerForMethod(slice, candidate, invokeTunnel);
                } else {
                    throw new UnsupportedOperationException();
                }
            }

            return Optional.ofNullable(handler).map(h -> {
                return Map.entry(method, h);
            }).stream();

        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        var unreferencedSlices = unreferencedSlicesBuilder.create().stream().map(IdentityWrapper::value).toList();
        if (!unreferencedSlices.isEmpty()) {
            throw new IllegalArgumentException(String.format("Unreferenced slices: %s", unreferencedSlices));
        }

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
                new CompositeProxyInvocationHandler(Collections.unmodifiableMap(methodDispatch)));

        return proxy;
    }

    private static Stream<Class<?>> unfoldInterface(Class<?> interfaceType) {
        return Stream.concat(
                Stream.of(interfaceType),
                Stream.of(interfaceType.getInterfaces()
        ).flatMap(CompositeProxy::unfoldInterface));
    }

    private static List<Class<?>> getSuperclasses(Class<?> type) {
        List<Class<?>> superclasses = new ArrayList<>();

        var current = type.getSuperclass();

        while (current != null) {
            superclasses.add(current);
            current = current.getSuperclass();
        }

        return superclasses;
    }

    private static Stream<Method> getProxyableMethods(Class<?> interfaceType) {
        var methods = unfoldInterface(interfaceType).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return !Modifier.isStatic(method.getModifiers());
        });
        return removeRedundancy(methods);
    }

    private static Stream<Method> getImplementerMethods(Object slice) {
        var sliceType = slice.getClass();
        var methods = Stream.of(
                Stream.of(sliceType),
                getSuperclasses(sliceType).stream(),
                Stream.of(sliceType.getInterfaces()).flatMap(CompositeProxy::unfoldInterface)
        ).flatMap(x -> x).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return     !Modifier.isStatic(method.getModifiers())
                    && !Modifier.isPrivate(method.getModifiers())
                    && !Modifier.isAbstract(method.getModifiers());
        });
        return removeRedundancy(methods);
    }

    private static Stream<Method> removeRedundancy(Stream<Method> methods) {
        var groups = methods.distinct().collect(Collectors.groupingBy(MethodSignature::new)).values();
        return groups.stream().map(group -> {
            // All but a single method should be filtered out from the group.
            return group.stream().reduce((a, b) -> {
                var ac = a.getDeclaringClass();
                var bc = b.getDeclaringClass();
                if (ac.isAssignableFrom(bc)) {
                    return b;
                } else if (bc.isAssignableFrom(ac)) {
                    return a;
                } else if (a.isDefault()) {
                    return b;
                } else {
                    return a;
                }
            }).orElseThrow();
        });
    }

    private static boolean signatureEquals(Method a, Method b) {
        return Objects.equals(new MethodSignature(a), new MethodSignature(b));
    }

    private record CompositeProxyInvocationHandler(Map<Method, Handler> dispatch) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var handler = dispatch.get(method);
            if (handler != null) {
                return handler.invoke(proxy, args);
            } else if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            } else {
                handler = OBJECT_METHOD_DISPATCH.get(method);
                if (handler != null) {
                    return handler.invoke(proxy, args);
                } else {
                    throw new UnsupportedOperationException(String.format("No handler for %s", method));
                }
            }
        }

        private static String objectToString(Object obj) {
            return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
        }

        private static boolean objectIsSame(Object obj, Object other) {
            return obj == other;
        }

        private static Method getMethod(Class<?> type, String methodName, Class<?>...paramaterTypes) {
            try {
                return type.getDeclaredMethod(methodName, paramaterTypes);
            } catch (NoSuchMethodException|SecurityException ex) {
                throw new InternalError(ex);
            }
        }

        private record ObjectMethodHandler(Method method) implements Handler {

            ObjectMethodHandler {
                Objects.requireNonNull(method);
            }

            @Override
            public Object invoke(Object proxy, Object[] args) throws Throwable {
                if (args == null) {
                    return method.invoke(null, proxy);
                } else {
                    final var newArgs = new Object[args.length + 1];
                    newArgs[0] = proxy;
                    System.arraycopy(args, 0, newArgs, 1, args.length);
                    return method.invoke(null, newArgs);
                }
            }
        }

        private static final Map<Method, Handler> OBJECT_METHOD_DISPATCH = Map.of(
                getMethod(Object.class, "toString"),
                new ObjectMethodHandler(getMethod(CompositeProxyInvocationHandler.class, "objectToString", Object.class)),
                getMethod(Object.class, "equals", Object.class),
                new ObjectMethodHandler(getMethod(CompositeProxyInvocationHandler.class, "objectIsSame", Object.class, Object.class)),
                getMethod(Object.class, "hashCode"),
                new ObjectMethodHandler(getMethod(System.class, "identityHashCode", Object.class))
        );
    }

    private static Handler createHandlerForDefaultMethod(Method method, InvokeTunnel invokeTunnel) {
        Objects.requireNonNull(method);
        if (invokeTunnel != null) {
            return (proxy, args) -> {
                return invokeTunnel.invokeDefault(proxy, method, args);
            };
        } else {
            return null;
        }
    }

    private static Handler createHandlerForMethod(Object obj, Method method, InvokeTunnel invokeTunnel) {
        Objects.requireNonNull(obj);
        Objects.requireNonNull(method);
        if (invokeTunnel != null) {
            return (proxy, args) -> {
                return invokeTunnel.invoke(obj, method, args);
            };
        } else {
            return (proxy, args) -> {
                return method.invoke(obj, args);
            };
        }
    }

    @FunctionalInterface
    private interface Handler {

        Object invoke(Object proxy, Object[] args) throws Throwable;
    }

    private record MethodSignature(String name, Class<?> returnType, List<Class<?>> parameterTypes) {
        MethodSignature {
            Objects.requireNonNull(name);
            Objects.requireNonNull(returnType);
            parameterTypes.forEach(Objects::requireNonNull);
        }

        MethodSignature(Method m) {
            this(m.getName(), m.getReturnType(), List.of(m.getParameterTypes()));
        }
    }


    private static int comparePriorityInConflictResolution(Method a, Method b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        if (Objects.equals(a, b)) {
            return 0;
        }

        var ac = a.getDeclaringClass();
        var bc = b.getDeclaringClass();
        if (Objects.equals(ac, bc)) {
            if (a.isDefault() && !b.isDefault()) {
                return -1;
            } else if (!a.isDefault() && b.isDefault()) {
                return 1;
            }
        } else if (ac.isAssignableFrom(bc)) {
            return -1;
        } else if (bc.isAssignableFrom(ac)) {
            return 1;
        }

        throw new IllegalArgumentException();
    }

    private static final ObjectConflictResolver STANDARD_OBJECT_CONFLICT_RESOLVER = (_, _, _) -> {
        return null;
    };

    private static final MethodConflictResolver STANDARD_METHOD_CONFLICT_RESOLVER = (_, method, obj, candidates) -> {
        if (!method.isDefault()) {
            return null;
        } else if (Stream.of(candidates).noneMatch(Predicate.isEqual(method))) {
            return method;
        } else {
            Comparator<Method> c = CompositeProxy::comparePriorityInConflictResolution;
            candidates = Stream.of(candidates)
                    .filter(Predicate.isEqual(method).negate())
                    .sorted(c.reversed()).limit(2)
                    .toArray(Method[]::new);
            return switch (candidates.length) {
                case 0 -> {
                    yield method;
                }
                case 1 -> {
                    yield candidates[0];
                }
                default -> {
                    if (c.compare(candidates[0], candidates[1]) == 0) {
                        yield null;
                    } else {
                        yield candidates[0];
                    }
                }
            };
        }
    };
}
