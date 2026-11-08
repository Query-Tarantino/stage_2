package org.ulpgc.tarantino.crawler.adapters.gutenberg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ulpgc.tarantino.common.model.BookErrorCode;
import org.ulpgc.tarantino.crawler.model.failure.DownloadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void replacesBytesThatAreNotUtf8AsTheHttpDownloadDoes() throws IOException {
        Files.createDirectories(mirror.resolve("84"));
        Files.write(mirror.resolve("84/pg84.txt"), new byte[]{'F', 'r', (byte) 0xE9, 'e'});

        assertEquals("Fr�e", new LocalMirrorDownloader(mirror).rawText(84));
    }

    @Test
    void reportsABookMissingFromTheMirrorAsNotFound() {
        DownloadException failure = assertThrows(DownloadException.class, () -> new LocalMirrorDownloader(mirror).rawText(84));

        assertEquals(BookErrorCode.NOT_FOUND, failure.reason());
    }
}
