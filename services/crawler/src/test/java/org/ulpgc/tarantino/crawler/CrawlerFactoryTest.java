package org.ulpgc.tarantino.crawler;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CrawlerFactoryTest {

    @Test
    void downloadsOverHttpUnlessALocalMirrorIsConfigured() {
        assertInstanceOf(GutenbergHttpDownloader.class, CrawlerFactory.downloader(new CrawlerConfig(Path.of("datalake"), null)));
        assertInstanceOf(LocalMirrorDownloader.class,
                CrawlerFactory.downloader(new CrawlerConfig(Path.of("datalake"), Path.of("mirror"))));
    }
}
