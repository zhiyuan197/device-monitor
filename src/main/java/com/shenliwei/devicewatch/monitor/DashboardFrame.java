package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.web.WebDashboard;
import com.shenliwei.devicewatch.web.WebDashboardConfig;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Main monitor shell: sidebar navigation plus five pages backed by a
 * live data hub that mirrors the server snapshots and updates.
 */
public final class DashboardFrame extends JFrame implements MonitorListener {
    static final int TREND_LIMIT = 90;

    static final String PAGE_OVERVIEW = "overview";
    static final String PAGE_ALERTS = "alerts";
    static final String PAGE_DEVICES = "devices";
    static final String PAGE_MESSAGES = "messages";
    static final String PAGE_REPORTS = "reports";
    static final String PAGE_SYSTEM = "system";

    private final MonitorClient client;
    private final String host;
    private final int port;

    final Map<String, DeviceView> devices = new LinkedHashMap<>();
    final Map<Long, AlertView> activeAlerts = new LinkedHashMap<>();
    final Map<String, Deque<Double>> cpuTrends = new LinkedHashMap<>();
    final Map<String, Deque<Double>> memoryTrends = new LinkedHashMap<>();
    final Map<String, Deque<Double>> latencyTrends = new LinkedHashMap<>();
    final Map<String, Deque<Double>> diskTrends = new LinkedHashMap<>();
    final Map<Long, ServiceCheckView> serviceChecks = new LinkedHashMap<>();
    private boolean serviceChecksRequested;

    private final boolean stopDemoOnExit;
    private CardLayout cardLayout;
    private JPanel cards;
    private JPanel sidebar;
    private JButton overviewNav;
    private JButton alertsNav;
    private JButton devicesNav;
    private JButton messagesNav;
    private JButton reportsNav;
    private JButton dashboardNav;
    private JButton systemNav;
    private JLabel alertBadge;
    private JLabel connectionLabel;
    private Timer reconnectTimer;
    private int reconnectAttempt;
    private boolean closing;
    private boolean exiting;
    private long previousUnacknowledged = -1;
    private TrayManager tray;
    private final WebDashboard webDashboard;

    private OverviewPage overviewPage;
    private AlertsPage alertsPage;
    private DevicesPage devicesPage;
    private ReportsPage reportsPage;
    private SystemPage systemPage;
    private MessagesPage messagesPage;
    private final MessageLogModel messageLog;
    private List<PagePanel> pages = List.of();
    private List<JButton> navButtons = List.of();
    private String currentPageId = PAGE_OVERVIEW;

    public DashboardFrame(String host, int port, String username, char[] password) {
        this(host, port, username, password, false, false, null, "");
    }

    public DashboardFrame(
            String host, int port, String username, char[] password, boolean stopDemoOnExit) {
        this(host, port, username, password, stopDemoOnExit, false, null, "");
    }

