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

import java.util.List;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;

/**
 * Bundler.
 */
public interface Bundler2 {

    /**
     * Returns the descriptor of a bundling operation that this bundler can perform.
     * 
     * @return the descriptor of a bundling operation that this bundler can perform
     */
    BundlingOperationDescriptor operation();

    /**
     * Returns configuration errors or an empty list if there are no errors.
     * <p>
     * If the return value is not an empty list, subsequent {@link #createBundle}
     * calls will fail.
     * 
     * @return the list of configuration errors or an empty list if there are no
     *         errors
     */
    default List<Exception> configurationErrors() {
        return List.of();
    }

    /**
     * Requests to create a bundle with the given parameters.
     * 
     * @param optionValues the parameters for the output bundle
     */
    void createBundle(Options optionValues);
}
