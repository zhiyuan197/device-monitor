package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ReportsPage extends PagePanel {
    private final JComboBox<String> deviceSelector = new JComboBox<>();
    private final JSpinner hoursSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 168, 1));
    private final JButton queryButton = UiStyles.primaryButton("查询历史");
    private final JButton exportTelemetryButton = UiStyles.secondaryButton("导出指标 CSV");
    private final JButton exportAlertsButton = UiStyles.secondaryButton("导出告警 CSV");
    private final JButton exportPdfButton = UiStyles.secondaryButton("导出 PDF");
    private final JLabel sampleStat = stat();
    private final JLabel cpuAverage = stat();
    private final JLabel cpuMaximum = stat();
    private final JLabel memoryAverage = stat();
    private final JLabel temperatureMaximum = stat();
    private final JLabel alertStat = stat();
    private final TrendChartPanel cpuChart =
            new TrendChartPanel("CPU 使用率", "%", 0, 100, UiStyles.BLUE, 80.0);
    private final TrendChartPanel memoryChart =
            new TrendChartPanel("内存使用率", "%", 0, 100, UiStyles.CYAN, 85.0);
    private final TrendChartPanel temperatureChart =
            new TrendChartPanel("温度", "°C", 30, 100, UiStyles.AMBER, 75.0);
    private final DefaultTableModel telemetryModel = new DefaultTableModel(
            new String[] {"采集时间", "接收时间", "CPU", "内存", "温度", "磁盘"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel alertModel = new DefaultTableModel(
            new String[] {"级别", "类型", "状态", "触发值", "触发时间", "恢复时间", "确认人", "确认时间"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private List<TelemetryView> telemetryRows = List.of();
    private List<AlertView> alertRows = List.of();

    ReportsPage(DashboardFrame frame) {
        super(frame);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setBackground(UiStyles.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(UiStyles.pageTitle("历史报告"));
        heading.add(titleBlock, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("设备"));
        deviceSelector.setPreferredSize(new Dimension(180, 30));
        controls.add(deviceSelector);
        controls.add(new JLabel("最近小时"));
        controls.add(hoursSpinner);
        queryButton.addActionListener(event -> query());
        controls.add(queryButton);
        controls.add(exportTelemetryButton);
        controls.add(exportAlertsButton);
        controls.add(exportPdfButton);
        exportTelemetryButton.setEnabled(false);
        exportAlertsButton.setEnabled(false);
        exportPdfButton.setEnabled(false);
        heading.add(controls, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 6, 10, 0));
        stats.setOpaque(false);
        stats.add(statCard("样本数", sampleStat, UiStyles.BLUE));
        stats.add(statCard("CPU 平均", cpuAverage, UiStyles.BLUE));
        stats.add(statCard("CPU 峰值", cpuMaximum, UiStyles.RED));
        stats.add(statCard("内存平均", memoryAverage, UiStyles.CYAN));
        stats.add(statCard("温度峰值", temperatureMaximum, UiStyles.AMBER));
        stats.add(statCard("告警条数", alertStat, UiStyles.RED));
        add(stats, BorderLayout.CENTER);

        JPanel charts = new JPanel(new GridLayout(1, 3, 12, 0));
        charts.setOpaque(false);
        charts.add(chartCard(cpuChart));
        charts.add(chartCard(memoryChart));
        charts.add(chartCard(temperatureChart));

        JTable telemetryTable = new JTable(telemetryModel);
        JTable alertsTable = new JTable(alertModel);
        telemetryTable.setRowHeight(30);
        alertsTable.setRowHeight(30);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("指标趋势", charts);
        tabs.addTab("指标明细", new JScrollPane(telemetryTable));
        tabs.addTab("告警明细", new JScrollPane(alertsTable));
        add(tabs, BorderLayout.SOUTH);

        exportTelemetryButton.addActionListener(event -> exportTelemetry());
        exportAlertsButton.addActionListener(event -> exportAlerts());
        exportPdfButton.addActionListener(event -> exportPdf());
        refreshDeviceSelector(null);
    }

    private JPanel chartCard(TrendChartPanel chart) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout());
        UiStyles.card(card, 10, 12);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    void preselectDevice(String deviceId) {
        refreshDeviceSelector(deviceId);
        query();
    }

    @Override
    void onDevicesChanged() {
        refreshDeviceSelector((String) deviceSelector.getSelectedItem());
    }

    private void refreshDeviceSelector(String preferred) {
        Object previous = preferred != null ? preferred : deviceSelector.getSelectedItem();
        deviceSelector.removeAllItems();
        deviceSelector.addItem("全部设备");
        for (DeviceView device : frame.deviceList()) {
            deviceSelector.addItem(device.deviceId());
        }
        if (previous != null) {
            deviceSelector.setSelectedItem(previous);
        }
    }

    private void query() {
        queryButton.setEnabled(false);
        telemetryModel.setRowCount(0);
        alertModel.setRowCount(0);
        telemetryRows = List.of();
        alertRows = List.of();
        exportTelemetryButton.setEnabled(false);
        exportAlertsButton.setEnabled(false);
        exportPdfButton.setEnabled(false);
        resetStats();

        long to = System.currentTimeMillis();
        long from = Instant.ofEpochMilli(to)
                .minus(((Number) hoursSpinner.getValue()).longValue(), ChronoUnit.HOURS)
                .toEpochMilli();
        String selected = (String) deviceSelector.getSelectedItem();
        String deviceId = selected == null || "全部设备".equals(selected) ? null : selected;

        AtomicInteger remaining = new AtomicInteger(2);
        CompletableFuture<List<TelemetryView>> telemetryFuture = deviceId == null
                ? CompletableFuture.completedFuture(List.of())
                : frame.client().queryTelemetry(deviceId, from, to, 1000);
        CompletableFuture<List<AlertView>> alertFuture =
                frame.client().queryAlerts(deviceId, null, null, from, to, 500);

        telemetryFuture.whenComplete((samples, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error == null) {
                        telemetryRows = samples;
                        for (TelemetryView sample : samples) {
                            telemetryModel.addRow(new Object[] {
                                UiStyles.formatTime(sample.collectedAt()),
                                UiStyles.formatTime(sample.receivedAt()),
                                UiStyles.formatPercent(sample.cpuPercent()),
                                UiStyles.formatPercent(sample.memoryPercent()),
                                UiStyles.formatTemperature(sample.temperatureCelsius()),
                                UiStyles.formatPercent(sample.diskPercent())
                            });
                        }
                        updateTelemetryStats(samples);
                    } else {
                        showQueryError(error);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        queryButton.setEnabled(true);
                        exportPdfButton.setEnabled(!telemetryRows.isEmpty());
                    }
                }));

        alertFuture.whenComplete((alerts, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error == null) {
                        alertRows = alerts;
                        for (AlertView alert : alerts) {
                            alertModel.addRow(new Object[] {
                                severityText(alert.severity()),
                                AlertsPage.alertLabel(alert.ruleType()),
                                "ACTIVE".equals(alert.status()) ? "活动" : "已恢复",
                                triggerText(alert),
                                UiStyles.formatTime(alert.triggeredAt()),
                                UiStyles.formatTime(alert.resolvedAt() == null
                                        ? 0 : alert.resolvedAt()),
                                alert.acknowledgedBy() == null
                                        || alert.acknowledgedBy().isBlank()
                                        ? "-" : alert.acknowledgedBy(),
                                UiStyles.formatTime(alert.acknowledgedAt() == null
                                        ? 0 : alert.acknowledgedAt())
                            });
                        }
                        alertStat.setText(Integer.toString(alerts.size()));
                        exportAlertsButton.setEnabled(!alerts.isEmpty());
                    } else {
                        showQueryError(error);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        queryButton.setEnabled(true);
                        exportPdfButton.setEnabled(!telemetryRows.isEmpty());
                    }
                }));
    }

    private void updateTelemetryStats(List<TelemetryView> samples) {
        if (samples.isEmpty()) {
            sampleStat.setText("0");
            return;
        }
        double cpuAverageValue = samples.stream()
                .mapToDouble(TelemetryView::cpuPercent).average().orElse(0);
        double cpuMax = samples.stream()
                .mapToDouble(TelemetryView::cpuPercent).max().orElse(0);
        double memoryAverageValue = samples.stream()
                .mapToDouble(TelemetryView::memoryPercent).average().orElse(0);
        double temperatureMax = samples.stream()
                .mapToDouble(TelemetryView::temperatureCelsius).max().orElse(0);
        sampleStat.setText(Integer.toString(samples.size()));
        cpuAverage.setText("%.1f%%".formatted(cpuAverageValue));
        cpuMaximum.setText("%.1f%%".formatted(cpuMax));
        memoryAverage.setText("%.1f%%".formatted(memoryAverageValue));
        temperatureMaximum.setText("%.1f °C".formatted(temperatureMax));
        cpuChart.setValues(samples.stream().map(TelemetryView::cpuPercent).toList());
        memoryChart.setValues(samples.stream().map(TelemetryView::memoryPercent).toList());
        temperatureChart.setValues(samples.stream()
                .map(TelemetryView::temperatureCelsius).toList());
        exportTelemetryButton.setEnabled(true);
    }

    private void resetStats() {
        sampleStat.setText("-");
        cpuAverage.setText("-");
        cpuMaximum.setText("-");
        memoryAverage.setText("-");
        temperatureMaximum.setText("-");
        alertStat.setText("-");
        cpuChart.setValues(List.of());
        memoryChart.setValues(List.of());
        temperatureChart.setValues(List.of());
    }

    private void exportTelemetry() {
        if (telemetryRows.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("telemetry-report.csv"));
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this))
                != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            StringBuilder data = new StringBuilder(
                    "deviceId,collectedAt,receivedAt,cpuPercent,memoryPercent,temperatureCelsius\n");
            for (TelemetryView sample : telemetryRows) {
                data.append(sample.deviceId()).append(',')
                        .append(sample.collectedAt()).append(',')
                        .append(sample.receivedAt()).append(',')
                        .append(sample.cpuPercent()).append(',')
                        .append(sample.memoryPercent()).append(',')
                        .append(sample.temperatureCelsius()).append('\n');
            }
            Files.writeString(chooser.getSelectedFile().toPath(), data, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "指标历史已导出", "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    exception.getMessage(), "导出失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAlerts() {
        if (alertRows.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("alert-report.csv"));
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this))
                != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            StringBuilder data = new StringBuilder(
                    "alertId,deviceId,ruleType,severity,status,triggerValue,triggeredAt,"
                            + "resolvedAt,acknowledgedAt,acknowledgedBy,ackComment\n");
            for (AlertView alert : alertRows) {
                data.append(alert.alertId()).append(',')
                        .append(csv(alert.deviceId())).append(',')
                        .append(alert.ruleType()).append(',')
                        .append(alert.severity()).append(',')
                        .append(alert.status()).append(',')
                        .append(alert.triggerValue() == null ? "" : alert.triggerValue()).append(',')
                        .append(alert.triggeredAt()).append(',')
                        .append(alert.resolvedAt() == null ? "" : alert.resolvedAt()).append(',')
                        .append(alert.acknowledgedAt() == null ? "" : alert.acknowledgedAt()).append(',')
                        .append(csv(alert.acknowledgedBy())).append(',')
                        .append(csv(alert.ackComment())).append('\n');
            }
            Files.writeString(chooser.getSelectedFile().toPath(), data, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "告警历史已导出", "导出成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    exception.getMessage(), "导出失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPdf() {
        if (telemetryRows.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        String selected = (String) deviceSelector.getSelectedItem();
        String deviceId = selected == null || "全部设备".equals(selected)
                ? "全部设备"
                : selected;
        chooser.setSelectedFile(new File("device-report-" + deviceId + ".pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF 文档", "pdf"));
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this))
                != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selectedFile = chooser.getSelectedFile();
        final File target = selectedFile.getName()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".pdf")
                ? selectedFile
                : new File(selectedFile.getParentFile(), selectedFile.getName() + ".pdf");
        exportPdfButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<PdfReportExporter.Snapshot> snapshots = new ArrayList<>();
                SwingUtilities.invokeAndWait(() -> {
                    snapshots.add(new PdfReportExporter.Snapshot(
                            "CPU 使用率", PdfReportExporter.renderChart(cpuChart, 640, 360)));
                    snapshots.add(new PdfReportExporter.Snapshot(
                            "内存使用率", PdfReportExporter.renderChart(memoryChart, 640, 360)));
                    snapshots.add(new PdfReportExporter.Snapshot(
                            "温度趋势", PdfReportExporter.renderChart(temperatureChart, 640, 360)));
                });
                long hours = ((Number) hoursSpinner.getValue()).longValue();
                PdfReportExporter.write(
                        target.toPath(),
                        "设备运行历史报告",
                        List.of(
                                "设备：" + deviceId,
                                "时间范围：最近 " + hours + " 小时",
                                "生成时间："
                                        + LocalDateTime.now()
                                                .format(DateTimeFormatter.ofPattern(
                                                        "yyyy-MM-dd HH:mm:ss"))),
                        List.of(
                                "样本数：" + sampleStat.getText(),
                                "CPU 平均：" + cpuAverage.getText(),
                                "CPU 峰值：" + cpuMaximum.getText(),
                                "内存平均：" + memoryAverage.getText(),
                                "温度峰值：" + temperatureMaximum.getText(),
                                "告警条数：" + alertStat.getText()),
                        snapshots);
                return null;
            }

            @Override
            protected void done() {
                exportPdfButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ReportsPage.this),
                            "PDF 报告已导出", "导出成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    Throwable cause =
                            exception.getCause() == null ? exception : exception.getCause();
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(ReportsPage.this),
                            cause.getMessage(), "导出失败", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showQueryError(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                cause.getMessage(), "查询失败", JOptionPane.ERROR_MESSAGE);
    }

    private static String severityText(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> "严重";
            case "WARNING" -> "警告";
            case "INFO" -> "提示";
            default -> severity;
        };
    }

    private static String triggerText(AlertView alert) {
        if (alert.triggerValue() == null) {
            return "-";
        }
        return switch (alert.ruleType()) {
            case "CPU", "MEMORY" -> "%.1f%%".formatted(alert.triggerValue());
            case "TEMPERATURE" -> "%.1f °C".formatted(alert.triggerValue());
            case "OFFLINE" -> "%.1f 秒".formatted(alert.triggerValue());
            default -> "%.1f".formatted(alert.triggerValue());
        };
    }

    private static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static JLabel stat() {
        JLabel label = new JLabel("-");
        label.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 18f));
        return label;
    }

    private static JPanel statCard(String caption, JLabel value, Color accent) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 2));
        UiStyles.card(card, 10, 12);
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        value.setForeground(accent);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }
}