    public DashboardFrame(
            String host,
            int port,
            String username,
            char[] password,
            boolean stopDemoOnExit,
            boolean useTls,
            java.nio.file.Path truststorePath,
            String truststorePassword) {
        super("设备状态监控与告警系统");
        this.host = host;
        this.port = port;
        this.stopDemoOnExit = stopDemoOnExit;
        Image icon = UiStyles.appIcon();
        if (icon != null) {
            setIconImage(icon);
        }
        messageLog = new MessageLogModel();
        client = new MonitorClient(
                host, port, username, password, this,
                useTls, truststorePath, truststorePassword,
                (direction, type, bytes, timestamp) ->
                        messageLog.add(new MessageEvent(
                                direction, type, bytes, timestamp)));
        webDashboard = new WebDashboard(
                WebDashboardConfig.load(ThemePreference.DEFAULT_PATH), host, port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(webDashboard::stop, "web-dashboard-shutdown"));
        buildUiFields();
        alertBadge.setVisible(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Rectangle desktop = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int windowWidth = Math.max(1180, Math.min(1440, desktop.width - 40));
        int windowHeight = Math.max(720, Math.min(860, desktop.height - 50));
        setSize(windowWidth, windowHeight);
        setMinimumSize(new Dimension(1040, 680));
        setLocationRelativeTo(null);
        setContentPane(buildShell());
        showPage(PAGE_OVERVIEW, overviewNav);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!exiting
                        && ThemeManager.preference().trayOnCloseEnabled()
                        && TrayManager.supported()) {
                    hideToTray();
                } else {
                    closeApplication();
                }
            }
        });
    }

    public void connectNow() throws Exception {
        client.connect();
    }

    MonitorClient client() {
        return client;
    }

    List<DeviceView> deviceList() {
        return devices.values().stream()
                .sorted(Comparator.comparing(DeviceView::deviceId))
                .toList();
    }

    List<AlertView> alertList() {
        return activeAlerts.values().stream()
                .sorted(Comparator.comparingLong(AlertView::triggeredAt).reversed())
                .toList();
    }

    void openReports(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            reportsPage.preselectDevice(deviceId);
        }
        showPage(PAGE_REPORTS, reportsNav);
    }

    void showDevicesPage() {
        showPage(PAGE_DEVICES, devicesNav);
    }

    private void openWebDashboard() {
        dashboardNav.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    if (!webDashboard.isRunning()) {
                        webDashboard.start();
                    }
                    return true;
                } catch (IOException exception) {
                    return false;
                }
            }

            @Override
            protected void done() {
                dashboardNav.setEnabled(true);
                try {
                    if (!get()) {
                        JOptionPane.showMessageDialog(
                                DashboardFrame.this,
                                "大屏服务启动失败，请检查 8088 端口是否被占用",
                                "实时大屏",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!webDashboard.openBrowser()) {
                        JOptionPane.showMessageDialog(
                                DashboardFrame.this,
                                "请在浏览器中打开：" + webDashboard.url(),
                                "实时大屏",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ignored) {
                    // Nothing else to do when the browser launch is interrupted.
                }
            }
        }.execute();
    }

    void showPageForCapture(String page) {
        JButton button = switch (page) {
            case PAGE_ALERTS -> alertsNav;
            case PAGE_DEVICES -> devicesNav;
            case PAGE_MESSAGES -> messagesNav;
            case PAGE_REPORTS -> reportsNav;
            case PAGE_SYSTEM -> systemNav;
            default -> overviewNav;
        };
        showPage(page, button);
    }

    private JPanel buildShell() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiStyles.BACKGROUND);
        sidebar = buildSidebar();
        root.add(sidebar, BorderLayout.WEST);

        cards.setOpaque(false);
        cards.add(overviewPage, PAGE_OVERVIEW);
        cards.add(alertsPage, PAGE_ALERTS);
        cards.add(devicesPage, PAGE_DEVICES);
        cards.add(messagesPage, PAGE_MESSAGES);
        cards.add(reportsPage, PAGE_REPORTS);
        cards.add(systemPage, PAGE_SYSTEM);
        root.add(cards, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildSidebar() {
        JPanel panel = new JPanel();
        panel.setBackground(UiStyles.SIDEBAR);
        panel.setLayout(new BorderLayout());
        panel.setPreferredSize(new Dimension(212, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, UiStyles.BORDER),
                BorderFactory.createEmptyBorder(20, 16, 14, 16)));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        Image appImage = UiStyles.appIcon();
        if (appImage != null) {
            JLabel brandIcon = new JLabel(new ImageIcon(
                    appImage.getScaledInstance(28, 28, java.awt.Image.SCALE_SMOOTH)));
            brand.add(brandIcon, BorderLayout.WEST);
        }
        JLabel title = new JLabel("设备监控中心");
        title.setForeground(UiStyles.TEXT);
        title.setFont(UiStyles.FONT.deriveFont(Font.BOLD, 17f));
        brand.add(title, BorderLayout.CENTER);
        top.add(brand);
        top.add(Box.createVerticalStrut(24));

        addNav(top, overviewNav, PAGE_OVERVIEW);
        top.add(Box.createVerticalStrut(4));
        JPanel alertsRow = navRow(alertsNav, alertBadge);
        alertsNav.addActionListener(event -> showPage(PAGE_ALERTS, alertsNav));
        top.add(alertsRow);
        top.add(Box.createVerticalStrut(4));
        addNav(top, devicesNav, PAGE_DEVICES);
        top.add(Box.createVerticalStrut(4));
        addNav(top, messagesNav, PAGE_MESSAGES);
        top.add(Box.createVerticalStrut(4));
        addNav(top, reportsNav, PAGE_REPORTS);
        top.add(Box.createVerticalStrut(4));
        top.add(navRow(dashboardNav, null));
        dashboardNav.addActionListener(event -> openWebDashboard());
        top.add(Box.createVerticalStrut(4));
        addNav(top, systemNav, PAGE_SYSTEM);
        panel.add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel statusCard = new JPanel();
        UiStyles.card(statusCard, 10, 12);
        JPanel statusColumn = new JPanel();
        statusColumn.setOpaque(false);
        statusColumn.setLayout(new BoxLayout(statusColumn, BoxLayout.Y_AXIS));
        connectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionLabel.setForeground(UiStyles.MUTED);
        connectionLabel.setFont(UiStyles.SMALL.deriveFont(Font.BOLD, 12f));
        statusColumn.add(connectionLabel);
        statusColumn.add(Box.createVerticalStrut(3));
        JLabel serverLabel = new JLabel(host + ":" + port);
        serverLabel.setForeground(UiStyles.MUTED);
        serverLabel.setFont(UiStyles.SMALL.deriveFont(11f));
        serverLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusColumn.add(serverLabel);
        statusCard.add(statusColumn);
        bottom.add(statusCard);
        bottom.add(Box.createVerticalStrut(10));

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void addNav(JPanel container, JButton button, String page) {
        JPanel row = navRow(button, null);
        button.addActionListener(event -> showPage(page, button));
        container.add(row);
    }

    private static JPanel navRow(JButton button, JLabel badge) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(button, BorderLayout.CENTER);
        if (badge != null) {
            row.add(badge, BorderLayout.EAST);
        }
        return row;
    }

    private static JButton navButton(String text) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setFont(UiStyles.FONT.deriveFont(Font.BOLD, 14f));
        button.setForeground(UiStyles.MUTED);
        button.setBackground(UiStyles.SIDEBAR);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    private void showPage(String page, JButton selected) {
        currentPageId = page;
        cardLayout.show(cards, page);
        for (JButton button : navButtons) {
            boolean active = button == selected;
            String iconName = (String) button.getClientProperty("UiStyles.navIcon");
            if (iconName != null) {
                button.setIcon(UiStyles.icon(
                        iconName, 16, active ? UiStyles.PRIMARY : UiStyles.MUTED));
                button.setIconTextGap(10);
            }
            button.setOpaque(active);
            button.setBackground(active ? UiStyles.PRIMARY_SOFT : UiStyles.SIDEBAR);
            button.setForeground(active ? UiStyles.PRIMARY : UiStyles.MUTED);
            button.setBorder(active
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, UiStyles.PRIMARY),
                            BorderFactory.createEmptyBorder(10, 11, 10, 14))
                    : BorderFactory.createEmptyBorder(10, 14, 10, 14));
        }
    }

    private void buildUiFields() {
        overviewNav = navButton("运行总览");
        overviewNav.putClientProperty("UiStyles.navIcon", "layout-dashboard");
        alertsNav = navButton("告警中心");
        alertsNav.putClientProperty("UiStyles.navIcon", "bell");
        devicesNav = navButton("设备管理");
        devicesNav.putClientProperty("UiStyles.navIcon", "server");
        messagesNav = navButton("报文观测");
        messagesNav.putClientProperty("UiStyles.navIcon", "radio");
        reportsNav = navButton("历史报告");
        reportsNav.putClientProperty("UiStyles.navIcon", "history");
        dashboardNav = navButton("实时大屏");
        dashboardNav.putClientProperty("UiStyles.navIcon", "tv");
        dashboardNav.setToolTipText("在浏览器中打开实时监控大屏");
        systemNav = navButton("系统设置");
        systemNav.putClientProperty("UiStyles.navIcon", "settings");
        alertBadge = UiStyles.countBadge();
        connectionLabel = new JLabel("● 正在连接");
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        overviewPage = new OverviewPage(this);
        alertsPage = new AlertsPage(this);
        devicesPage = new DevicesPage(this);
        messagesPage = new MessagesPage(this, messageLog);
        reportsPage = new ReportsPage(this);
        systemPage = new SystemPage(this);
        pages = List.of(
                overviewPage, alertsPage, devicesPage,
                messagesPage, reportsPage, systemPage);
        navButtons = List.of(
                overviewNav, alertsNav, devicesNav, messagesNav,
                reportsNav, dashboardNav, systemNav);
    }

    void toggleTheme() {
        ThemeManager.preference().toggle();
        UiStyles.apply();
        rebuildUi();
    }

    void toggleThemeForCapture() {
        toggleTheme();
    }

    private void rebuildUi() {
        buildUiFields();
        alertBadge.setVisible(false);
        setContentPane(buildShell());
        updateAlertBadge();
        revalidate();
        repaint();
        for (PagePanel page : pages) {
            page.onDevicesChanged();
            page.onAlertsChanged();
            page.onServiceChecksChanged();
        }
        for (PagePanel page : pages) {
            page.onConnectionChanged(client.isConnected());
        }
        showPage(currentPageId, navButtonFor(currentPageId));
        if (client.isConnected()) {
            connectionLabel.setText("● 已连接");
            connectionLabel.setForeground(UiStyles.GREEN);
        }
    }

    private JButton navButtonFor(String page) {
        return switch (page) {
            case PAGE_ALERTS -> alertsNav;
            case PAGE_DEVICES -> devicesNav;
            case PAGE_REPORTS -> reportsNav;
            case PAGE_SYSTEM -> systemNav;
            default -> overviewNav;
        };
    }

    private void hideToTray() {
        try {
            if (tray == null) {
                tray = TrayManager.install(this, () -> {
                    exiting = true;
                    closeApplication();
                });
            }
            setVisible(false);
            tray.notify("设备监控", "程序已最小化到系统托盘，告警会继续推送");
        } catch (Exception exception) {
            closeApplication();
        }
    }

    private void updateAlertBadge() {
        long unacknowledged = activeAlerts.values().stream()
                .filter(alert -> alert.acknowledgedAt() == null)
                .count();
        if (unacknowledged == 0) {
            alertBadge.setVisible(false);
            alertBadge.setToolTipText(null);
            alertBadge.setText("");
            return;
        }
        alertBadge.setVisible(true);
        alertBadge.setText(unacknowledged > 99 ? "99+" : Long.toString(unacknowledged));
        alertBadge.setToolTipText(unacknowledged + " 条待确认告警");
    }

    private void reconnect() {
        cancelReconnectTimer();
        attemptReconnect();
    }

    void reconnectFromUi() {
        reconnect();
    }

    void logoutFromUi() {
        closeApplication();
    }

    private void scheduleReconnect() {
        if (closing) {
            return;
        }
        cancelReconnectTimer();
        int nextAttempt = reconnectAttempt + 1;
        long delay = ReconnectScheduler.delayMillis(nextAttempt);
        connectionLabel.setText("● 连接中断，" + (delay / 1000) + " 秒后自动重连（第 "
                + nextAttempt + " 次）");
        reconnectTimer = new Timer((int) delay, event -> attemptReconnect());
        reconnectTimer.setRepeats(false);
        reconnectTimer.start();
    }

    private void attemptReconnect() {
        if (closing) {
            return;
        }
        reconnectAttempt++;
        connectionLabel.setText("● 正在重新连接（第 " + reconnectAttempt + " 次）...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                client.connect();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    connectionLabel.setText("● 重连失败：" + cause.getMessage());
                    scheduleReconnect();
                }
            }
        };
        worker.execute();
    }

    private void cancelReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.stop();
            reconnectTimer = null;
        }
    }

    private void closeApplication() {
        exiting = true;
        closing = true;
        cancelReconnectTimer();
        if (tray != null) {
            tray.dispose();
            tray = null;
        }
        webDashboard.stop();
        client.close();
        dispose();
        if (stopDemoOnExit) {
            DemoEnvironment.stop();
        }
        System.exit(0);
    }


    @Override
    public void onDeviceSnapshot(List<DeviceView> snapshot) {
        SwingUtilities.invokeLater(() -> {
            devices.clear();
            cpuTrends.clear();
            memoryTrends.clear();
            latencyTrends.clear();
            diskTrends.clear();
            for (DeviceView device : snapshot) {
                devices.put(device.deviceId(), device);
                cpuTrends.put(device.deviceId(), new ArrayDeque<>());
                memoryTrends.put(device.deviceId(), new ArrayDeque<>());
                latencyTrends.put(device.deviceId(), new ArrayDeque<>());
                diskTrends.put(device.deviceId(), new ArrayDeque<>());
                if (device.cpuPercent() != null) {
                    cpuTrends.get(device.deviceId()).addLast(device.cpuPercent());
                }
                if (device.memoryPercent() != null) {
                    memoryTrends.get(device.deviceId()).addLast(device.memoryPercent());
                }
                if (device.latencyMillis() > 0) {
                    latencyTrends.get(device.deviceId()).addLast((double) device.latencyMillis());
                }
                if (device.diskPercent() != null) {
                    diskTrends.get(device.deviceId()).addLast(device.diskPercent());
                }
            }
            pages.forEach(PagePanel::onDevicesChanged);
            webDashboard.onDeviceSnapshot(snapshot);
        });
    }

    @Override
    public void onDeviceUpdate(DeviceView device) {
        SwingUtilities.invokeLater(() -> {
            devices.put(device.deviceId(), device);
            if (device.cpuPercent() != null) {
                Deque<Double> trend = cpuTrends.computeIfAbsent(
                        device.deviceId(), ignored -> new ArrayDeque<>());
                trend.addLast(device.cpuPercent());
                while (trend.size() > TREND_LIMIT) {
                    trend.removeFirst();
                }
            }
            if (device.memoryPercent() != null) {
                Deque<Double> trend = memoryTrends.computeIfAbsent(
                        device.deviceId(), ignored -> new ArrayDeque<>());
                trend.addLast(device.memoryPercent());
                while (trend.size() > TREND_LIMIT) {
                    trend.removeFirst();
                }
            }
            if (device.latencyMillis() > 0) {
                Deque<Double> trend = latencyTrends.computeIfAbsent(
                        device.deviceId(), ignored -> new ArrayDeque<>());
                trend.addLast((double) device.latencyMillis());
                while (trend.size() > TREND_LIMIT) {
                    trend.removeFirst();
                }
            }
            if (device.diskPercent() != null) {
                Deque<Double> trend = diskTrends.computeIfAbsent(
                        device.deviceId(), ignored -> new ArrayDeque<>());
                trend.addLast(device.diskPercent());
                while (trend.size() > TREND_LIMIT) {
                    trend.removeFirst();
                }
            }
            pages.forEach(PagePanel::onDevicesChanged);
            webDashboard.onDeviceUpdate(device);
        });
    }

    @Override
    public void onAlertSnapshot(List<AlertView> alerts) {
        SwingUtilities.invokeLater(() -> {
            activeAlerts.clear();
            for (AlertView alert : alerts) {
                activeAlerts.put(alert.alertId(), alert);
            }
            previousUnacknowledged = activeAlerts.values().stream()
                    .filter(alert -> alert.acknowledgedAt() == null)
                    .count();
            updateAlertBadge();
            pages.forEach(PagePanel::onAlertsChanged);
            webDashboard.onAlertSnapshot(alerts);
        });
    }

    @Override
    public void onAlertUpdate(AlertView alert) {
        SwingUtilities.invokeLater(() -> {
            if ("ACTIVE".equals(alert.status())) {
                activeAlerts.put(alert.alertId(), alert);
            } else {
                activeAlerts.remove(alert.alertId());
            }
            long unacknowledged = activeAlerts.values().stream()
                    .filter(item -> item.acknowledgedAt() == null)
                    .count();
            if (previousUnacknowledged >= 0
                    && unacknowledged > previousUnacknowledged
                    && !closing
                    && ThemeManager.preference().toastEnabled()) {
                AlertView newest = activeAlerts.values().stream()
                        .filter(item -> item.acknowledgedAt() == null)
                        .max(Comparator.comparingLong(AlertView::triggeredAt))
                        .orElse(null);
                if (newest != null) {
                    AlertToast.show(this, "新告警 · " + newest.deviceId() + " · "
                            + AlertsPage.alertLabel(newest.ruleType()));
                    if (tray != null && !isVisible()) {
                        tray.notify(
                                "新告警",
                                newest.deviceId() + " · "
                                        + AlertsPage.alertLabel(newest.ruleType()));
                    }
                }
            }
            previousUnacknowledged = unacknowledged;
            updateAlertBadge();
            pages.forEach(PagePanel::onAlertsChanged);
            webDashboard.onAlertUpdate(alert);
        });
    }

    @Override
    public void onConnectionState(boolean connected, String detail) {
        SwingUtilities.invokeLater(() -> {
            webDashboard.onConnectionState(connected, detail);
            if (connected) {
                cancelReconnectTimer();
                reconnectAttempt = 0;
                if (!serviceChecksRequested) {
                    serviceChecksRequested = true;
                    client.listServiceChecks().whenComplete((checks, error) -> {
                        if (error == null) {
                            SwingUtilities.invokeLater(() -> {
                                serviceChecks.clear();
                                for (ServiceCheckView check : checks) {
                                    serviceChecks.put(check.checkId(), check);
                                }
                                webDashboard.onServiceCheckSnapshot(checks);
                                pages.forEach(PagePanel::onServiceChecksChanged);
                            });
                        }
                    });
                }
                connectionLabel.setText("● 已连接");
                connectionLabel.setForeground(UiStyles.GREEN);
            } else {
                connectionLabel.setForeground(UiStyles.RED);
                if (!closing) {
                    scheduleReconnect();
                } else {
                    connectionLabel.setText("● 连接中断");
                }
            }
            setTitle((connected ? "在线 · " : "离线 · ") + "设备状态监控与告警系统");
            pages.forEach(page -> page.onConnectionChanged(connected));
        });
    }

    @Override
    public void onRemoteError(String code, String message) {
        SwingUtilities.invokeLater(() -> {
            connectionLabel.setText("● 服务器提示");
            connectionLabel.setForeground(new Color(250, 204, 21));
            if ("AUTH_REQUIRED".equals(code) || "SERVER_SHUTTING_DOWN".equals(code)) {
                JOptionPane.showMessageDialog(
                        this, message, "连接提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    @Override
    public void onServiceCheckSnapshot(List<ServiceCheckView> checks) {
        SwingUtilities.invokeLater(() -> {
            serviceChecks.clear();
            for (ServiceCheckView check : checks) {
                serviceChecks.put(check.checkId(), check);
            }
            webDashboard.onServiceCheckSnapshot(checks);
            pages.forEach(PagePanel::onServiceChecksChanged);
        });
    }

    @Override
    public void onServiceCheckUpdate(ServiceCheckView check) {
        SwingUtilities.invokeLater(() -> {
            serviceChecks.put(check.checkId(), check);
            webDashboard.onServiceCheckUpdate(check);
            pages.forEach(PagePanel::onServiceChecksChanged);
        });
    }

    @Override
    public void onServiceCheckDeleted(long checkId) {
        SwingUtilities.invokeLater(() -> {
            serviceChecks.remove(checkId);
            webDashboard.onServiceCheckDeleted(checkId);
            pages.forEach(PagePanel::onServiceChecksChanged);
        });
    }
}
