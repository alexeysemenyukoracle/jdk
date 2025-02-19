/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Group of paths. Each path in the group is assigned a unique id.
 */
public final class PathGroup {

    /**
     * Creates path group with the initial paths.
     *
     * @param paths the initial paths
     */
    public PathGroup(Map<Object, Path> paths) {
        entries = new HashMap<>(paths);
    }

    /**
     * Returns a path associated with the given identifier in this path group.
     *
     * @param id the identifier
     * @return the path corresponding to the given identifier in this path group or
     *         <code>null</code> if there is no such path
     */
    public Path getPath(Object id) {
        Objects.requireNonNull(id);
        return entries.get(id);
    }

    /**
     * Assigns the specified path value to the given identifier in this path group.
     * If the given identifier doesn't exist in this path group, it is added,
     * otherwise, the current value associated with the identifier is replaced with
     * the given path value. If the path value is <code>null</code> the given
     * identifier is removed from this path group if it existed; otherwise, no
     * action is taken.
     *
     * @param id   the identifier
     * @param path the path to associate with the identifier or <code>null</code>
     */
    public void setPath(Object id, Path path) {
        Objects.requireNonNull(id);
        if (path != null) {
            entries.put(id, path);
        } else {
            entries.remove(id);
        }
    }

    /**
     * Adds a path associated with the new unique identifier to this path group.
     *
     * @param path the path to associate the new unique identifier in this path
     *             group
     */
    public void ghostPath(Path path) {
        Objects.requireNonNull(path);
        setPath(new Object(), path);
    }

    /**
     * Gets all identifiers of this path group.
     * <p>
     * The order of identifiers in the returned list is undefined.
     *
     * @return all identifiers of this path group
     */
    public Set<Object> keys() {
        return entries.keySet();
    }

    /**
     * Gets paths associated with all identifiers in this path group.
     * <p>
     * The order of paths in the returned list is undefined.
     *
     * @return paths associated with all identifiers in this path group
     */
    public List<Path> paths() {
        return entries.values().stream().toList();
    }

    /**
     * Gets root paths in this path group.
     * <p>
     * If multiple identifiers are associated with the same path value in the group,
     * the path value is added to the returned list only once. Paths that are
     * descendants of other paths in the group are not added to the returned list.
     * <p>
     * The order of paths in the returned list is undefined.
     *
     * @return unique root paths in this path group
     */
    public List<Path> roots() {
        if (entries.isEmpty()) {
            return List.of();
        }

        // Sort by the number of path components in descending order.
        final var sorted = entries.entrySet().stream().map(e -> {
            return Map.entry(e.getValue().normalize(), e.getValue());
        }).sorted(Comparator.comparingInt(e -> e.getValue().getNameCount() * -1)).distinct().toList();

        final var shortestNormalizedPath = sorted.getLast().getKey();
        if (shortestNormalizedPath.getNameCount() == 1 && shortestNormalizedPath.getFileName().toString().isEmpty()) {
            return List.of(sorted.getLast().getValue());
        }

        final List<Path> roots = new ArrayList<>();

        for (int i = 0; i < sorted.size(); ++i) {
            final var path = sorted.get(i).getKey();
            boolean pathIsRoot = true;
            for (int j = i + 1; j < sorted.size(); ++j) {
                final var maybeParent = sorted.get(j).getKey();
                if (path.getNameCount() > maybeParent.getNameCount() && path.startsWith(maybeParent)) {
                    pathIsRoot = false;
                    break;
                }
            }

            if (pathIsRoot) {
                roots.add(sorted.get(i).getValue());
            }
        }

        return roots;
    }

    /**
     * Gets the number of bytes in root paths of this path group. The method sums
     * the size of all root path entries in the group. If the path entry is a
     * directory it calculates the total size of the files in the directory. If the
     * path entry is a file, it takes its size.
     *
     * @return the total number of bytes in root paths of this path group
     * @throws IOException If an I/O error occurs
     */
    public long sizeInBytes() throws IOException {
        long reply = 0;
        for (Path dir : roots().stream().filter(Files::isDirectory).toList()) {
            try (Stream<Path> stream = Files.walk(dir)) {
                reply += stream.filter(Files::isRegularFile).mapToLong(f -> f.toFile().length()).sum();
            }
        }
        return reply;
    }

