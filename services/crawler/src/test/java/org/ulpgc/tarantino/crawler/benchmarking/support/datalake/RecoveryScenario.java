package org.ulpgc.tarantino.crawler.benchmarking.support.datalake;

import org.ulpgc.tarantino.crawler.benchmarking.support.files.Directories;
import org.ulpgc.tarantino.crawler.commands.IngestBookCommand;
import org.ulpgc.tarantino.crawler.model.book.GutenbergText;
import org.ulpgc.tarantino.crawler.model.book.StoredPaths;
import org.ulpgc.tarantino.crawler.ports.BookDownloader;
import org.ulpgc.tarantino.crawler.ports.DatalakeStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RecoveryScenario {

    private static final Instant INTERRUPTED_RUN = Instant.parse("2025-09-25T14:00:00Z");
    private static final Instant RESUMED_RUN = INTERRUPTED_RUN.plus(Duration.ofHours(1));

    private final String layout;
    private final Path root;
    private final BookDownloader downloader;

    public RecoveryScenario(String layout, Path root, BookDownloader downloader) {
        this.layout = layout;
        this.root = root;
        this.downloader = downloader;
    }

    public RecoveryOutcome run(List<Integer> ids) {
        int interruptedBook = ids.size() / 2;
        ingest(ids.subList(0, interruptedBook), INTERRUPTED_RUN);
        interruptWhileStoring(ids.get(interruptedBook));
        datalake(RESUMED_RUN).removeIncompleteWrites();
        ingest(ids, RESUMED_RUN);
        return new RecoveryOutcome(everyBookStoredOnce(ids), leftoverFiles(ids));
    }

    private void ingest(List<Integer> ids, Instant runTime) {
        IngestBookCommand ingest = new IngestBookCommand(downloader, datalake(runTime));
        ids.forEach(ingest::execute);
    }

    private void interruptWhileStoring(int bookId) {
        StoredPaths paths = datalake(INTERRUPTED_RUN).save(GutenbergText.bookText(bookId, downloader.rawText(bookId)));
        try {
            Files.move(paths.body(), paths.body().resolveSibling(paths.body().getFileName() + ".tmp"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean everyBookStoredOnce(List<Integer> ids) {
        DatalakeStorage datalake = datalake(RESUMED_RUN);
        return bodyFileCount() == ids.size() && ids.stream().allMatch(id -> datalake.pathsOf(id).isPresent());
    }

    private long leftoverFiles(List<Integer> ids) {
        DatalakeStorage datalake = datalake(RESUMED_RUN);
        Set<Path> bookFiles = ids.stream()
                .map(datalake::pathsOf)
                .flatMap(Optional::stream)
                .flatMap(paths -> Stream.of(paths.header(), paths.body()))
                .collect(Collectors.toSet());
        return Directories.fileCount(root, file -> !bookFiles.contains(file));
    }

    private long bodyFileCount() {
        return Directories.fileCount(root, RecoveryScenario::isBody);
    }

    private static boolean isBody(Path file) {
        String name = file.getFileName().toString();
        return name.equals("body.txt") || name.endsWith(".body.txt");
    }

    private DatalakeStorage datalake(Instant runTime) {
        return DatalakeFixture.datalake(layout, root, Clock.fixed(runTime, ZoneOffset.UTC));
    }
}
