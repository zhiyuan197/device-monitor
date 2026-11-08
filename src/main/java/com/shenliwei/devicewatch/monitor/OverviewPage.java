package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

final class OverviewPage extends PagePanel {
    private final JLabel totalValue = metricValue();
    private final JLabel onlineValue = metricValue();
    private final JLabel offlineValue = metricValue();
    private final JLabel alertValue = metricValue();
    private final JLabel unacknowledgedValue = metricValue();
    private final JLabel latencyValue = metricValue();
    private final MultiTrendChartPanel aggregateChart =
            new MultiTrendChartPanel("全网平均负载趋势", "%", 0, 100);
    private final DistributionPanel distributionPanel = new DistributionPanel();
    private final JTextField searchField = new JTextField();
    private final DeviceTableModel deviceModel = new DeviceTableModel();
    private final TableRowSorter<DeviceTableModel> sorter = new TableRowSorter<>(deviceModel);
    private final JTable deviceTable = new JTable(deviceModel);
    private final DeviceDetailPanel detailPanel;
    private final Deque<Double> averageCpu = new ArrayDeque<>();
    private final Deque<Double> averageMemory = new ArrayDeque<>();

    OverviewPage(DashboardFrame frame) {
        super(frame);
        detailPanel = new DeviceDetailPanel(frame);
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setBackground(UiStyles.BACKGROUND);

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(UiStyles.pageTitle("运行总览"));
        heading.add(titleBlock, BorderLayout.WEST);
        JPanel headerTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerTools.setOpaque(false);
        headerTools.add(UiStyles.chip("实时刷新", UiStyles.GREEN_SOFT,
                UiStyles.GREEN_TEXT));
        JButton demoControlButton = UiStyles.secondaryButton("演示控制");
        demoControlButton.setIcon(UiStyles.icon("zap", 14, UiStyles.MUTED));
        demoControlButton.setIconTextGap(6);
        demoControlButton.addActionListener(event -> openDemoControl());
        headerTools.add(demoControlButton);
        heading.add(headerTools, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(1, 6, 10, 0));
        metrics.setOpaque(false);
        metrics.add(UiStyles.metricCard("设备总数", totalValue, UiStyles.BLUE, 12, 14));
        metrics.add(UiStyles.metricCard("在线设备", onlineValue, UiStyles.GREEN, 12, 14));
        metrics.add(UiStyles.metricCard("离线设备", offlineValue, UiStyles.MUTED, 12, 14));
        metrics.add(UiStyles.metricCard("活动告警", alertValue, UiStyles.RED, 12, 14));
        metrics.add(UiStyles.metricCard("待确认", unacknowledgedValue, UiStyles.AMBER, 12, 14));
        metrics.add(UiStyles.metricCard("平均链路延迟", latencyValue, UiStyles.CYAN, 12, 14));
        top.add(metrics, BorderLayout.CENTER);

        JPanel middle = new JPanel(new GridLayout(1, 2, 14, 0));
        middle.setOpaque(false);
        JPanel chartCard = new JPanel();
        chartCard.setLayout(new BorderLayout());
        UiStyles.card(chartCard, 14, 16);
        chartCard.add(sectionHeader("全网负载聚合"), BorderLayout.NORTH);
        chartCard.add(aggregateChart, BorderLayout.CENTER);
        middle.add(chartCard);

        JPanel distributionCard = new JPanel();
        distributionCard.setLayout(new BorderLayout());
        UiStyles.card(distributionCard, 14, 16);
        distributionCard.add(sectionHeader("告警分布"), BorderLayout.NORTH);
        distributionCard.add(distributionPanel, BorderLayout.CENTER);
        middle.add(distributionCard);

        JPanel upper = new JPanel(new BorderLayout(0, 14));
        upper.setOpaque(false);
        upper.add(top, BorderLayout.NORTH);
        upper.add(middle, BorderLayout.CENTER);
        add(upper, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel tableHeader = new JPanel(new BorderLayout(8, 0));
        tableHeader.setOpaque(false);
        tableHeader.add(sectionHeader("设备列表"), BorderLayout.WEST);
        searchField.putClientProperty("JTextField.placeholderText", "搜索编号或名称");
        searchField.setPreferredSize(new Dimension(220, 30));
        tableHeader.add(searchField, BorderLayout.EAST);
        configureTable();

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(deviceTable), detailPanel);
        split.setResizeWeight(0.62);
        split.setDividerLocation(680);
        split.setBorder(null);
        split.setOpaque(false);
        JPanel splitCard = new JPanel();
        splitCard.setLayout(new BorderLayout());
        UiStyles.card(splitCard, 12, 14);
        splitCard.add(tableHeader, BorderLayout.NORTH);
        splitCard.add(split, BorderLayout.CENTER);
        bottom.add(splitCard, BorderLayout.CENTER);
        add(bottom, BorderLayout.CENTER);

        wireSearch();
    }

