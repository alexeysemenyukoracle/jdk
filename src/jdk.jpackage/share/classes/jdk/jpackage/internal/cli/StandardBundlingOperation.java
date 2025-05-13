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
package jdk.jpackage.internal.cli;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.BundlingOperation;

/**
 * Standard jpackage operations.
 */
enum StandardBundlingOperation implements BundlingOperation {
    CREATE_WIN_APP_IMAGE("^(?!(linux-|mac-|win-exe-|win-msi-))"),
    CREATE_LINUX_APP_IMAGE("^(?!(win-|mac-|linux-rpm-|linux-deb-))"),
    CREATE_MAC_APP_IMAGE("^(?!(linux-|win-|mac-dmg-|mac-pkg-))"),
    CREATE_WIN_EXE("^(?!(linux-|mac-|win-msi-))"),
    CREATE_WIN_MSI("^(?!(linux-|mac-|win-exe-))"),
    CREATE_LINUX_RPM("^(?!(win-|mac-|linux-deb-))"),
    CREATE_LINUX_DEB("^(?!(win-|mac-|linux-rpm-))"),
    CREATE_MAC_PKG("^(?!(linux-|win-|mac-dmg-))"),
    CREATE_MAC_DMG("^(?!(linux-|win-|mac-pkg-))"),
    SIGN_MAC_APP_IMAGE;

    StandardBundlingOperation(String optionNameRegexp) {
        optionNamePredicate = Pattern.compile(optionNameRegexp).asPredicate();
    }

    StandardBundlingOperation() {
        optionNamePredicate = v -> false;
    }

    static Builder scope() {
        return new Builder();
    }

    static final class Builder {
        Builder forOptionName(String v) {
            return add(StandardBundlingOperation.fromOptionName(v));
        }

        Builder add(Collection<BundlingOperation> v) {
            v.forEach(Objects::requireNonNull);
            actions.addAll(v);
            return this;
        }

        Builder add(BundlingOperation... v) {
            return add(List.of(v));
        }

        Builder remove(Collection<BundlingOperation> v) {
            v.forEach(Objects::requireNonNull);
            actions.removeAll(v);
            return this;
        }

        Builder remove(BundlingOperation... v) {
            return remove(List.of(v));
        }

        Builder clear() {
            actions.clear();
            return this;
        }

        Set<BundlingOperation> create() {
            if (actions.isEmpty()) {
                throw new UnsupportedOperationException();
            }
            return Set.copyOf(actions);
        }

        private final Set<BundlingOperation> actions = new HashSet<>();
    }

    static Set<BundlingOperation> fromOptionName(String optionName) {
        Objects.requireNonNull(optionName);
        return Stream.of(StandardBundlingOperation.values()).filter(v -> {
            return v.optionNamePredicate.test(optionName);
        }).collect(Collectors.toSet());
    }

    static final Set<BundlingOperation> WINDOWS = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_WIN_MSI, CREATE_WIN_EXE);

    static final Set<BundlingOperation> LINUX = Set.of(
            CREATE_LINUX_APP_IMAGE, CREATE_LINUX_RPM, CREATE_LINUX_DEB);

    static final Set<BundlingOperation> MACOS = Set.of(
            CREATE_MAC_APP_IMAGE, CREATE_MAC_DMG, CREATE_MAC_PKG, SIGN_MAC_APP_IMAGE);

    static final Set<BundlingOperation> MAC_SIGNING = MACOS;

    static final Set<BundlingOperation> CREATE_APP_IMAGE = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_LINUX_APP_IMAGE, CREATE_MAC_APP_IMAGE);

    static final Set<BundlingOperation> CREATE_NATIVE = Set.of(
            CREATE_WIN_MSI, CREATE_WIN_EXE,
            CREATE_LINUX_RPM, CREATE_LINUX_DEB,
            CREATE_MAC_DMG, CREATE_MAC_PKG);

    static final Set<BundlingOperation> CREATE_BUNDLE = Stream.of(
            CREATE_APP_IMAGE,
            CREATE_NATIVE
    ).flatMap(Set::stream).collect(Collectors.toSet());

    private final Predicate<String> optionNamePredicate;
}
