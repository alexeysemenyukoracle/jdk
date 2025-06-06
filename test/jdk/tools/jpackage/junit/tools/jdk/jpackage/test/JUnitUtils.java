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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;


public final class JUnitUtils {

    public static void assertArrayEquals(Object expected, Object actual) {
        ARRAY_ASSERTERS.getOrDefault(expected.getClass().componentType(), OBJECT_ARRAY_ASSERTER).acceptUnchecked(expected, actual);
    }


    public static final class ExceptionAnalizer {

        public ExceptionAnalizer() {
        }

        public ExceptionAnalizer(Exception ex) {
            hasMessage(ex.getMessage());
            isInstanceOf(ex.getClass());
            Optional.ofNullable(ex.getCause()).map(Throwable::getClass).ifPresent(this::isCauseInstanceOf);
        }

        public boolean analize(Exception ex) {
            Objects.requireNonNull(ex);

            if (expectedType != null && !expectedType.isInstance(ex)) {
                return false;
            }

            if (expectedMessage != null && !expectedMessage.equals(ex.getMessage())) {
                return false;
            }

            if (expectedCauseType != null && !expectedCauseType.isInstance(ex.getCause())) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(expectedCauseType, expectedMessage, expectedType);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            ExceptionAnalizer other = (ExceptionAnalizer) obj;
            return Objects.equals(expectedCauseType, other.expectedCauseType)
                    && Objects.equals(expectedMessage, other.expectedMessage)
                    && Objects.equals(expectedType, other.expectedType);
        }

        public ExceptionAnalizer hasMessage(String v) {
            expectedMessage = v;
            return this;
        }

        public ExceptionAnalizer isInstanceOf(Class<? extends Exception> v) {
            expectedType = v;
            return this;
        }

        public ExceptionAnalizer isCauseInstanceOf(Class<? extends Throwable> v) {
            expectedCauseType = v;
            return this;
        }

        public ExceptionAnalizer hasCause(boolean v) {
            return isCauseInstanceOf(v ? Exception.class : null);
        }

        public ExceptionAnalizer hasCause() {
            return hasCause(true);
        }

        private String expectedMessage;
        private Class<? extends Exception> expectedType;
        private Class<? extends Throwable> expectedCauseType;
    }


    @FunctionalInterface
    private interface ArrayEqualsAsserter<T> {
        void accept(T expected, T actual);

        @SuppressWarnings("unchecked")
        default void acceptUnchecked(Object expected, Object actual) {
            accept((T)expected, (T)actual);
        }
    }


    private static final Map<Class<?>, ArrayEqualsAsserter<?>> ARRAY_ASSERTERS = Map.of(
            boolean.class, (ArrayEqualsAsserter<boolean[]>)Assertions::assertArrayEquals,
            byte.class, (ArrayEqualsAsserter<byte[]>)Assertions::assertArrayEquals,
            char.class, (ArrayEqualsAsserter<char[]>)Assertions::assertArrayEquals,
            double.class, (ArrayEqualsAsserter<double[]>)Assertions::assertArrayEquals,
            float.class, (ArrayEqualsAsserter<float[]>)Assertions::assertArrayEquals,
            int.class, (ArrayEqualsAsserter<int[]>)Assertions::assertArrayEquals,
            long.class, (ArrayEqualsAsserter<long[]>)Assertions::assertArrayEquals,
            short.class, (ArrayEqualsAsserter<short[]>)Assertions::assertArrayEquals
    );

    private static final ArrayEqualsAsserter<Object[]> OBJECT_ARRAY_ASSERTER = Assertions::assertArrayEquals;
}
