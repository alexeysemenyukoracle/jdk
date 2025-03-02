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
package jdk.jpackage.internal.util;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TokenReplace {

    public TokenReplace(String... tokens) {
        if (tokens.length == 0) {
            throw new IllegalArgumentException("Empty token list");
        }
        this.tokens = Set.of(tokens);
        regexp = Pattern.compile(this.tokens.stream()
                .sorted(Comparator.<String>naturalOrder().thenComparing(Comparator.comparingInt(String::length)))
                .map(Pattern::quote).collect(joining("|", "(", ")")));
    }

    public String applyTo(String str, Function<String, Object> tokenValueSupplier) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(tokenValueSupplier);
        return regexp.matcher(str).replaceAll(mr -> {
            final var token = mr.group();
            return Matcher.quoteReplacement(Objects.requireNonNull(tokenValueSupplier.apply(token), () -> {
                return String.format("Null value for token [%s]", token);
            }).toString());
        });
    }

    @Override
    public String toString() {
        if (tokens.size() == 1) {
            return "TokenReplace(" + tokens.iterator().next() + ")";
        } else {
            return "TokenReplace(" + regexp.toString() + ")";
        }
    }

    public static String applyTo(List<TokenReplace> tokens, String str, Function<String, Object> tokenValueSupplier) {
        for (final var tokenReplace : tokens) {
            str = tokenReplace.applyTo(str, tokenValueSupplier);
        }
        return str;
    }

    public static TokenReplace combine(TokenReplace x, TokenReplace y) {
        return new TokenReplace(Stream.of(x.tokens, y.tokens).flatMap(Collection::stream).distinct().toArray(String[]::new));
    }

    public static Function<String, Object> createCachingTokenValueSupplier(Map<String, Supplier<Object>> tokenValueSuppliers) {
        Objects.requireNonNull(tokenValueSuppliers);
        final Map<String, Object> cache = new HashMap<>();
        return token -> {
            return cache.computeIfAbsent(token, k -> {
                final var tokenValueSupplier = Objects.requireNonNull(tokenValueSuppliers.get(token), () -> {
                    return String.format("No token value supplier for token [%s]", token);
                });
                return Objects.requireNonNull(tokenValueSupplier.get(), () -> {
                    return String.format("Null value for token [%s]", token);
                });
            });
        };
    }

    private final Set<String> tokens;
    private final Pattern regexp;
}
