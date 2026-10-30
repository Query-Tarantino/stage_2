package org.ulpgc.tarantino.bench.charts;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.AxesChartStyler;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Writes a chart of a run as {@code charts/<name>.png}, with the run's {@code run_id} in its footer (SPEC §9). */
final class RunChart {

    static final String DIRECTORY = "charts";
    static final int WIDTH = 900;
    static final int HEIGHT = 540;
    static final int FOOTER_HEIGHT = 28;

    private static final int FOOTER_MARGIN = 10;
    private static final String FORMAT = "png";
    private static final String FOOTER = "run_id: ";

    private RunChart() {
    }

    static Path write(Chart<? extends AxesChartStyler, ?> chart, Path runDirectory, String name) {
        Path file = runDirectory.resolve(DIRECTORY).resolve(name + "." + FORMAT);
        chart.getStyler().setLocale(Locale.ROOT);
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(withFooter(BitmapEncoder.getBufferedImage(chart), FOOTER + runDirectory.getFileName()), FORMAT, file.toFile());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage withFooter(BufferedImage plot, String footer) {
        BufferedImage image = new BufferedImage(plot.getWidth(), plot.getHeight() + FOOTER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(plot, 0, 0, null);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawString(footer, FOOTER_MARGIN, image.getHeight() - FOOTER_MARGIN);
        graphics.dispose();
        return image;
    }
}
