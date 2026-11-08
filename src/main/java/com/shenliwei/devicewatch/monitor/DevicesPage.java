package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class DevicesPage extends PagePanel {
    private final DeviceTableModel deviceModel = new DeviceTableModel();
    private final JTable deviceTable = new JTable(deviceModel);
    private final JButton registerButton = UiStyles.primaryButton("注册设备");
    private final JButton editButton = UiStyles.secondaryButton("编辑信息");
    private final JButton toggleButton = UiStyles.secondaryButton("停用 / 启用");
    private final JButton credentialButton = UiStyles.secondaryButton("重置凭证");
    private final JButton rulesButton = UiStyles.secondaryButton("告警规则");
    private final DeviceDetailPanel detailPanel;

    DevicesPage(DashboardFrame frame) {
        super(frame);
        registerButton.setIcon(UiStyles.icon("plus", 14, java.awt.Color.WHITE));
        registerButton.setIconTextGap(6);
        detailPanel = new DeviceDetailPanel(frame);
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setBackground(UiStyles.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(UiStyles.pageTitle("设备管理"));
        heading.add(titleBlock, BorderLayout.WEST);
        add(heading, BorderLayout.NORTH);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tools.setOpaque(false);
        registerButton.addActionListener(event -> openRegisterDialog());
        editButton.addActionListener(event -> openEditDialog());
        toggleButton.addActionListener(event -> toggleSelected());
        credentialButton.addActionListener(event -> resetCredential());
        rulesButton.addActionListener(event -> openRulesDialog());
        editButton.setEnabled(false);
        toggleButton.setEnabled(false);
        credentialButton.setEnabled(false);
        rulesButton.setEnabled(false);
        tools.add(registerButton);
        tools.add(editButton);
        tools.add(toggleButton);
        tools.add(credentialButton);
        tools.add(rulesButton);
        add(tools, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setOpaque(false);
        JPanel tableCard = new JPanel();
        tableCard.setLayout(new BorderLayout());
        UiStyles.card(tableCard, 10, 12);
        configureTable();
        tableCard.add(new JScrollPane(deviceTable), BorderLayout.CENTER);
        content.add(tableCard, BorderLayout.CENTER);

        JPanel detailCard = new JPanel();
        detailCard.setLayout(new BorderLayout());
        UiStyles.card(detailCard, 12, 14);
        detailCard.setPreferredSize(new Dimension(360, 0));
        detailCard.add(detailPanel, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, tableCard, detailCard);
        split.setResizeWeight(0.62);
        split.setDividerLocation(500);
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        content.add(split, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    private void configureTable() {
        deviceTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        deviceTable.setFillsViewportHeight(true);
        deviceTable.setRowHeight(34);
        deviceTable.setShowGrid(false);
        deviceTable.setIntercellSpacing(new Dimension(0, 0));
        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.getColumnModel().getColumn(2).setCellRenderer(statusRenderer());
        deviceTable.getColumnModel().getColumn(4).setCellRenderer(enabledRenderer());
        deviceTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateSelection();
            }
        });
    }

    private void updateSelection() {
        DeviceView device = selectedDevice();
        detailPanel.show(device);
        boolean present = device != null;
        editButton.setEnabled(present);
        toggleButton.setEnabled(present);
        credentialButton.setEnabled(present);
        rulesButton.setEnabled(present);
        if (present) {
            toggleButton.setText(device.enabled() ? "停用设备" : "启用设备");
        }
    }

    private DeviceView selectedDevice() {
        int viewRow = deviceTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return deviceModel.deviceAt(deviceTable.convertRowIndexToModel(viewRow));
    }

    private void openRegisterDialog() {
        DeviceFormDialog.Result result = DeviceFormDialog.showRegister(
                SwingUtilities.getWindowAncestor(this));
        if (result == null) {
            return;
        }
        frame.client().registerDevice(
                        result.deviceId(), result.displayName(), result.location())
                .whenComplete((registration, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null) {
                                showError("设备注册失败", error);
                                return;
                            }
                            CredentialDialog.show(
                                    SwingUtilities.getWindowAncestor(this),
                                    registration.device().deviceId(),
                                    registration.credential());
                        }));
    }

    private void openEditDialog() {
        DeviceView device = selectedDevice();
        if (device == null) {
            return;
        }
        DeviceFormDialog.Result result = DeviceFormDialog.showEdit(
                SwingUtilities.getWindowAncestor(this), device);
        if (result == null) {
            return;
        }
        frame.client().updateDevice(
                        device.deviceId(), result.displayName(), result.location())
                .whenComplete((updated, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null) {
                                showError("设备信息更新失败", error);
                            }
                        }));
    }

    private void toggleSelected() {
        DeviceView device = selectedDevice();
        if (device == null) {
            return;
        }
        boolean enable = !device.enabled();
        String action = enable ? "启用" : "停用";
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "确定" + action + "设备 " + device.deviceId() + " 吗？"
                        + (enable ? "" : "\n停用后设备将断开连接并无法再上报数据。"),
                action + "设备",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        frame.client().setDeviceEnabled(device.deviceId(), enable)
                .whenComplete((updated, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null) {
                                showError(action + "设备失败", error);
                            }
                        }));
    }

    private void resetCredential() {
        DeviceView device = selectedDevice();
        if (device == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "重新签发设备 " + device.deviceId() + " 的接入凭证？\n旧凭证将立即失效。",
                "重置凭证",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        frame.client().resetDeviceCredential(device.deviceId())
                .whenComplete((credential, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null) {
                                showError("凭证重置失败", error);
                                return;
                            }
                            CredentialDialog.show(
                                    SwingUtilities.getWindowAncestor(this),
                                    device.deviceId(),
                                    credential);
                        }));
    }

    private void openRulesDialog() {
        DeviceView device = selectedDevice();
        if (device == null) {
            return;
        }
        DeviceRulesView rules = RulesDialog.show(
                SwingUtilities.getWindowAncestor(this), device);
        if (rules == null) {
            return;
        }
        frame.client().updateDeviceRules(device.deviceId(), rules)
                .whenComplete((updated, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null) {
                                showError("告警规则更新失败", error);
                            }
                        }));
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
    void onDevicesChanged() {
        String selected = detailPanel.deviceId();
        deviceModel.replaceAll(frame.deviceList());
        if (selected != null) {
            for (int i = 0; i < deviceModel.getRowCount(); i++) {
                if (deviceModel.deviceAt(i).deviceId().equals(selected)) {
                    deviceTable.getSelectionModel().setSelectionInterval(i, i);
                    break;
                }
            }
        }
        if (selectedDevice() == null && deviceModel.getRowCount() > 0) {
            deviceTable.getSelectionModel().setSelectionInterval(0, 0);
        }
        updateSelection();
    }

    private static DefaultTableCellRenderer statusRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                DeviceView device = (DeviceView) value;
                JLabel chip = UiStyles.statusChip(device.online(), device.enabled());
                chip.setHorizontalAlignment(JLabel.CENTER);
                return chip;
            }
        };
    }

    private static DefaultTableCellRenderer enabledRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                boolean enabled = (Boolean) value;
                return UiStyles.chip(enabled ? "启用" : "停用",
                        enabled ? UiStyles.GREEN_SOFT : UiStyles.NEUTRAL,
                        enabled ? UiStyles.GREEN_TEXT : UiStyles.TEXT);
            }
        };
    }

    private static final class DeviceTableModel extends AbstractTableModel {
        private final String[] columns = {
            "设备编号", "名称", "状态", "位置", "启用", "CPU", "内存", "温度", "最后上报"
        };
        private List<DeviceView> rows = List.of();

        void replaceAll(List<DeviceView> values) {
            rows = values.stream()
                    .sorted(Comparator.comparing(DeviceView::deviceId))
                    .toList();
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
            return switch (columnIndex) {
                case 2 -> DeviceView.class;
                case 4 -> Boolean.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DeviceView device = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> device.deviceId();
                case 1 -> device.displayName();
                case 2 -> device;
                case 3 -> device.location().isBlank() ? "-" : device.location();
                case 4 -> device.enabled();
                case 5 -> UiStyles.formatPercent(device.cpuPercent());
                case 6 -> UiStyles.formatPercent(device.memoryPercent());
                case 7 -> UiStyles.formatTemperature(device.temperatureCelsius());
                case 8 -> UiStyles.formatShortTime(device.lastSeenAt());
                default -> "";
            };
        }
    }
}
