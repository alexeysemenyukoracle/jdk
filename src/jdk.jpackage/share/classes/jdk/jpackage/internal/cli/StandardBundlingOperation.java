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

import static jdk.jpackage.internal.model.AppImagePackageType.APP_IMAGE;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.SetBuilder;


/**
 * Standard jpackage operations.
 */
enum StandardBundlingOperation implements BundlingOperationOptionScope {
    CREATE_WIN_APP_IMAGE(APP_IMAGE, "^(?!(linux-|mac-|win-exe-|win-msi-))", OperatingSystem.WINDOWS),
    CREATE_LINUX_APP_IMAGE(APP_IMAGE, "^(?!(win-|mac-|linux-rpm-|linux-deb-))", OperatingSystem.LINUX),
    CREATE_MAC_APP_IMAGE(APP_IMAGE, "^(?!(linux-|win-|mac-dmg-|mac-pkg-))", OperatingSystem.MACOS),
    CREATE_WIN_EXE(StandardPackageType.WIN_EXE, "^(?!(linux-|mac-|win-msi-))", OperatingSystem.WINDOWS),
    CREATE_WIN_MSI(StandardPackageType.WIN_MSI, "^(?!(linux-|mac-|win-exe-))", OperatingSystem.WINDOWS),
    CREATE_LINUX_RPM(StandardPackageType.LINUX_RPM, "^(?!(win-|mac-|linux-deb-))", OperatingSystem.LINUX),
    CREATE_LINUX_DEB(StandardPackageType.LINUX_DEB, "^(?!(win-|mac-|linux-rpm-))", OperatingSystem.LINUX),
    CREATE_MAC_PKG(StandardPackageType.MAC_PKG, "^(?!(linux-|win-|mac-dmg-))", OperatingSystem.MACOS),
    CREATE_MAC_DMG(StandardPackageType.MAC_DMG, "^(?!(linux-|win-|mac-pkg-))", OperatingSystem.MACOS),
    SIGN_MAC_APP_IMAGE(APP_IMAGE, OperatingSystem.MACOS);

    StandardBundlingOperation(PackageType packageType, String optionNameRegexp, OperatingSystem os) {
        this.packageType = Objects.requireNonNull(packageType);
        optionNamePredicate = Pattern.compile(optionNameRegexp).asPredicate();
        this.os = Objects.requireNonNull(os);
    }

    StandardBundlingOperation(PackageType packageType, OperatingSystem os) {
        this.packageType = Objects.requireNonNull(packageType);
        optionNamePredicate = v -> false;
        this.os = Objects.requireNonNull(os);
    }

    OperatingSystem os() {
        return os;
    }

    String packageTypeValue() {
        if (packageType.equals(APP_IMAGE)) {
            return "app-image";
        } else {
            return ((StandardPackageType)packageType).suffix().substring(1);
        }
    }

    PackageType packageType() {
        return packageType;
    }

    static Stream<StandardBundlingOperation> ofPlatform(OperatingSystem os) {
        Objects.requireNonNull(os);
        return Stream.of(values()).filter(v -> {
            return v.os().equals(os);
        });
    }

    static Builder scope() {
        return new Builder();
    }

    static final class Builder {
        Builder forOptionName(String v) {
            return add(fromOptionName(v));
        }

        Builder add(Collection<BundlingOperationOptionScope> v) {
            impl.add(v);
            return this;
        }

        Builder add(BundlingOperationOptionScope... v) {
            return add(List.of(v));
        }

        Builder remove(Collection<BundlingOperationOptionScope> v) {
            impl.remove(v);
            return this;
        }

        Builder remove(BundlingOperationOptionScope... v) {
            return remove(List.of(v));
        }

        Builder clear() {
            impl.clear();
            return this;
        }

        Set<BundlingOperationOptionScope> create() {
            return impl.create();
        }

        private final SetBuilder<BundlingOperationOptionScope> impl = new SetBuilder<>();
    }

    static Set<BundlingOperationOptionScope> fromOptionName(String optionName) {
        Objects.requireNonNull(optionName);
        return Stream.of(StandardBundlingOperation.values()).filter(v -> {
            return v.optionNamePredicate.test(optionName);
        }).collect(Collectors.toSet());
    }

    static final Set<BundlingOperationOptionScope> WINDOWS = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_WIN_MSI, CREATE_WIN_EXE);

    static final Set<BundlingOperationOptionScope> LINUX = Set.of(
            CREATE_LINUX_APP_IMAGE, CREATE_LINUX_RPM, CREATE_LINUX_DEB);

    static final Set<BundlingOperationOptionScope> MACOS = Set.of(
            CREATE_MAC_APP_IMAGE, CREATE_MAC_DMG, CREATE_MAC_PKG, SIGN_MAC_APP_IMAGE);

    static final Set<BundlingOperationOptionScope> MAC_SIGNING = MACOS;

    static final Set<BundlingOperationOptionScope> CREATE_APP_IMAGE = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_LINUX_APP_IMAGE, CREATE_MAC_APP_IMAGE);

    static final Set<BundlingOperationOptionScope> CREATE_NATIVE = Set.of(
            CREATE_WIN_MSI, CREATE_WIN_EXE,
            CREATE_LINUX_RPM, CREATE_LINUX_DEB,
            CREATE_MAC_DMG, CREATE_MAC_PKG);

    static final Set<BundlingOperationOptionScope> CREATE_BUNDLE = Stream.of(
            CREATE_APP_IMAGE,
            CREATE_NATIVE
    ).flatMap(Set::stream).collect(Collectors.toSet());

    private final Predicate<String> optionNamePredicate;
    private final OperatingSystem os;
    private final PackageType packageType;
}
