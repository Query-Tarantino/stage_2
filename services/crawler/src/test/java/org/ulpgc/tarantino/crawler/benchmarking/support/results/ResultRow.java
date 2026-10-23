package org.ulpgc.tarantino.crawler.benchmarking.support.results;

import java.util.Locale;

public record ResultRow(String structure, String metric, int books, double value, double error, String unit) {

    public static final String LANGUAGE = "java";
    public static final String CSV_HEADER = "language,structure,metric,n_books,value,error,unit";
    /** {@code error} is the half-width of this confidence interval of the mean of the samples (SPEC §11). */
    public static final double CONFIDENCE = 0.95;

    public static ResultRow exact(String structure, String metric, int books, double value, String unit) {
        return new ResultRow(structure, metric, books, value, 0, unit);
    }

    public static ResultRow sample(String structure, String metric, int books, double value, String unit) {
        return new ResultRow(structure, metric, books, value, Double.NaN, unit);
    }

    public static ResultRow parse(String csvLine) {
        String[] fields = csvLine.split(",", -1);
        return new ResultRow(fields[1], fields[2], Integer.parseInt(fields[3]), Double.parseDouble(fields[4]),
                fields[5].isEmpty() ? Double.NaN : Double.parseDouble(fields[5]), fields[6]);
    }

    public String csvLine() {
        return String.join(",", LANGUAGE, structure, metric, String.valueOf(books), decimal(value), Double.isNaN(error) ? "" : decimal(error), unit);
    }

    private static String decimal(double number) {
        return String.format(Locale.ROOT, "%.3f", number);
    }
}
