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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
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
 * Sloop sloop = CompositeProxy.create(Sloop.class, new Sailboat() {
 * }, withMain, withJib);
 *
 * Catboat catboat = CompositeProxy.create(Catboat.class, new Sailboat() {
 * }, withMain);
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
         * Returns the method that should be used in a composite proxy to implement
         * abstract method {@code obj}.
         * <p>
         * The return value must be either {@code a} or {@code b} or {@code null}.
         *
         * @param obj object
         * @param a   a method from the class of the {@code obj} object
         * @param b   a method from the class of the {@code obj} object
         * @return either {@code a} or {@code b} or {@code null} if can't detect which
         *         of {@code a} or {@code b} methods should be used
         */
        Method choose(Object obj, Method a, Method b);
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
         * <p>
         * The return value must be either {@code a} or {@code b} or {@code null}.
         *
         * @param method abstract method
         * @param a      an object with a method with the same signature (the name,
         *               return and parameter types) as the signature of the
         *               {@code method} method
         * @param b      an object with a method with the same signature (the name,
         *               return and parameter types) as the signature of the
         *               {@code method} method
         * @return either {@code a} or {@code b} or {@code null} if can't detect which
         *         of {@code a} or {@code b} objects should be used
         */
        Object choose(Method method, Object a, Object b);
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

        final var proxyableMethods = getProxyableMethods(interfaceType).toList();

        final var proxyableMethodSignatures = proxyableMethods.stream().map(MethodSignature::new).toList();

        final Map<Method, List<Object>> sliceGroupDispatch = Stream.of(slices)
                .map(IdentityWrapper::new)
                .distinct()
                .map(IdentityWrapper::value)
                .flatMap(slice -> {
                    return getImplementerMethods(slice).filter(method -> {
                        return proxyableMethodSignatures.contains(new MethodSignature(method));
                    }).map(method -> {
                        return Map.entry(method, slice);
                    });
                }).collect(groupingBy(Map.Entry::getKey, mapping(Map.Entry::getValue, toList())));

        final Map<Method, Object> sliceDispatch = sliceGroupDispatch.entrySet().stream().map(e -> {
            var method = e.getKey();
            var candidates = e.getValue();

            Object slice;
            if (candidates.size() == 1) {
                slice = candidates.getFirst();
            } else {
                slice = candidates.stream()
                        .reduce(new ObjectConflictResolverAdapter(method, objectConflictResolver))
                        .orElseThrow();
            }

            return Map.entry(method, slice);

        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        final var unreferencedSlices = SetBuilder.build(Stream.of(slices).map(IdentityWrapper::new).toArray(IdentityWrapper[]::new))
                .remove(sliceDispatch.values().stream().map(IdentityWrapper::new).toArray(IdentityWrapper[]::new))
                .emptyAllowed(true).create().stream().map(IdentityWrapper::value).toList();

        if (!unreferencedSlices.isEmpty()) {
            throw new IllegalArgumentException(String.format("Unreferenced slices: %s", unreferencedSlices));
        }

        final Map<Method, Handler> methodDispatch = sliceDispatch.entrySet().stream().map(e -> {
            var method = e.getKey();
            var slice = e.getValue();
            return Map.entry(method, createHandler(method, slice, methodConflictResolver, invokeTunnel));
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        final Map<Method, Handler> defaultMethodDispatch = SetBuilder.build(proxyableMethods)
                .remove(methodDispatch.keySet())
                .emptyAllowed(true).create().stream().map(method -> {
                    if (!method.isDefault()) {
                        throw new IllegalArgumentException(String.format("None of the slices can handle %s", method));
                    } else {
                        return Map.entry(method, createHandlerForDefaultMethod(method, invokeTunnel));
                    }
                }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        final Map<Method, Handler> dispatch;
        if (defaultMethodDispatch.isEmpty()) {
            dispatch = methodDispatch;
        } else {
            dispatch = new HashMap<>(methodDispatch);
            dispatch.putAll(defaultMethodDispatch);
        }

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
                new CompositeProxyInvocationHandler(Collections.unmodifiableMap(dispatch)));

        return proxy;
    }

    private static Handler createHandler(
            Method method,
            Object slice,
            MethodConflictResolver conflictResolver,
            InvokeTunnel invokeTunnel) {

        return getImplementerMethods(slice).filter(m -> {
            return signatureEquals(m, method);
        }).reduce(new MethodConflictResolverAdapter(slice, conflictResolver)).map(m -> {
            if (isInvokeDefault(m, slice)) {
                return createHandlerForDefaultMethod(m, invokeTunnel);
            } else {
                return createHandlerForMethod(slice, m, invokeTunnel);
            }
        }).orElseThrow();
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
        return unfoldInterface(interfaceType).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return !Modifier.isStatic(method.getModifiers());
        }).distinct();
    }

    private static Stream<Method> getImplementerMethods(Object slice) {
        var sliceType = slice.getClass();
        return Stream.of(
                Stream.of(sliceType),
                getSuperclasses(sliceType).stream(),
                Stream.of(sliceType.getInterfaces()).flatMap(CompositeProxy::unfoldInterface)
        ).flatMap(x -> x).flatMap(type -> {
            return Stream.of(type.getMethods());
        }).filter(method -> {
            return !Modifier.isStatic(method.getModifiers()) && !Modifier.isPrivate(method.getModifiers());
        }).distinct();
    }

    private static boolean isInvokeDefault(Method method, Object slice) {
        Objects.requireNonNull(method);
        Objects.requireNonNull(slice);

        if (!method.isDefault()) {
            return false;
        }

        // The "method" is default.
        // See if it is overridden by any non-abstract method in the "slice".
        // If it is, InvocationHandler.invokeDefault() should not be used to call it.

        final var sliceClass = slice.getClass();

        final var methodOverriden = Stream.of(sliceClass.getMethods())
                .filter(Predicate.not(Predicate.isEqual(method)))
                .filter(sliceMethod -> { 
                    return !Modifier.isAbstract(sliceMethod.getModifiers()); 
                })
                .anyMatch(sliceMethod -> { 
                    return signatureEquals(sliceMethod, method); 
                });

        return !methodOverriden;
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

        static class ObjectMethodHandler extends HandlerOfMethod {

            ObjectMethodHandler(Method method) {
                super(method);
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

    private static HandlerOfMethod createHandlerForDefaultMethod(Method method, InvokeTunnel invokeTunnel) {
        if (invokeTunnel != null) {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return invokeTunnel.invokeDefault(proxy, this.method, args);
                }
            };
        } else {
            return null;
        }
    }

    private static HandlerOfMethod createHandlerForMethod(Object obj, Method method, InvokeTunnel invokeTunnel) {
        if (invokeTunnel != null) {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return invokeTunnel.invoke(obj, this.method, args);
                }
            };
        } else {
            return new HandlerOfMethod(method) {
                @Override
                public Object invoke(Object proxy, Object[] args) throws Throwable {
                    return this.method.invoke(obj, args);
                }
            };
        }
    }

    @FunctionalInterface
    private interface Handler {

        Object invoke(Object proxy, Object[] args) throws Throwable;
    }

    private abstract static class HandlerOfMethod implements Handler {
        HandlerOfMethod(Method method) {
            this.method = method;
        }

        protected final Method method;
    }

    private record ObjectConflictResolverAdapter(Method method, ObjectConflictResolver conflictResolver) 
            implements BinaryOperator<Object> {

        ObjectConflictResolverAdapter {
            Objects.requireNonNull(method);
            Objects.requireNonNull(conflictResolver);
        }

        @Override
        public Object apply(Object a, Object b) {
            var r = conflictResolver.choose(method, Objects.requireNonNull(a), Objects.requireNonNull(b));
            if (r == a) {
                return a;
            } else if (r == b) {
                return b;
            } else if (r == null) {
                throw new IllegalArgumentException(String.format(
                        "Ambiguous choice between %s and %s for %s", a, b, method));
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private record MethodConflictResolverAdapter(Object obj, MethodConflictResolver conflictResolver) 
            implements BinaryOperator<Method> {

        MethodConflictResolverAdapter {
            Objects.requireNonNull(obj);
            Objects.requireNonNull(conflictResolver);
        }

        @Override
        public Method apply(Method a, Method b) {
            var r = conflictResolver.choose(obj, Objects.requireNonNull(a), Objects.requireNonNull(b));
            if (r == a) {
                return a;
            } else if (r == b) {
                return b;
            } else if (r == null) {
                throw new IllegalArgumentException(String.format(
                        "Ambiguous choice between %s and %s in %s", a, b, obj));
            } else {
                throw new UnsupportedOperationException();
            }
        }
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

    private static final ObjectConflictResolver STANDARD_OBJECT_CONFLICT_RESOLVER = (_, _, _) -> {
        return null;
    };

    private static final MethodConflictResolver STANDARD_METHOD_CONFLICT_RESOLVER = (_, _, _) -> {
        return null;
    };
}
