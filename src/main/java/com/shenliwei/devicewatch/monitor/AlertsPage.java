package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

final class AlertsPage extends PagePanel {
    private final JLabel activeLabel = metric("0");
    private final JLabel waitingLabel = metric("0");
    private final JLabel criticalLabel = metric("0");
    private final JComboBox<String> typeFilter =
            new JComboBox<>(new String[] {"全部类型", "CPU 过高", "内存过高", "温度过高", "设备离线"});
    private final JComboBox<String> severityFilter =
            new JComboBox<>(new String[] {"全部级别", "严重", "警告", "提示"});
    private final JComboBox<String> stateFilter =
            new JComboBox<>(new String[] {"全部状态", "待确认", "已确认"});
    private final AlertTableModel alertModel = new AlertTableModel();
    private final TableRowSorter<AlertTableModel> sorter = new TableRowSorter<>(alertModel);
    private final JTable alertTable = new JTable(alertModel);
    private final JButton acknowledgeButton = UiStyles.primaryButton("确认并备注");
    private final JButton resolveButton = UiStyles.secondaryButton("标记解决");
    private final JButton historyButton = UiStyles.secondaryButton("查看设备历史");
    private final AlertDetailPanel detailPanel = new AlertDetailPanel();

    AlertsPage(DashboardFrame frame) {
        super(frame);
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setBackground(UiStyles.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(UiStyles.pageTitle("告警中心"));
        heading.add(titleBlock, BorderLayout.WEST);

        JPanel metrics = new JPanel(new GridLayout(1, 3, 12, 0));
        metrics.setOpaque(false);
        metrics.add(UiStyles.metricCard("活动告警", activeLabel, UiStyles.RED, 10, 14));
        metrics.add(UiStyles.metricCard("待确认", waitingLabel, UiStyles.AMBER, 10, 14));
        metrics.add(
                UiStyles.metricCard("严重告警", criticalLabel, new Color(190, 18, 60), 10, 14));
        heading.add(metrics, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        tools.add(new JLabel("类型"));
        tools.add(typeFilter);
        tools.add(new JLabel("级别"));
        tools.add(severityFilter);
        tools.add(new JLabel("状态"));
        tools.add(stateFilter);
        tools.add(Box.createHorizontalStrut(12));
        acknowledgeButton.addActionListener(event -> handleAcknowledge());
        resolveButton.addActionListener(event -> handleResolve());
        historyButton.addActionListener(event -> openHistory());
        acknowledgeButton.setEnabled(false);
        resolveButton.setEnabled(false);
        historyButton.setEnabled(false);
        tools.add(acknowledgeButton);
        tools.add(resolveButton);
        tools.add(historyButton);
        add(tools, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setOpaque(false);
        JPanel tableCard = new JPanel();
        tableCard.setLayout(new BorderLayout());
        UiStyles.card(tableCard, 10, 12);
        configureTable();
        tableCard.add(new JScrollPane(alertTable), BorderLayout.CENTER);
        content.add(tableCard, BorderLayout.CENTER);
        JPanel detailCard = new JPanel();
        detailCard.setLayout(new BorderLayout());
        UiStyles.card(detailCard, 12, 14);
        detailCard.setPreferredSize(new Dimension(330, 0));
        detailCard.add(detailPanel, BorderLayout.CENTER);
        content.add(detailCard, BorderLayout.EAST);
        add(content, BorderLayout.CENTER);

        typeFilter.addActionListener(event -> applyFilters());
        severityFilter.addActionListener(event -> applyFilters());
        stateFilter.addActionListener(event -> applyFilters());
        alertTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelection();
            }
        });
    }

    private void configureTable() {
        alertTable.setRowSorter(sorter);
        alertTable.setFillsViewportHeight(true);
        alertTable.setRowHeight(36);
        alertTable.setShowGrid(false);
        alertTable.setIntercellSpacing(new Dimension(0, 0));
        alertTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alertTable.getColumnModel().getColumn(0).setCellRenderer(severityRenderer());
        alertTable.getColumnModel().getColumn(1).setCellRenderer(ruleRenderer());
        alertTable.getColumnModel().getColumn(4).setCellRenderer(ackRenderer());
    }

