package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JLabel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the PDF export contract: a valid PDF document is produced and
 * chart snapshots render at the requested size.
 */
class PdfReportExporterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersChartSnapshotAtRequestedSize() {
        BufferedImage image = PdfReportExporter.renderChart(new JLabel("CPU"), 320, 180);
        assertEquals(320, image.getWidth());
        assertEquals(180, image.getHeight());
    }

    @Test
    void writesLoadablePdfWithPages() throws Exception {
        Path file = temporaryDirectory.resolve("report.pdf");
        BufferedImage chart = PdfReportExporter.renderChart(new JLabel("温度"), 320, 180);
        PdfReportExporter.write(
                file,
                "设备运行历史报告",
                List.of("设备：DEV-001", "时间范围：最近 6 小时"),
                List.of("样本数：120", "CPU 平均：45.2%"),
                List.of(new PdfReportExporter.Snapshot("温度趋势", chart)));

        byte[] bytes = Files.readAllBytes(file);
        assertTrue(bytes.length > 4);
        assertEquals('%', bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals('D', bytes[2]);
        assertEquals('F', bytes[3]);

        try (PDDocument document = PDDocument.load(file.toFile())) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }
}
