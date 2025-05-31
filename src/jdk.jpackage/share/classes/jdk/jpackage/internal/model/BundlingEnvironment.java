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
package jdk.jpackage.internal.model;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Bundling environment. Defines available bundling operations.
 */
public interface BundlingEnvironment {

    /**
     * Returns descriptors of the supported bundling operations.
     * <p>
     * The first element in the list should denote the default bundling operation.
     * 
     * @return the supported bundling operations or an empty list if non are
     *         supported
     */
    List<BundlingOperationDescriptor> supportedOperations();

    /**
     * Returns configuration errors or an empty list if there are no such errors for
     * the target bundling operation.
     * 
     * @param op the descriptor of the target bundling operation
     * @return the list of configuration errors or an empty list if there are no
     *         such errors for the specified bundling operation
     * @throws NoSuchElementException if the specified descriptor is not one of the
     *                                items in the list returned by
     *                                {@link #supportedOperations()} method
     */
    default List<Exception> configurationErrors(BundlingOperationDescriptor op) {
        return List.of();
    }
}
