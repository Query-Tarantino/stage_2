package org.ulpgc.tarantino.control;

import org.ulpgc.tarantino.control.commands.ControlPipeline;
import org.ulpgc.tarantino.control.model.StepReport;
import org.ulpgc.tarantino.crawler.CrawlerConfig;
import org.ulpgc.tarantino.indexer.IndexerConfig;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        ControlConfig config = ControlConfig.fromEnvironment();
        CrawlerConfig crawler = CrawlerConfig.fromEnvironment();
        List<Integer> candidates = ControlFactory.candidates(config, crawler, Arrays.stream(args).findFirst());
        ControlPipeline pipeline = ControlFactory.pipeline(config, crawler, IndexerConfig.fromEnvironment(), candidates);
        Stream.generate(pipeline::runStep).takeWhile(report -> !report.idle()).forEach(Main::print);
        System.out.println("[CONTROL] Nothing left to do");
    }

    private static void print(StepReport report) {
        System.out.println("[CONTROL] " + report.description());
    }
}