    private void openDemoControl() {
        JFrame owner = (JFrame) SwingUtilities.getWindowAncestor(this);
        DemoControlDialog dialog = new DemoControlDialog(owner, frame);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void configureTable() {
        deviceTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        deviceTable.setRowSorter(sorter);
        deviceTable.setFillsViewportHeight(true);
        deviceTable.setRowHeight(34);
        deviceTable.setShowGrid(false);
        deviceTable.setIntercellSpacing(new Dimension(0, 0));
        deviceTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedDevice();
            }
        });
        deviceTable.getColumnModel().getColumn(1).setCellRenderer(nameRenderer());
        deviceTable.getColumnModel().getColumn(2).setCellRenderer(chipRenderer());
    }

    private void wireSearch() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyFilter();
            }
        });
    }

    private void applyFilter() {
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DeviceTableModel, ? extends Integer> entry) {
                if (search.isEmpty()) {
                    return true;
                }
                DeviceView device = deviceModel.deviceAt(entry.getIdentifier());
                return device.deviceId().toLowerCase(Locale.ROOT).contains(search)
                        || device.displayName().toLowerCase(Locale.ROOT).contains(search);
            }
        });
    }

    private void showSelectedDevice() {
        int viewRow = deviceTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        DeviceView device = deviceModel.deviceAt(deviceTable.convertRowIndexToModel(viewRow));
        detailPanel.show(device);
    }

    @Override
    void onDevicesChanged() {
        List<DeviceView> rows = frame.deviceList();
        String selected = detailPanel.deviceId();
        deviceModel.replaceAll(rows);
        applyFilter();

        List<DeviceView> online = rows.stream().filter(DeviceView::online).toList();
        if (!online.isEmpty()) {
            double cpu = online.stream()
                    .mapToDouble(device -> device.cpuPercent() == null ? 0 : device.cpuPercent())
                    .average().orElse(0);
            double memory = online.stream()
                    .mapToDouble(device -> device.memoryPercent() == null ? 0 : device.memoryPercent())
                    .average().orElse(0);
            averageCpu.addLast(cpu);
            averageMemory.addLast(memory);
            while (averageCpu.size() > DashboardFrame.TREND_LIMIT) {
                averageCpu.removeFirst();
                averageMemory.removeFirst();
            }
            aggregateChart.setSeries(List.of(
                    new MultiTrendChartPanel.Series("CPU 平均", UiStyles.BLUE,
                            new ArrayList<>(averageCpu)),
                    new MultiTrendChartPanel.Series("内存平均", UiStyles.CYAN,
                            new ArrayList<>(averageMemory))));
        }

        long onlineCount = online.size();
        long offlineCount = rows.size() - onlineCount;
        totalValue.setText(Integer.toString(rows.size()));
        onlineValue.setText(Long.toString(onlineCount));
        offlineValue.setText(Long.toString(offlineCount));
        latencyValue.setText(averageLatencyMillis(online) + " ms");
        refreshAlerts();

        if (selected != null) {
            frame.devices.values().stream()
                    .filter(device -> device.deviceId().equals(selected))
                    .findFirst()
                    .ifPresentOrElse(
                            detailPanel::show,
                            () -> selectFirstDevice());
        } else {
            selectFirstDevice();
        }
    }

    private void selectFirstDevice() {
        if (deviceModel.getRowCount() > 0) {
            deviceTable.getSelectionModel().setSelectionInterval(0, 0);
            detailPanel.show(deviceModel.deviceAt(0));
        }
    }

    @Override
    void onAlertsChanged() {
        refreshAlerts();
    }

    private void refreshAlerts() {
        List<AlertView> alerts = frame.alertList();
        long unacknowledged = alerts.stream()
                .filter(alert -> alert.acknowledgedAt() == null)
                .count();
        alertValue.setText(Integer.toString(alerts.size()));
        unacknowledgedValue.setText(Long.toString(unacknowledged));
        distributionPanel.setAlerts(alerts);
    }

    static long averageLatencyMillis(List<DeviceView> devices) {
        List<Long> known = devices.stream()
                .map(DeviceView::latencyMillis)
                .filter(latency -> latency > 0)
                .toList();
        if (known.isEmpty()) {
            return 0;
        }
        return Math.round(known.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("0");
        label.setFont(UiStyles.METRIC);
        return label;
    }

    private static JPanel sectionHeader(String title) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UiStyles.SECTION);
        titleLabel.setForeground(UiStyles.TEXT);
        panel.add(titleLabel);
        return panel;
    }

    private static DefaultTableCellRenderer chipRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                DeviceView device = (DeviceView) value;
                JLabel chip = UiStyles.statusChip(device.online(), device.enabled());
                chip.setHorizontalAlignment(JLabel.CENTER);
                if (selected) {
                    chip.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UiStyles.BLUE),
                            BorderFactory.createEmptyBorder(2, 9, 2, 9)));
                }
                return chip;
            }
        };
    }

    private static DefaultTableCellRenderer nameRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                DeviceView device = (DeviceView) value;
                JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                holder.setOpaque(false);
                JLabel name = new JLabel(device.displayName());
                name.setFont(UiStyles.FONT);
                name.setForeground(UiStyles.TEXT);
                holder.add(name);
                if (device.real()) {
                    holder.add(UiStyles.chip("本机", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT));
                }
                return holder;
            }
        };
    }

    private static final class DeviceTableModel extends AbstractTableModel {
        private final String[] columns = {
            "设备编号", "名称", "状态", "位置", "CPU", "内存", "温度", "最后上报"
        };
        private List<DeviceView> rows = List.of();

        void replaceAll(List<DeviceView> values) {
            rows = values;
            fireTableDataChanged();
        }

        DeviceView deviceAt(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 || columnIndex == 2 ? DeviceView.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DeviceView device = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> device.deviceId();
                case 1 -> device;
                case 2 -> device;
                case 3 -> device.location().isBlank() ? "-" : device.location();
                case 4 -> UiStyles.formatPercent(device.cpuPercent());
                case 5 -> UiStyles.formatPercent(device.memoryPercent());
                case 6 -> UiStyles.formatTemperature(device.temperatureCelsius());
                case 7 -> UiStyles.formatShortTime(device.lastSeenAt());
                default -> "";
            };
        }
    }

    /**
     * Horizontal bar list showing active alert counts by rule type and severity.
     */
    private static final class DistributionPanel extends JPanel {
        private List<AlertView> alerts = List.of();

        DistributionPanel() {
            setOpaque(false);
        }

        void setAlerts(List<AlertView> values) {
            alerts = values == null ? List.of() : values;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                record Entry(String label, long count, Color color) {
                }
                List<Entry> entries = List.of(
                        new Entry("CPU 过高", count("CPU"), UiStyles.RED),
                        new Entry("内存过高", count("MEMORY"), UiStyles.CYAN),
                        new Entry("温度过高", count("TEMPERATURE"), UiStyles.AMBER),
                        new Entry("设备离线", count("OFFLINE"), UiStyles.MUTED));
                long total = Math.max(1, alerts.size());
                int y = 14;
                int rowHeight = 44;
                for (Entry entry : entries) {
                    g.setFont(UiStyles.FONT.deriveFont(Font.BOLD, 12.5f));
                    g.setColor(UiStyles.TEXT);
                    g.drawString(entry.label(), 10, y + 14);
                    String countText = Long.toString(entry.count());
                    java.awt.FontMetrics metrics = g.getFontMetrics();
                    g.drawString(countText, getWidth() - metrics.stringWidth(countText) - 8, y + 14);

                    int barY = y + 23;
                    int barHeight = 10;
                    g.setColor(UiStyles.BORDER.brighter());
                    g.fillRoundRect(10, barY, getWidth() - 70, barHeight, 10, 10);
                    int fill = (int) Math.round((getWidth() - 70) * entry.count() / (double) total);
                    if (fill > 2) {
                        g.setColor(entry.color());
                        g.fillRoundRect(10, barY, Math.min(fill, getWidth() - 70), barHeight, 10, 10);
                    }
                    y += rowHeight;
                }
                g.setColor(UiStyles.MUTED);
                g.setFont(UiStyles.SMALL.deriveFont(11f));
                g.drawString("共 " + alerts.size() + " 条活动告警", 10, y + 12);
            } finally {
                g.dispose();
            }
        }

        private long count(String ruleType) {
            return alerts.stream().filter(alert -> ruleType.equals(alert.ruleType())).count();
        }
    }
}
