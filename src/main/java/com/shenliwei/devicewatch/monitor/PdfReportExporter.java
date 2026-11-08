package com.shenliwei.devicewatch.monitor;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * Minimal PDF report writer for the history page: title, metadata, a
 * statistics summary and chart snapshots. Uses the system Chinese font
 * so labels and captions render correctly.
 */
final class PdfReportExporter {
    record Snapshot(String caption, BufferedImage image) {
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PdfReportExporter() {
    }

    static BufferedImage renderChart(Component component, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            component.setSize(width, height);
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    static void write(
            Path file,
            String title,
            List<String> metaLines,
            List<String> statsLines,
            List<Snapshot> charts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadChineseFont(document);
            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float margin = 56;
            float cursorY = pageHeight - 64;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            try {
                content.setFont(font, 20);
                content.beginText();
                content.newLineAtOffset(margin, cursorY);
                content.showText(title);
                content.endText();
                cursorY -= 30;

                content.setFont(font, 10);
                for (String line : metaLines) {
                    content.beginText();
                    content.newLineAtOffset(margin, cursorY);
                    content.showText(line);
                    content.endText();
                    cursorY -= 17;
                }
                cursorY -= 6;

                content.setFont(font, 13);
                content.beginText();
                content.newLineAtOffset(margin, cursorY);
                content.showText("统计摘要");
                content.endText();
                cursorY -= 20;
                content.setFont(font, 10);
                for (String line : statsLines) {
                    content.beginText();
                    content.newLineAtOffset(margin, cursorY);
                    content.showText(line);
                    content.endText();
                    cursorY -= 17;
                }
                cursorY -= 6;

                float chartWidth = pageWidth - 2 * margin;
                float chartHeight = chartWidth * 9f / 16f;
                for (Snapshot chart : charts) {
                    if (cursorY < margin + chartHeight + 40) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        cursorY = pageHeight - 64;
                    }
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ImageIO.write(chart.image(), "png", bytes);
                    PDImageXObject imageObject =
                            PDImageXObject.createFromByteArray(
                                    document, bytes.toByteArray(), chart.caption());
                    content.drawImage(imageObject, margin, cursorY - chartHeight,
                            chartWidth, chartHeight);
                    cursorY -= chartHeight + 8;
                    content.setFont(font, 10);
                    content.beginText();
                    content.newLineAtOffset(margin, cursorY);
                    content.showText(chart.caption());
                    content.endText();
                    cursorY -= 26;
                }

                content.setFont(font, 9);
                content.beginText();
                content.newLineAtOffset(margin, 42);
                content.showText("本报告由设备状态监控与告警系统生成 · "
                        + LocalDateTime.now().format(TIME_FORMAT));
                content.endText();
            } finally {
                content.close();
            }
            document.save(file.toFile());
        }
    }

    private static PDType0Font loadChineseFont(PDDocument document) throws IOException {
        List<File> candidates = List.of(
                new File("C:\\Windows\\Fonts\\Deng.ttf"),
                new File("C:\\Windows\\Fonts\\simhei.ttf"),
                new File("C:\\Windows\\Fonts\\msyh.ttc"));
        for (File fontFile : candidates) {
            if (!fontFile.isFile()) {
                continue;
            }
            try {
                if (fontFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".ttc")) {
                    try (TrueTypeCollection collection = new TrueTypeCollection(fontFile)) {
                        TrueTypeFont chosen = null;
                        for (String name : new String[] {
                            "Microsoft YaHei UI", "Microsoft YaHei", "MicrosoftYaHeiUI"
                        }) {
                            chosen = collection.getFontByName(name);
                            if (chosen != null) {
                                break;
                            }
                        }
                        if (chosen == null) {
                            continue;
                        }
                        byte[] fontData;
                        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                            chosen.getOriginalData().transferTo(buffer);
                            fontData = buffer.toByteArray();
                        }
                        return PDType0Font.load(
                                document, new ByteArrayInputStream(fontData));
                    }
                }
                return PDType0Font.load(document, fontFile);
            } catch (IOException exception) {
                // Try the next candidate font.
            }
        }
        throw new IOException("未找到可用的中文字体（C:\\Windows\\Fonts）");
    }
}
