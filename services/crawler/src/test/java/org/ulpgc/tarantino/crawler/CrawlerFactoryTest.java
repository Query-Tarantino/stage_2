package org.ulpgc.tarantino.crawler;

import org.junit.jupiter.api.Test;
import org.ulpgc.tarantino.common.config.TarantinoConfig;
import org.ulpgc.tarantino.common.config.TarantinoService;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.GutenbergHttpDownloader;
import org.ulpgc.tarantino.crawler.adapters.gutenberg.LocalMirrorDownloader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CrawlerFactoryTest {

    @Test
    void downloadsOverHttpUnlessALocalMirrorIsConfigured() {
        assertInstanceOf(GutenbergHttpDownloader.class,
                CrawlerFactory.downloader(TarantinoConfig.from(Map.of(), TarantinoService.CRAWLER)));
        assertInstanceOf(LocalMirrorDownloader.class,
                CrawlerFactory.downloader(TarantinoConfig.from(Map.of("TARANTINO_MIRROR", "mirror"), TarantinoService.CRAWLER)));
    }
}