    private void applyFilters() {
        String rule = switch (typeFilter.getSelectedIndex()) {
            case 1 -> "CPU";
            case 2 -> "MEMORY";
            case 3 -> "TEMPERATURE";
            case 4 -> "OFFLINE";
            default -> "";
        };
        String severity = switch (severityFilter.getSelectedIndex()) {
            case 1 -> "CRITICAL";
            case 2 -> "WARNING";
            case 3 -> "INFO";
            default -> "";
        };
        int state = stateFilter.getSelectedIndex();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends AlertTableModel, ? extends Integer> entry) {
                AlertView alert = alertModel.alertAt(entry.getIdentifier());
                if (!rule.isEmpty() && !rule.equals(alert.ruleType())) {
                    return false;
                }
                if (!severity.isEmpty() && !severity.equals(alert.severity())) {
                    return false;
                }
                if (state == 1 && alert.acknowledgedAt() != null) {
                    return false;
                }
                if (state == 2 && alert.acknowledgedAt() == null) {
                    return false;
                }
                return true;
            }
        });
    }

    private void updateSelection() {
        AlertView alert = selectedAlert();
        detailPanel.show(alert);
        acknowledgeButton.setEnabled(alert != null && alert.acknowledgedAt() == null);
        resolveButton.setEnabled(alert != null);
        historyButton.setEnabled(alert != null);
    }

    private AlertView selectedAlert() {
        int viewRow = alertTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return alertModel.alertAt(alertTable.convertRowIndexToModel(viewRow));
    }

    private void handleAcknowledge() {
        AlertView alert = selectedAlert();
        if (alert == null || alert.acknowledgedAt() != null) {
            return;
        }
        String comment = AlertActionDialog.ask(
                SwingUtilities.getWindowAncestor(this),
                "确认告警",
                "确认设备 " + alert.deviceId() + " 的告警（可选填备注）：");
        if (comment == null) {
            return;
        }
        acknowledgeButton.setEnabled(false);
        frame.client().acknowledgeAlert(alert.alertId(), comment).whenComplete((updated, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        showError("告警确认失败", error);
                    } else {
                        frame.onAlertUpdate(updated);
                    }
                    updateSelection();
                }));
    }

    private void handleResolve() {
        AlertView alert = selectedAlert();
        if (alert == null) {
            return;
        }
        String comment = AlertActionDialog.ask(
                SwingUtilities.getWindowAncestor(this),
                "解决告警",
                "将设备 " + alert.deviceId() + " 的告警标记为已解决（可选填说明）：");
        if (comment == null) {
            return;
        }
        resolveButton.setEnabled(false);
        frame.client().resolveAlert(alert.alertId(), comment).whenComplete((updated, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        showError("告警解决失败", error);
                    } else {
                        frame.onAlertUpdate(updated);
                    }
                    updateSelection();
                }));
    }

    private void openHistory() {
        AlertView alert = selectedAlert();
        if (alert != null) {
            frame.openReports(alert.deviceId());
        }
    }

    private void showError(String title, Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                cause.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    void onAlertsChanged() {
        alertModel.replaceAll(frame.alertList());
        applyFilters();
        long waiting = frame.alertList().stream()
                .filter(alert -> alert.acknowledgedAt() == null)
                .count();
        long critical = frame.alertList().stream()
                .filter(alert -> "CRITICAL".equals(alert.severity()))
                .count();
        activeLabel.setText(Integer.toString(frame.alertList().size()));
        waitingLabel.setText(Long.toString(waiting));
        criticalLabel.setText(Long.toString(critical));
        if (selectedAlert() == null && alertModel.getRowCount() > 0) {
            alertTable.getSelectionModel().setSelectionInterval(0, 0);
        }
        updateSelection();
    }

    private static JLabel metric(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiStyles.METRIC.deriveFont(24f));
        return label;
    }

    private static DefaultTableCellRenderer severityRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                return UiStyles.severityChip((String) value);
            }
        };
    }

    private static DefaultTableCellRenderer ruleRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                AlertView alert = (AlertView) value;
                String label = alertLabel(alert.ruleType());
                JLabel component = (JLabel) super.getTableCellRendererComponent(
                        table, label, selected, focused, row, column);
                return component;
            }
        };
    }

    private static DefaultTableCellRenderer ackRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                AlertView alert = (AlertView) value;
                boolean acknowledged = alert.acknowledgedAt() != null;
                JLabel chip = acknowledged
                        ? UiStyles.chip("已确认", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT)
                        : UiStyles.chip("待确认", UiStyles.AMBER_SOFT, UiStyles.AMBER_TEXT);
                return chip;
            }
        };
    }

    static String alertLabel(String rule) {
        return switch (rule) {
            case "CPU" -> "CPU 过高";
            case "MEMORY" -> "内存过高";
            case "TEMPERATURE" -> "温度过高";
            case "OFFLINE" -> "设备离线";
            case "SERVICE_DOWN" -> "服务故障";
            default -> rule;
        };
    }

    private static final class AlertTableModel extends AbstractTableModel {
        private final String[] columns = {"级别", "类型", "设备", "触发值", "确认状态", "触发时间"};
        private List<AlertView> rows = List.of();

        void replaceAll(List<AlertView> values) {
            rows = values.stream()
                    .sorted(Comparator.comparingLong(AlertView::triggeredAt).reversed())
                    .toList();
            fireTableDataChanged();
        }

        AlertView alertAt(int row) {
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
            return switch (columnIndex) {
                case 0, 4 -> String.class;
                case 1 -> AlertView.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AlertView alert = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> alert.severity();
                case 1 -> alert;
                case 2 -> alert.deviceId();
                case 3 -> triggerText(alert);
                case 4 -> alert;
                case 5 -> UiStyles.formatTime(alert.triggeredAt());
                default -> "";
            };
        }

        static String triggerText(AlertView alert) {
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
    }

    private static final class AlertDetailPanel extends JPanel {
        private final JLabel rule = detailValue();
        private final JLabel device = detailValue();
        private final JLabel trigger = detailValue();
        private final JLabel triggeredAt = detailValue();
        private final JLabel ackState = detailValue();
        private final JLabel ackBy = detailValue();
        private final JLabel ackComment = new JLabel("<html>-</html>");

        AlertDetailPanel() {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            JLabel header = UiStyles.pageTitle("告警详情");
            header.setFont(UiStyles.SECTION);
            add(header, BorderLayout.NORTH);

            JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
            fields.setOpaque(false);
            fields.add(field("告警类型", rule));
            fields.add(field("设备", device));
            fields.add(field("触发值", trigger));
            fields.add(field("触发时间", triggeredAt));
            fields.add(field("确认状态", ackState));
            fields.add(field("确认人", ackBy));
            JPanel commentBlock = new JPanel(new BorderLayout(0, 3));
            commentBlock.setOpaque(false);
            JLabel commentCaption = new JLabel("处理备注");
            commentCaption.setForeground(UiStyles.MUTED);
            commentCaption.setFont(UiStyles.SMALL);
            ackComment.setForeground(UiStyles.TEXT);
            ackComment.setFont(UiStyles.FONT);
            commentBlock.add(commentCaption, BorderLayout.NORTH);
            commentBlock.add(ackComment, BorderLayout.CENTER);
            fields.add(commentBlock);
            add(fields, BorderLayout.CENTER);
        }

        void show(AlertView alert) {
            if (alert == null) {
                rule.setText("-");
                device.setText("-");
                trigger.setText("-");
                triggeredAt.setText("-");
                ackState.setText("-");
                ackBy.setText("-");
                ackComment.setText("<html>-</html>");
                return;
            }
            rule.setText(AlertsPage.alertLabel(alert.ruleType()) + "  ·  " + alert.severity());
            device.setText(alert.deviceId());
            trigger.setText(AlertTableModel.triggerText(alert));
            triggeredAt.setText(UiStyles.formatTime(alert.triggeredAt()));
            ackState.setText(alert.acknowledgedAt() == null ? "待确认" : "已确认");
            ackBy.setText(alert.acknowledgedBy() == null || alert.acknowledgedBy().isBlank()
                    ? "-"
                    : alert.acknowledgedBy());
            ackComment.setText("<html>" + (alert.ackComment() == null
                    || alert.ackComment().isBlank() ? "无" : alert.ackComment()) + "</html>");
        }

        private static JPanel field(String caption, JLabel value) {
            JPanel panel = new JPanel(new BorderLayout(0, 2));
            panel.setOpaque(false);
            JLabel label = new JLabel(caption);
            label.setForeground(UiStyles.MUTED);
            label.setFont(UiStyles.SMALL);
            value.setForeground(UiStyles.TEXT);
            value.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 13f));
            panel.add(label, BorderLayout.NORTH);
            panel.add(value, BorderLayout.CENTER);
            return panel;
        }

        private static JLabel detailValue() {
            JLabel label = new JLabel("-");
            label.setForeground(UiStyles.TEXT);
            label.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 13f));
            return label;
        }
    }
}
