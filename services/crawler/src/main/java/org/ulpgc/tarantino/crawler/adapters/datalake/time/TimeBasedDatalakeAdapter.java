package org.ulpgc.tarantino.crawler.adapters.datalake.time;

import org.ulpgc.tarantino.crawler.adapters.datalake.BookFiles;
import org.ulpgc.tarantino.crawler.model.book.BookText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeBasedDatalakeAdapter implements DatalakeStorage {

    private static final DateTimeFormatter DAY_DIRECTORY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_DIRECTORY = DateTimeFormatter.ofPattern("HH");
    private static final String HEADER_SUFFIX = ".header.txt";
    private static final String BODY_SUFFIX = ".body.txt";
    private static final int BOOK_FILE_DEPTH = 3;

    private final Path root;
    private final Clock clock;

    public TimeBasedDatalakeAdapter(Path root) {
        this(root, Clock.systemUTC());
    }

    public TimeBasedDatalakeAdapter(Path root, Clock clock) {
        this.root = root;
        this.clock = clock;
    }

    @Override
    public StoredPaths save(BookText book) {
        return BookFiles.write(pathsIn(currentDirectory(), book.bookId()), book);
    }

    @Override
    public Optional<StoredPaths> pathsOf(int bookId) {
        return bodyFile(bookId).map(body -> pathsIn(body.getParent(), bookId));
    }

    @Override
    public int removeIncompleteWrites() {
        return BookFiles.removeIncompleteWrites(root);
    }

    @Override
    public Set<Integer> idsStoredSince(Instant instant) {
        String firstHour = hourKey(LocalDateTime.ofInstant(instant, clock.getZone()));
        return hourDirectories()
                .filter(directory -> hourKey(directory).compareTo(firstHour) >= 0)
                .flatMap(TimeBasedDatalakeAdapter::bookIdsIn)
                .collect(Collectors.toSet());
    }

    private Path currentDirectory() {
        LocalDateTime now = LocalDateTime.now(clock);
        return root.resolve(DAY_DIRECTORY.format(now)).resolve(HOUR_DIRECTORY.format(now));
    }

    private Stream<Path> hourDirectories() {
        return BookFiles.children(root).stream().flatMap(day -> BookFiles.children(day).stream());
    }

    private static String hourKey(LocalDateTime time) {
        return DAY_DIRECTORY.format(time) + HOUR_DIRECTORY.format(time);
    }

    private static String hourKey(Path hourDirectory) {
        return hourDirectory.getParent().getFileName().toString() + hourDirectory.getFileName();
    }

    private static Stream<Integer> bookIdsIn(Path hourDirectory) {
        return BookFiles.children(hourDirectory).stream()
                .filter(file -> file.getFileName().toString().endsWith(BODY_SUFFIX))
                .map(file -> BookFiles.bookId(file, BODY_SUFFIX));
    }

    private static StoredPaths pathsIn(Path directory, int bookId) {
        return new StoredPaths(directory.resolve(bookId + HEADER_SUFFIX), directory.resolve(bookId + BODY_SUFFIX));
    }

    private Optional<Path> bodyFile(int bookId) {
        return Files.isDirectory(root) ? firstFileNamed(bookId + BODY_SUFFIX) : Optional.empty();
    }

    private Optional<Path> firstFileNamed(String name) {
        try (Stream<Path> files = Files.find(root, BOOK_FILE_DEPTH, (path, attributes) -> path.endsWith(name))) {
            return files.findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
