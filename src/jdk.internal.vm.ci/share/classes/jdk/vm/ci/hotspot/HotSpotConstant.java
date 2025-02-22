/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.meta.Constant;

/**
 * A value in a space managed by Hotspot (e.g. heap or metaspace).
 * Some of these values can be referenced with a compressed pointer
 * instead of a full word-sized pointer.
 */
public interface HotSpotConstant extends Constant {

    /**
     * Determines if this constant is compressed.
     */
    boolean isCompressed();

    /**
     * Determines if this constant is compressible.
     */
    boolean isCompressible();

    /**
     * Gets a compressed version of this uncompressed constant.
     *
     * @throws IllegalArgumentException if this constant is not compressible
     */
    Constant compress();

    /**
     * Gets an uncompressed version of this compressed constant.
     *
     * @throws IllegalArgumentException if this is an uncompressed constant
     *         or this constant is not compressible
     */
    Constant uncompress();
}