    /**
     * Creates a copy of this path group with all paths resolved against the given
     * root. Taken action is equivalent to creating a copy of this path group and
     * calling <code>root.resolve()</code> on every path in the copy.
     *
     * @param root the root against which to resolve paths
     *
     * @return a new path group resolved against the given root path
     */
    public PathGroup resolveAt(Path root) {
        Objects.requireNonNull(root);
        return new PathGroup(entries.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> root.resolve(e.getValue()))));
    }

    /**
     * Copies files/directories from the locations in the path group into the
     * locations of the given path group. For every identifier found in this and the
     * given group, copy the associated file or directory from the location
     * specified by the path value associated with the identifier in this group into
     * the location associated with the identifier in the given group.
     *
     * @param dst the destination path group
     * @throws IOException If an I/O error occurs
     */
    public void copy(PathGroup dst, CopyOption ...options) throws IOException {
        copy(this, dst, new Copy(false, options));
    }

    /**
     * Similar to {@link #copy(PathGroup)} but moves files/directories instead of
     * copying.
     *
     * @param dst the destination path group
     * @throws IOException If an I/O error occurs
     */
    public void move(PathGroup dst, CopyOption ...options) throws IOException {
        copy(this, dst, new Copy(true, options));
        deleteEntries();
    }

    /**
     * Similar to {@link #copy(PathGroup)} but uses the given handler to transform
     * paths instead of coping.
     *
     * @param dst the destination path group
     * @param handler the path transformation handler
     * @throws IOException If an I/O error occurs
     */
    public void transform(PathGroup dst, TransformHandler handler) throws IOException {
        copy(this, dst, handler);
    }

    /**
     * Handler of file copying and directory creating.
     *
     * @see #transform
     */
    public static interface TransformHandler {

        /**
         * Request to copy a file from the given source location into the given
         * destination location.
         *
         * @implNote Default implementation takes no action
         *
         * @param src the source file location
         * @param dst the destination file location
         * @throws IOException If an I/O error occurs
         */
        default void copyFile(Path src, Path dst) throws IOException {

        }

        /**
         * Request to create a directory at the given location.
         *
         * @implNote Default implementation takes no action
         *
         * @param dir the path where the directory is requested to be created
         * @throws IOException
         */
        default void createDirectory(Path dir) throws IOException {

        }
    }

    private void deleteEntries() throws IOException {
        for (final var file : entries.values()) {
            if (Files.isDirectory(file)) {
                FileUtils.deleteRecursive(file);
            } else {
                Files.deleteIfExists(file);
            }
        }
    }

    private record CopySpec(Path from, Path to, Path fromNormalized, Path toNormalized) {
        CopySpec {
            Objects.requireNonNull(from);
            Objects.requireNonNull(fromNormalized);
            Objects.requireNonNull(to);
            Objects.requireNonNull(toNormalized);
        }

        CopySpec(Path from, Path to) {
            this(from, to, from.normalize(), to.normalize());
        }
    }

    private static void copy(PathGroup src, PathGroup dst, TransformHandler handler) throws IOException {
        List<CopySpec> copySpecs = new ArrayList<>();
        List<Path> excludePaths = new ArrayList<>();

        for (final var e : src.entries.entrySet()) {
            final var srcPath = e.getValue();
            final var dstPath = dst.entries.get(e.getKey());
            if (dstPath != null) {
                copySpecs.add(new CopySpec(srcPath, dstPath));
            } else {
                excludePaths.add(srcPath.normalize());
            }
        }

        copy(copySpecs, excludePaths, handler);
    }

    private record Copy(boolean move, CopyOption ... options) implements TransformHandler {
        @Override
        public void copyFile(Path src, Path dst) throws IOException {
            Files.createDirectories(dst.getParent());
            if (move) {
                Files.move(src, dst, options);
            } else {
                Files.copy(src, dst, options);
            }
        }

        @Override
        public void createDirectory(Path dir) throws IOException {
            Files.createDirectories(dir);
        }
    }

    private static boolean match(Path what, List<Path> paths) {
        return paths.stream().anyMatch(what::startsWith);
    }

    private static void copy(List<CopySpec> copySpecs, List<Path> excludePaths,
            TransformHandler handler) throws IOException {
        Objects.requireNonNull(excludePaths);
        Objects.requireNonNull(handler);

        final var copySpecMap = copySpecs.stream().<CopySpec>mapMulti((copySpec, consumer) -> {
            final var src = copySpec.from;

            if (!Files.exists(src) || match(src, excludePaths)) {
                return;
            }

            if (Files.isDirectory(copySpec.from)) {
                final var dst = copySpec.to;
                try (final var files = Files.walk(src)) {
                    files.filter(file -> {
                        return !match(file, excludePaths);
                    }).map(file -> {
                        return new CopySpec(file, dst.resolve(src.relativize(file)));
                    }).toList().forEach(consumer::accept);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            } else {
                consumer.accept(copySpec);
            }
        }).collect(toMap(CopySpec::toNormalized, x -> x, (x, y) -> {
            if (x.fromNormalized.equals(y.fromNormalized)) {
                // Duplicated copy specs, accept.
                return x;
            } else {
                throw new IllegalStateException(String.format(
                        "Duplicate source files [%s] and [%s] for [%s] destination file", x.from, y.from, x.to));
            }
        }));

        try {
            copySpecMap.values().stream().forEach(copySpec -> {
                try {
                    if (Files.isDirectory(copySpec.from)) {
                        handler.createDirectory(copySpec.to);
                    } else {
                        handler.copyFile(copySpec.from, copySpec.to);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private final Map<Object, Path> entries;
}
