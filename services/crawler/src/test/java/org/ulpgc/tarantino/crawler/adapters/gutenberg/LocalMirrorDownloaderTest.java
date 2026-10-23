package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;
import org.ulpgc.tarantino.crawler.model.failure.FailureReason;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalMirrorDownloaderTest {

    @TempDir
    Path mirror;

    @Test
    void readsABookFromTheGeneratedCollectionLayout() throws IOException {
        Files.createDirectories(mirror.resolve("1342"));
        Files.writeString(mirror.resolve("1342/pg1342.txt"), "Title: Pride and Prejudice — café");

        assertEquals("Title: Pride and Prejudice — café", new LocalMirrorDownloader(mirror).rawText(1342));
    }

    @Test
    void reportsABookMissingFromTheMirrorAsNotFound() {
        DownloadException failure = assertThrows(DownloadException.class, () -> new LocalMirrorDownloader(mirror).rawText(84));

        assertEquals(FailureReason.NOT_FOUND, failure.reason());
    }

    @Test
    void listsTheBooksWithATextInIdOrder() throws IOException {
        for (String file : List.of("1342/pg1342.txt", "84/pg84.txt", "11/pg11-images.epub", "README", "cache/pgcache.txt")) {
            Files.createDirectories(mirror.resolve(file).getParent());
            Files.writeString(mirror.resolve(file), "text");
        }

        assertEquals(List.of(84, 1342), new LocalMirrorDownloader(mirror).bookIds());
    }
}
