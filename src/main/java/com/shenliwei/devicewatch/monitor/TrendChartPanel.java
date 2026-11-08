package com.shenliwei.devicewatch.monitor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.JPanel;

final class TrendChartPanel extends JPanel {
    private final String title;
    private final String unit;
    private final double minimum;
    private final double maximum;
    private final Color seriesColor;
    private final Double threshold;
    private List<Double> values = List.of();
    private int hoverIndex = -1;

    TrendChartPanel(
            String title,
            String unit,
            double minimum,
            double maximum,
            Color seriesColor,
            Double threshold) {
        this.title = title;
        this.unit = unit;
        this.minimum = minimum;
        this.maximum = maximum;
        this.seriesColor = seriesColor;
        this.threshold = threshold;
        setOpaque(false);
        setPreferredSize(new Dimension(420, 220));
        setMinimumSize(new Dimension(280, 160));
        getAccessibleContext().setAccessibleName(title + "趋势图");
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

    void setValues(List<Double> newValues) {
        values = newValues == null ? List.of() : List.copyOf(newValues);
        hoverIndex = -1;
        repaint();
    }

    private void updateHover(int x) {
        int left = 44;
        int chartWidth = Math.max(1, getWidth() - left - 16);
        hoverIndex = values.size() >= 2
                ? nearestIndex(x, values.size(), left, chartWidth)
                : -1;
        repaint();
    }

    static int nearestIndex(double x, int count, int left, int width) {
        if (count <= 0) {
            return -1;
        }
        int last = count - 1;
        double position = count == 1 ? 0 : (x - left) / (double) width * last;
        return Math.max(0, Math.min(last, (int) Math.round(position)));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int left = 44;
            int right = 16;
            int top = 46;
            int bottom = 30;
            int chartWidth = Math.max(1, width - left - right);
            int chartHeight = Math.max(1, height - top - bottom);

            g.setColor(UiStyles.TEXT);
            g.setFont(UiStyles.FONT_BOLD.deriveFont(13f));
            g.drawString(title, 12, 22);

            String current = values.isEmpty()
                    ? "-"
                    : "%.1f %s".formatted(values.get(values.size() - 1), unit);
            g.setColor(seriesColor);
            g.setFont(UiStyles.FONT_BOLD.deriveFont(14f));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(current, width - metrics.stringWidth(current) - 12, 22);

            g.setFont(UiStyles.FONT.deriveFont(11f));
            for (int i = 0; i <= 4; i++) {
                int y = top + (chartHeight * i / 4);
                double labelValue = maximum - ((maximum - minimum) * i / 4.0);
                g.setColor(UiStyles.BORDER);
                g.drawLine(left, y, left + chartWidth, y);
                String label = "%.0f".formatted(labelValue);
                g.setColor(UiStyles.MUTED);
                g.drawString(label, 10, y + 4);
            }

            if (threshold != null && threshold >= minimum && threshold <= maximum) {
                int y = valueY(threshold, top, chartHeight);
                g.setColor(new Color(UiStyles.RED.getRed(), UiStyles.RED.getGreen(),
                        UiStyles.RED.getBlue(), 130));
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[] {5f, 4f}, 0f));
                g.drawLine(left, y, left + chartWidth, y);
                g.setColor(UiStyles.RED);
                g.drawString("阈值", left + chartWidth - 30, y - 5);
            }

            if (values.size() < 2) {
                g.setColor(UiStyles.MUTED);
                g.setFont(UiStyles.FONT.deriveFont(13f));
                String message = "等待设备实时数据";
                FontMetrics metrics2 = g.getFontMetrics();
                g.drawString(message,
                        left + (chartWidth - metrics2.stringWidth(message)) / 2,
                        top + chartHeight / 2);
            } else {
                Path2D line = new Path2D.Double();
                Path2D fill = new Path2D.Double();
                for (int i = 0; i < values.size(); i++) {
                    double x = left + chartWidth * i / (double) (values.size() - 1);
                    double y = valueY(values.get(i), top, chartHeight);
                    if (i == 0) {
                        line.moveTo(x, y);
                        fill.moveTo(x, top + chartHeight);
                        fill.lineTo(x, y);
                    } else {
                        line.lineTo(x, y);
                        fill.lineTo(x, y);
                    }
                }
                fill.lineTo(left + chartWidth, top + chartHeight);
                fill.closePath();
                g.setColor(new Color(seriesColor.getRed(), seriesColor.getGreen(),
                        seriesColor.getBlue(), 36));
                g.fill(fill);
                g.setColor(seriesColor);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(line);

                double lastX = left + chartWidth;
                double lastY = valueY(values.get(values.size() - 1), top, chartHeight);
                g.setColor(UiStyles.SURFACE);
                g.fill(new Ellipse2D.Double(lastX - 4.5, lastY - 4.5, 9, 9));
                g.setColor(seriesColor);
                g.fill(new Ellipse2D.Double(lastX - 3.2, lastY - 3.2, 6.4, 6.4));
            }

            if (hoverIndex >= 0 && hoverIndex < values.size()) {
                double hoverX = left + chartWidth * hoverIndex / (double) Math.max(1, values.size() - 1);
                double hoverY = valueY(values.get(hoverIndex), top, chartHeight);
                g.setColor(UiStyles.BORDER);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[] {3f, 3f}, 0f));
                g.drawLine((int) Math.round(hoverX), top, (int) Math.round(hoverX),
                        top + chartHeight);
                g.setColor(seriesColor);
                g.fill(new Ellipse2D.Double(hoverX - 3, hoverY - 3, 6, 6));
                drawTooltip(g, hoverX, hoverY,
                        "%.1f %s".formatted(values.get(hoverIndex), unit));
            }

            g.setColor(UiStyles.MUTED);
            g.setFont(UiStyles.FONT.deriveFont(11f));
            String caption = "最近 " + values.size() + " 个采样点";
            FontMetrics captionMetrics = g.getFontMetrics();
            g.drawString(caption, left + chartWidth - captionMetrics.stringWidth(caption),
                    height - 8);
        } finally {
            g.dispose();
        }
    }

    private int valueY(double value, int top, int chartHeight) {
        double clamped = Math.max(minimum, Math.min(maximum, value));
        return top + (int) Math.round((maximum - clamped) / (maximum - minimum) * chartHeight);
    }

    private void drawTooltip(Graphics2D g, double anchorX, double anchorY, String text) {
        g.setFont(UiStyles.FONT.deriveFont(11.5f));
        FontMetrics metrics = g.getFontMetrics();
        int boxWidth = metrics.stringWidth(text) + 16;
        int boxHeight = 24;
        int boxX = (int) Math.round(anchorX + 10);
        int boxY = (int) Math.round(anchorY - boxHeight / 2.0);
        boxX = Math.min(boxX, getWidth() - boxWidth - 4);
        boxY = Math.max(4, Math.min(boxY, getHeight() - boxHeight - 4));
        g.setColor(UiStyles.TEXT);
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, boxX + 8, boxY + 16);
    }
}
