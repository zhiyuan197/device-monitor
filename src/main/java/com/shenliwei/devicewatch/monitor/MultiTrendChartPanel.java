package com.shenliwei.devicewatch.monitor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.JPanel;

/**
 * Lightweight multi-series line chart used for whole-network aggregates.
 */
final class MultiTrendChartPanel extends JPanel {
    record Series(String name, Color color, List<Double> values) {
    }

    private final String title;
    private final String unit;
    private final double minimum;
    private final double maximum;
    private List<Series> series = List.of();
    private int hoverIndex = -1;

    MultiTrendChartPanel(String title, String unit, double minimum, double maximum) {
        this.title = title;
        this.unit = unit;
        this.minimum = minimum;
        this.maximum = maximum;
        setOpaque(false);
        setPreferredSize(new Dimension(480, 230));
        setMinimumSize(new Dimension(300, 150));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(event.getX());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoverIndex = -1;
                repaint();
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    void setSeries(List<Series> newSeries) {
        series = newSeries == null ? List.of() : List.copyOf(newSeries);
        hoverIndex = -1;
        repaint();
    }

    private void updateHover(int x) {
        int left = 42;
        int chartWidth = Math.max(1, getWidth() - left - 14);
        int count = series.stream().mapToInt(line -> line.values().size()).max().orElse(0);
        hoverIndex = count >= 2
                ? TrendChartPanel.nearestIndex(x, count, left, chartWidth)
                : -1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int left = 42;
            int right = 14;
            int top = 46;
            int bottom = 28;
            int chartWidth = Math.max(1, width - left - right);
            int chartHeight = Math.max(1, height - top - bottom);

            g.setColor(UiStyles.TEXT);
            g.setFont(UiStyles.FONT_BOLD.deriveFont(13f));
            g.drawString(title, 12, 22);

            g.setFont(UiStyles.FONT.deriveFont(11f));
            for (int i = 0; i <= 4; i++) {
                int y = top + (chartHeight * i / 4);
                double labelValue = maximum - ((maximum - minimum) * i / 4.0);
                g.setColor(UiStyles.BORDER);
                g.drawLine(left, y, left + chartWidth, y);
                g.setColor(UiStyles.MUTED);
                g.drawString("%.0f".formatted(labelValue), 10, y + 4);
            }

            boolean anyData = series.stream().anyMatch(s -> s.values().size() >= 2);
            if (!anyData) {
                g.setColor(UiStyles.MUTED);
                g.setFont(UiStyles.FONT.deriveFont(13f));
                String message = "等待各设备上报数据";
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(message,
                        left + (chartWidth - metrics.stringWidth(message)) / 2,
                        top + chartHeight / 2);
            } else {
                for (Series line : series) {
                    List<Double> values = line.values();
                    if (values.size() < 2) {
                        continue;
                    }
                    Path2D path = new Path2D.Double();
                    for (int i = 0; i < values.size(); i++) {
                        double x = left + chartWidth * i / (double) (values.size() - 1);
                        double y = valueY(values.get(i), top, chartHeight);
                        if (i == 0) {
                            path.moveTo(x, y);
                        } else {
                            path.lineTo(x, y);
                        }
                    }
                    g.setColor(line.color());
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    g.draw(path);
                }
            }

            int legendX = left;
            g.setFont(UiStyles.FONT.deriveFont(11.5f));
            for (Series line : series) {
                String label = line.name();
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(line.color());
                g.fillRoundRect(legendX, height - 18, 12, 6, 6, 6);
                g.setColor(UiStyles.MUTED);
                g.drawString(label, legendX + 16, height - 8);
                legendX += 16 + 8 + metrics.stringWidth(label) + 18;
            }

            if (hoverIndex >= 0) {
                int count = series.stream().mapToInt(line -> line.values().size()).max().orElse(0);
                if (count >= 2) {
                    double hoverX = left + chartWidth * hoverIndex / (double) (count - 1);
                    g.setColor(UiStyles.BORDER);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            1f, new float[] {3f, 3f}, 0f));
                    g.drawLine((int) Math.round(hoverX), top, (int) Math.round(hoverX),
                            top + chartHeight);
                    drawTooltip(g, hoverX, top);
                }
            }
        } finally {
            g.dispose();
        }
    }

    private void drawTooltip(Graphics2D g, double anchorX, int top) {
        List<String> lines = new java.util.ArrayList<>();
        for (Series line : series) {
            if (hoverIndex < line.values().size()) {
                lines.add(line.name() + "  " + "%.1f".formatted(line.values().get(hoverIndex)));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        g.setFont(UiStyles.FONT.deriveFont(11.5f));
        FontMetrics metrics = g.getFontMetrics();
        int boxWidth = lines.stream().mapToInt(metrics::stringWidth).max().orElse(40) + 16;
        int boxHeight = lines.size() * 16 + 12;
        int boxX = (int) Math.round(anchorX + 10);
        int boxY = top + 8;
        boxX = Math.min(boxX, getWidth() - boxWidth - 4);
        g.setColor(UiStyles.TEXT);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);
        g.setColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), boxX + 8, boxY + 18 + i * 16);
        }
    }

    private int valueY(double value, int top, int chartHeight) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        return top + (int) Math.round((maximum - clamped) / (maximum - minimum) * chartHeight);
    }
}
