package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.ArrayDeque;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Live detail card for one device: metrics, trend charts and quick actions.
 */
final class DeviceDetailPanel extends JPanel {
    private final DashboardFrame frame;
    private final JLabel title = new JLabel("请选择设备");
    private final JLabel statusChipHolder = new JLabel();
    private final JLabel location = detailValue();
    private final JLabel source = detailValue();
    private final JLabel cpu = detailValue();
    private final JLabel memory = detailValue();
    private final JLabel temperature = detailValue();
    private final JLabel disk = detailValue();
    private final JLabel throughput = detailValue();
    private final JLabel latency = detailValue();
    private final JLabel lastSeen = detailValue();
    private final TrendChartPanel cpuTrend =
            new TrendChartPanel("CPU 使用率", "%", 0, 100, UiStyles.BLUE, 80.0);
    private final TrendChartPanel memoryTrend =
            new TrendChartPanel("内存使用率", "%", 0, 100, UiStyles.CYAN, 85.0);
    private final TrendChartPanel latencyTrend =
            new TrendChartPanel("上报延迟", "ms", 0, 5000, UiStyles.BLUE, 1000.0);
    private final TrendChartPanel diskTrend =
            new TrendChartPanel("磁盘使用率", "%", 0, 100, UiStyles.AMBER, 90.0);
    private String deviceId;

    DeviceDetailPanel(DashboardFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setOpaque(false);
        setMinimumSize(new java.awt.Dimension(320, 0));
        for (TrendChartPanel chart : new TrendChartPanel[] {
            cpuTrend, memoryTrend, latencyTrend, diskTrend
        }) {
            chart.setPreferredSize(new java.awt.Dimension(0, 200));
        }

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        header.add(title, BorderLayout.WEST);
        header.add(statusChipHolder, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        JPanel facts = new JPanel(new GridLayout(0, 2, 12, 8));
        facts.setOpaque(false);
        facts.add(fieldBlock("数据来源", source));
        facts.add(fieldBlock("所在位置", location));
        facts.add(fieldBlock("CPU 使用率", cpu));
        facts.add(fieldBlock("内存使用率", memory));
        facts.add(fieldBlock("设备温度", temperature));
        facts.add(fieldBlock("磁盘使用率", disk));
        facts.add(fieldBlock("上报吞吐", throughput));
        facts.add(fieldBlock("上报延迟", latency));
        body.add(facts, BorderLayout.NORTH);

        JPanel lastSeenRow = new JPanel(new BorderLayout());
        lastSeenRow.setOpaque(false);
        JLabel lastSeenCaption = new JLabel("最后上报时间");
        lastSeenCaption.setForeground(UiStyles.MUTED);
        lastSeenCaption.setFont(UiStyles.SMALL);
        lastSeen.setHorizontalAlignment(JLabel.RIGHT);
        lastSeenRow.add(lastSeenCaption, BorderLayout.WEST);
        lastSeenRow.add(lastSeen, BorderLayout.EAST);
        body.add(lastSeenRow, BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);

        JPanel charts = new JPanel(new GridLayout(1, 1, 0, 0));
        charts.setOpaque(false);
        JPanel chartPair = new JPanel(new GridLayout(0, 1, 0, 8));
        chartPair.setOpaque(false);
        chartPair.add(cpuTrend);
        chartPair.add(memoryTrend);
        chartPair.add(diskTrend);
        chartPair.add(latencyTrend);
        charts.add(chartPair);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton historyButton = UiStyles.primaryButton("历史报告");
        historyButton.addActionListener(event -> {
            if (deviceId != null) {
                frame.openReports(deviceId);
            }
        });
        JButton devicesButton = UiStyles.secondaryButton("设备管理");
        devicesButton.addActionListener(event -> frame.showDevicesPage());
        actions.add(historyButton);
        actions.add(devicesButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(charts, BorderLayout.CENTER);
        south.add(actions, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    void show(DeviceView device) {
        if (device == null) {
            deviceId = null;
            title.setText("请选择设备");
            statusChipHolder.removeAll();
            statusChipHolder.revalidate();
            source.setText("-");
            source.setForeground(UiStyles.MUTED);
            location.setText("-");
            cpu.setText("-");
            memory.setText("-");
            temperature.setText("-");
            disk.setText("-");
            throughput.setText("-");
            latency.setText("-");
            lastSeen.setText("-");
            cpuTrend.setValues(java.util.List.of());
            memoryTrend.setValues(java.util.List.of());
            latencyTrend.setValues(java.util.List.of());
            diskTrend.setValues(java.util.List.of());
            repaint();
            return;
        }
        deviceId = device.deviceId();
        title.setText(device.deviceId() + "  ·  " + device.displayName());
        statusChipHolder.removeAll();
        statusChipHolder.add(UiStyles.statusChip(device.online(), device.enabled()));
        statusChipHolder.setLayout(new BoxLayout(statusChipHolder, BoxLayout.X_AXIS));
        statusChipHolder.revalidate();
        source.setText(device.real() ? "本机真实数据" : "模拟数据");
        source.setForeground(device.real() ? UiStyles.BLUE : UiStyles.MUTED);
        location.setText(device.location().isBlank() ? "未设置" : device.location());
        cpu.setText(UiStyles.formatPercent(device.cpuPercent()));
        memory.setText(UiStyles.formatPercent(device.memoryPercent()));
        temperature.setText(UiStyles.formatTemperature(device.temperatureCelsius()));
        disk.setText(device.diskPercent() == null ? "-" : UiStyles.formatPercent(device.diskPercent()));
        throughput.setText(device.networkKbps() > 0
                ? String.format(java.util.Locale.ROOT, "%.1f Kbps", device.networkKbps())
                : "-");
        latency.setText(device.latencyMillis() > 0
                ? UiStyles.formatNumber(device.latencyMillis()) + " ms"
                : "-");
        lastSeen.setText(UiStyles.formatTime(device.lastSeenAt()));
        cpuTrend.setValues(new ArrayList<>(frame.cpuTrends.getOrDefault(
                device.deviceId(), new ArrayDeque<>())));
        memoryTrend.setValues(new ArrayList<>(frame.memoryTrends.getOrDefault(
                device.deviceId(), new ArrayDeque<>())));
        latencyTrend.setValues(new ArrayList<>(frame.latencyTrends.getOrDefault(
                device.deviceId(), new ArrayDeque<>())));
        diskTrend.setValues(new ArrayList<>(frame.diskTrends.getOrDefault(
                device.deviceId(), new ArrayDeque<>())));
        revalidate();
        repaint();
    }

    String deviceId() {
        return deviceId;
    }

    private static JPanel fieldBlock(String caption, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        value.setForeground(UiStyles.TEXT);
        value.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 14f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel detailValue() {
        JLabel label = new JLabel("-");
        label.setForeground(UiStyles.TEXT);
        label.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 14f));
        return label;
    }
}
