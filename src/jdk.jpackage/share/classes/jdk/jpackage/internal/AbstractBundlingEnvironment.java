/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.CliBundlingEnvironment;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;

abstract class AbstractBundlingEnvironment implements CliBundlingEnvironment {

    protected AbstractBundlingEnvironment(Collection<Bundler2> bundlers,
            Function<Iterable<Bundler2>, Optional<Bundler2>> defaultBundlerFinder) {
        this.defaultBundlerFinder = Objects.requireNonNull(defaultBundlerFinder);
        this.bundlers = Objects.requireNonNull(bundlers);
    }
    
    protected AbstractBundlingEnvironment(Collection<Bundler2> bundlers,
            BundlingOperationDescriptor defaultBundlingOperation) {
        this(bundlers, items -> findBundler(items, defaultBundlingOperation));
    }

    protected AbstractBundlingEnvironment(Function<Iterable<Bundler2>, Optional<Bundler2>> defaultBundlerFinder) {
        this(loadBundlers(), defaultBundlerFinder);
    }

    protected AbstractBundlingEnvironment(BundlingOperationDescriptor defaultBundlingOperation) {
        this(loadBundlers(), defaultBundlingOperation);
    }

    @Override
    public List<BundlingOperationDescriptor> supportedOperations() {
        return defaultBundlerFinder.apply(bundlers).map(defaultBundler -> {
            return Stream.concat(Stream.of(defaultBundler), bundlers.stream().filter(v -> {
                return v != defaultBundler;
            })).map(Bundler2::operation).toList();
        }).orElseGet(List::of);
    }

    @Override
    public List<Exception> configurationErrors(BundlingOperationDescriptor op) {
        return getBundler(op).configurationErrors();
    }

    @Override
    public void createBundle(BundlingOperationDescriptor op, Options cmdline) {
        Objects.requireNonNull(cmdline);
        final var bundler = getBundler(op);

        bundler.createBundle(cmdline);
    }

    private Bundler2 getBundler(BundlingOperationDescriptor op) {
        return findBundler(bundlers, op).orElseThrow(NoSuchElementException::new);
    }

    private static Optional<Bundler2> findBundler(Iterable<Bundler2> bundlers, BundlingOperationDescriptor op) {
        Objects.requireNonNull(op);
        return StreamSupport.stream(bundlers.spliterator(), false).filter(b -> {
            return b.operation().equals(op);
        }).findFirst();
    }

    private static Collection<Bundler2> loadBundlers() {
        return ServiceLoader.load(Bundler2.class, Bundler2.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    private final Collection<Bundler2> bundlers;
    private final Function<Iterable<Bundler2>, Optional<Bundler2>> defaultBundlerFinder;
}
