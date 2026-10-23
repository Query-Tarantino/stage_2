package org.ulpgc.tarantino.crawler.benchmarking.support.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class Directories {

    private Directories() {
    }

    /** Deletes the files in parallel, as a folders index holds hundreds of thousands, and then the directories. */
    public static void delete(Path root) {
        Tree tree = Tree.of(root);
        tree.files().parallelStream().forEach(Directories::deleteEntry);
        tree.directories().reversed().forEach(Directories::deleteEntry);
    }

    /** Creates the directories and then copies the files in parallel. */
    public static void copy(Path source, Path target) {
        Tree tree = Tree.of(source);
        tree.directories().forEach(directory -> createDirectories(target.resolve(source.relativize(directory))));
        tree.files().parallelStream().forEach(file -> copyFile(file, target.resolve(source.relativize(file))));
    }

    public static long fileCount(Path root) {
        return fileCount(root, file -> true);
    }

    public static long fileCount(Path root, Predicate<Path> filter) {
        return entries(root).stream().filter(Files::isRegularFile).filter(filter).count();
    }

    /** Measures the tree in one walk, reading sizes from the attributes the walk already has. */
    public static Footprint footprint(Path root) {
        if (!Files.exists(root)) {
            return Footprint.NONE;
        }
        try {
            long blockSize = Files.getFileStore(root).getBlockSize();
            FootprintVisitor visitor = new FootprintVisitor(root, blockSize);
            Files.walkFileTree(root, visitor);
            return visitor.footprint();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> entries(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.walk(root)) {
            return entries.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyFile(Path file, Path target) {
        try {
            Files.copy(file, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteEntry(Path entry) {
        try {
            Files.delete(entry);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The directories of a tree, each before its contents, and its other entries, listed in one walk. */
    private record Tree(List<Path> directories, List<Path> files) {

        private static Tree of(Path root) {
            Tree tree = new Tree(new ArrayList<>(), new ArrayList<>());
            if (!Files.exists(root)) {
                return tree;
            }
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                        tree.directories().add(directory);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        tree.files().add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return tree;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class FootprintVisitor extends SimpleFileVisitor<Path> {

        private final Path root;
        private final long blockSize;
        private long files;
        private long directories;
        private long bytes;
        private long allocatedBytes;

        private FootprintVisitor(Path root, long blockSize) {
            this.root = root;
            this.blockSize = blockSize;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (!directory.equals(root)) {
                directories++;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile()) {
                files++;
                bytes += attributes.size();
                allocatedBytes += (attributes.size() + blockSize - 1) / blockSize * blockSize;
            }
            return FileVisitResult.CONTINUE;
        }

        private Footprint footprint() {
            return new Footprint(files, directories, bytes, allocatedBytes);
        }
    }
}
