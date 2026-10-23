package org.ulpgc.tarantino.crawler.benchmarking.support.results;

public record Metric(String name, String unit, Conversion conversion) {

    @FunctionalInterface
    public interface Conversion {
        double value(double score, int books);
    }

    public static Metric asMeasured(String name, String unit) {
        return new Metric(name, unit, (score, books) -> score);
    }

    public static Metric perBook(String name, int booksPerRun) {
        return new Metric(name, "ms/book", (milliseconds, books) -> milliseconds / booksPerRun);
    }

    public static Metric booksPerSecond(String name) {
        return new Metric(name, "books/s", (milliseconds, books) -> books / (milliseconds / 1000));
    }

    public ResultRow row(String structure, int books, double score, double scoreError) {
        double value = conversion.value(score, books);
        return new ResultRow(structure, name, books, value, Math.abs(value * scoreError / score), unit);
    }
}
