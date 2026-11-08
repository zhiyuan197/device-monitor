package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

final class SystemPage extends PagePanel {
    private final JLabel hostValue = value();
    private final JLabel uptimeValue = value();
    private final JLabel sessionsValue = value();
    private final JLabel devicesValue = value();
    private final JLabel telemetryValue = value();
    private final JLabel alertsValue = value();
    private final JLabel databaseSizeValue = value();
    private final JLabel databasePathValue = value();
    private final JLabel tlsValue = value();
    private final JButton refreshStatusButton = UiStyles.secondaryButton("刷新状态");
    private final JButton reconnectButton = UiStyles.secondaryButton("重新连接");
    private final JButton logoutButton = UiStyles.dangerButton("退出登录");
    private final AuditTableModel auditModel = new AuditTableModel();
    private final JTable auditTable = new JTable(auditModel);
    private final JButton refreshAuditButton = UiStyles.secondaryButton("刷新审计日志");
    private final JPasswordField oldPassword = new JPasswordField(16);
    private final JPasswordField newPassword = new JPasswordField(16);
    private final JPasswordField confirmPassword = new JPasswordField(16);
    private final JButton changePasswordButton = UiStyles.primaryButton("修改密码");
    private final DefaultTableModel serviceModel = new DefaultTableModel(
            new String[] {"名称", "目标", "状态", "可用率", "最后检查"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable serviceTable = new JTable(serviceModel);
    private final JButton addServiceButton = UiStyles.primaryButton("添加检查");
    private final JButton deleteServiceButton = UiStyles.secondaryButton("删除检查");

    SystemPage(DashboardFrame frame) {
        super(frame);
        applyButtonIcons();
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        setBackground(UiStyles.BACKGROUND);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(UiStyles.pageTitle("系统设置"));
        heading.add(titleBlock, BorderLayout.WEST);

        JPanel columns = new JPanel(new GridLayout(1, 2, 14, 0));
        columns.setOpaque(false);
        columns.add(buildStatusCard());
        columns.add(buildPasswordCard());

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 14, 0);
        body.add(columns, constraints);
        constraints.gridy = 1;
        body.add(buildPersonalizationCard(), constraints);
        constraints.gridy = 2;
        constraints.weighty = 0;
        body.add(buildServiceCard(), constraints);

        JPanel auditCard = new JPanel();
        auditCard.setLayout(new BorderLayout(0, 8));
        UiStyles.card(auditCard, 12, 14);
        JPanel auditHeader = new JPanel(new BorderLayout());
        auditHeader.setOpaque(false);
        JLabel auditTitle = new JLabel("操作审计日志");
        auditTitle.setFont(UiStyles.SECTION);
        auditTitle.setForeground(UiStyles.TEXT);
        auditHeader.add(auditTitle, BorderLayout.WEST);
        refreshAuditButton.addActionListener(event -> refreshAudit());
        auditHeader.add(refreshAuditButton, BorderLayout.EAST);
        auditCard.add(auditHeader, BorderLayout.NORTH);
        configureAuditTable();
        JScrollPane auditScroll = new JScrollPane(auditTable);
        auditScroll.setPreferredSize(new Dimension(0, 240));
        auditCard.add(auditScroll, BorderLayout.CENTER);
        constraints.gridy = 3;
        constraints.weighty = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        body.add(auditCard, constraints);
        add(heading, BorderLayout.NORTH);
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(null);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(bodyScroll, BorderLayout.CENTER);
    }

    private void applyButtonIcons() {
        refreshStatusButton.setIcon(UiStyles.icon("rotate-cw", 14, UiStyles.MUTED));
        reconnectButton.setIcon(UiStyles.icon("wifi", 14, UiStyles.MUTED));
        addServiceButton.setIcon(UiStyles.icon("plus", 14, java.awt.Color.WHITE));
        deleteServiceButton.setIcon(UiStyles.icon("trash-2", 14, UiStyles.MUTED));
        logoutButton.setIcon(UiStyles.icon("log-out", 14, UiStyles.RED));
        for (javax.swing.JButton button : new javax.swing.JButton[] {
            refreshStatusButton, reconnectButton, addServiceButton,
            deleteServiceButton, logoutButton
        }) {
            button.setIconTextGap(6);
        }
    }

    private JPanel buildServiceCard() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        UiStyles.card(card, 12, 14);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("服务健康检查");
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        header.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        addServiceButton.addActionListener(event -> openAddServiceDialog());
        deleteServiceButton.addActionListener(event -> deleteSelectedService());
        deleteServiceButton.setEnabled(false);
        actions.add(addServiceButton);
        actions.add(deleteServiceButton);
        header.add(actions, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);
        serviceTable.setRowHeight(30);
        serviceTable.setShowGrid(false);
        serviceTable.setIntercellSpacing(new Dimension(0, 0));
        serviceTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                deleteServiceButton.setEnabled(serviceTable.getSelectedRow() >= 0);
            }
        });
        JScrollPane serviceScroll = new JScrollPane(serviceTable);
        serviceScroll.setPreferredSize(new Dimension(0, 120));
        card.add(serviceScroll, BorderLayout.CENTER);
        return card;
    }

    @Override
    void onServiceChecksChanged() {
        serviceModel.setRowCount(0);
        frame.serviceChecks.values().stream()
                .sorted(Comparator.comparingLong(ServiceCheckView::checkId))
                .forEach(check -> serviceModel.addRow(new Object[] {
                    check.name(),
                    check.host() + ":" + check.port(),
                    check.online() ? "正常" : "故障",
                    String.format(java.util.Locale.ROOT, "%.1f%%", check.availabilityPercent()),
                    check.lastCheckedAt() <= 0
                            ? "-"
                            : UiStyles.formatTime(check.lastCheckedAt())
                }));
    }

    private void openAddServiceDialog() {
        JTextField nameField = new JTextField("Web 服务", 16);
        JTextField hostField = new JTextField("127.0.0.1", 16);
        JTextField portField = new JTextField("80", 6);
        JTextField intervalField = new JTextField("5", 4);
        JTextField thresholdField = new JTextField("3", 4);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("名称"));
        form.add(nameField);
        form.add(new JLabel("主机"));
        form.add(hostField);
        form.add(new JLabel("端口"));
        form.add(portField);
        form.add(new JLabel("检查间隔（秒）"));
        form.add(intervalField);
        form.add(new JLabel("失败阈值（次）"));
        form.add(thresholdField);
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this), form, "添加服务健康检查",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        int port;
        int intervalSeconds;
        int threshold;
        try {
            port = Integer.parseInt(portField.getText().trim());
            intervalSeconds = Integer.parseInt(intervalField.getText().trim());
            threshold = Integer.parseInt(thresholdField.getText().trim());
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "端口、间隔和阈值必须是整数", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        frame.client().registerServiceCheck(
                        nameField.getText().trim(), hostField.getText().trim(), port,
                        intervalSeconds * 1000, threshold)
                .whenComplete((check, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        showError("添加检查失败", error);
                        return;
                    }
                    frame.serviceChecks.put(check.checkId(), check);
                    onServiceChecksChanged();
                }));
    }

    private void deleteSelectedService() {
        int viewRow = serviceTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        ServiceCheckView check = frame.serviceChecks.values().stream()
                .sorted(Comparator.comparingLong(ServiceCheckView::checkId))
                .toList()
                .get(viewRow);
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "删除服务检查 " + check.name() + " 吗？",
                "删除检查",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        frame.client().deleteServiceCheck(check.checkId())
                .whenComplete((deleted, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        showError("删除检查失败", error);
                    }
                }));
    }

    private JPanel buildStatusCard() {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 10));
        UiStyles.card(card, 14, 16);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("服务器状态");
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        header.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        refreshStatusButton.addActionListener(event -> refreshStatus());
        reconnectButton.addActionListener(event -> frame.reconnectFromUi());
        reconnectButton.setEnabled(false);
        actions.add(refreshStatusButton);
        actions.add(reconnectButton);
        header.add(actions, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 2, 12, 10));
        fields.setOpaque(false);
        fields.add(row("监听地址", hostValue));
        fields.add(row("运行时长", uptimeValue));
        fields.add(row("当前会话（设备 / 监控端）", sessionsValue));
        fields.add(row("已注册设备", devicesValue));
        fields.add(row("已存储采样", telemetryValue));
        fields.add(row("数据库体积", databaseSizeValue));
        fields.add(row("活动告警", alertsValue));
        fields.add(row("数据库文件", databasePathValue));
        fields.add(row("TLS 加密", tlsValue));
        card.add(fields, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPersonalizationCard() {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 10));
        UiStyles.card(card, 12, 14);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("外观与提醒");
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        header.add(title, BorderLayout.WEST);
        JLabel hint = new JLabel("个性化设置会立即生效并自动保存");
        hint.setFont(UiStyles.SMALL);
        hint.setForeground(UiStyles.MUTED);
        header.add(hint, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 28, 0));
        options.setOpaque(false);
        JCheckBox darkTheme = checkBox(
                "深色模式", ThemeManager.preference().current() == ThemePreference.Theme.DARK);
        darkTheme.addActionListener(event -> frame.toggleTheme());
        JCheckBox alertSound = checkBox("告警提示音", ThemeManager.preference().alertSoundEnabled());
        alertSound.addActionListener(event -> ThemeManager.preference().toggleAlertSound());
        JCheckBox alertToast = checkBox("告警弹窗提醒", ThemeManager.preference().toastEnabled());
        alertToast.addActionListener(event -> ThemeManager.preference().toggleToast());
        options.add(darkTheme);
        options.add(alertSound);
        options.add(alertToast);
        if (TrayManager.supported()) {
            JCheckBox trayOnClose = checkBox(
                    "关闭窗口时隐藏到托盘",
                    ThemeManager.preference().trayOnCloseEnabled());
            trayOnClose.addActionListener(
                    event -> ThemeManager.preference().toggleTrayOnClose());
            options.add(trayOnClose);
        }
        card.add(options, BorderLayout.CENTER);
        return card;
    }

    private static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text);
        box.setFont(UiStyles.FONT);
        box.setForeground(UiStyles.TEXT);
        box.setOpaque(false);
        box.setSelected(selected);
        return box;
    }

    private JPanel buildPasswordCard() {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(0, 10));
        UiStyles.card(card, 14, 16);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("账号安全");
        title.setFont(UiStyles.SECTION);
        title.setForeground(UiStyles.TEXT);
        header.add(title);
        card.add(header, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 10));
        fields.setOpaque(false);
        oldPassword.putClientProperty("JTextField.placeholderText", "输入当前密码");
        newPassword.putClientProperty("JTextField.placeholderText", "8-64 位新密码");
        confirmPassword.putClientProperty("JTextField.placeholderText", "再次输入新密码");
        fields.add(labeledField("当前密码", oldPassword));
        fields.add(labeledField("新密码", newPassword));
        fields.add(labeledField("确认新密码", confirmPassword));
        card.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        changePasswordButton.addActionListener(event -> changePassword());
        logoutButton.addActionListener(event -> frame.logoutFromUi());
        actions.add(logoutButton);
        actions.add(changePasswordButton);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private static JPanel labeledField(String caption, JPasswordField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        field.setPreferredSize(new Dimension(0, 34));
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    @Override
    void onConnectionChanged(boolean connected) {
        reconnectButton.setEnabled(!connected);
    }

    private void configureAuditTable() {
        auditTable.setFillsViewportHeight(true);
        auditTable.setRowHeight(30);
        auditTable.setShowGrid(false);
        auditTable.setIntercellSpacing(new Dimension(0, 0));
        auditTable.getColumnModel().getColumn(1).setCellRenderer(actionRenderer());
    }

    private void refreshStatus() {
        refreshStatusButton.setEnabled(false);
        frame.client().serverStatus().whenComplete((status, error) ->
                SwingUtilities.invokeLater(() -> {
                    refreshStatusButton.setEnabled(true);
                    if (error != null) {
                        showError("服务器状态获取失败", error);
                        return;
                    }
                    hostValue.setText(status.host() + ":" + status.port());
                    uptimeValue.setText(UiStyles.formatDuration(status.uptimeMillis()));
                    sessionsValue.setText(status.deviceSessions()
                            + " / " + status.monitorSessions());
                    devicesValue.setText(Integer.toString(status.registeredDevices()));
                    telemetryValue.setText(UiStyles.formatNumber(status.telemetryCount()));
                    databaseSizeValue.setText(UiStyles.formatBytes(status.databaseSizeBytes()));
                    alertsValue.setText(Long.toString(status.activeAlerts()));
                    String fullPath = status.databasePath();
                    databasePathValue.setText(
                            java.nio.file.Path.of(fullPath).getFileName().toString());
                    databasePathValue.setToolTipText(fullPath);
                    tlsValue.setText(status.tlsEnabled()
                            ? "已启用 · 端口 " + status.tlsPort()
                                    + " · " + status.tlsCertSubject()
                            : "未启用");
                    tlsValue.setToolTipText(status.tlsEnabled()
                            ? "握手失败次数：" + status.tlsHandshakeFailures()
                            : null);
                }));
    }

    private void refreshAudit() {
        refreshAuditButton.setEnabled(false);
        frame.client().queryAuditLog(300).whenComplete((events, error) ->
                SwingUtilities.invokeLater(() -> {
                    refreshAuditButton.setEnabled(true);
                    if (error != null) {
                        showError("审计日志获取失败", error);
                        return;
                    }
                    auditModel.replaceAll(events);
                }));
    }

    private void changePassword() {
        String oldValue = new String(oldPassword.getPassword());
        String newValue = new String(newPassword.getPassword());
        String confirmValue = new String(confirmPassword.getPassword());
        if (oldValue.isEmpty() || newValue.isEmpty()) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "当前密码和新密码不能为空", "输入不完整", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newValue.length() < 8) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "新密码至少 8 位", "密码过短", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newValue.equals(confirmValue)) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "两次输入的新密码不一致", "密码不一致", JOptionPane.WARNING_MESSAGE);
            return;
        }
        changePasswordButton.setEnabled(false);
        frame.client().changePassword(oldValue, newValue).whenComplete((changed, error) ->
                SwingUtilities.invokeLater(() -> {
                    changePasswordButton.setEnabled(true);
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                                cause.getMessage(), "修改失败", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    oldPassword.setText("");
                    newPassword.setText("");
                    confirmPassword.setText("");
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                            "密码已修改，下次登录请使用新密码", "修改成功",
                            JOptionPane.INFORMATION_MESSAGE);
                }));
    }

    private void showError(String title, Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel value() {
        JLabel label = new JLabel("-");
        label.setForeground(UiStyles.TEXT);
        label.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 13f));
        return label;
    }

    private static JPanel row(String caption, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static DefaultTableCellRenderer actionRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean selected, boolean focused,
                    int row, int column) {
                String action = (String) value;
                JLabel chip = actionChip(action);
                return chip;
            }
        };
    }

    static JLabel actionChip(String action) {
        return switch (action == null ? "" : action) {
            case "LOGIN" -> UiStyles.chip("登录", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT);
            case "ACK_ALERT" ->
                    UiStyles.chip("确认告警", UiStyles.AMBER_SOFT, UiStyles.AMBER_TEXT);
            case "RESOLVE_ALERT" ->
                    UiStyles.chip("解决告警", UiStyles.GREEN_SOFT, UiStyles.GREEN_TEXT);
            case "REGISTER_DEVICE" ->
                    UiStyles.chip("注册设备", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT);
            case "UPDATE_DEVICE" ->
                    UiStyles.chip("编辑设备", UiStyles.BLUE_SOFT, UiStyles.BLUE_TEXT);
            case "ENABLE_DEVICE" ->
                    UiStyles.chip("启用设备", UiStyles.GREEN_SOFT, UiStyles.GREEN_TEXT);
            case "DISABLE_DEVICE" ->
                    UiStyles.chip("停用设备", UiStyles.RED_SOFT, UiStyles.RED_TEXT);
            case "UPDATE_DEVICE_RULES" ->
                    UiStyles.chip("修改规则", UiStyles.NEUTRAL, UiStyles.TEXT);
            case "RESET_DEVICE_CREDENTIAL" ->
                    UiStyles.chip("重置凭证", UiStyles.NEUTRAL, UiStyles.TEXT);
            case "CHANGE_PASSWORD" ->
                    UiStyles.chip("修改密码", UiStyles.NEUTRAL, UiStyles.TEXT);
            default -> UiStyles.chip(action, UiStyles.NEUTRAL, UiStyles.TEXT);
        };
    }

    private static final class AuditTableModel extends AbstractTableModel {
        private final String[] columns = {"时间", "操作", "操作人", "对象", "说明"};
        private List<AuditEventView> rows = List.of();

        void replaceAll(List<AuditEventView> values) {
            rows = values.stream()
                    .sorted(Comparator.comparingLong(AuditEventView::createdAt).reversed())
                    .toList();
            fireTableDataChanged();
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
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AuditEventView event = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> UiStyles.formatTime(event.createdAt());
                case 1 -> event.action();
                case 2 -> event.actor();
                case 3 -> event.target() == null || event.target().isBlank()
                        ? "-" : event.target();
                case 4 -> event.detail() == null || event.detail().isBlank()
                        ? "-" : event.detail();
                default -> "";
            };
        }
    }
}
