package com.shenliwei.devicewatch.monitor;

import com.shenliwei.devicewatch.common.AppConfig;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Flat, centered login window: one card, clear labels, no decorative
 * side panel.
 */
final class LoginFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 20);
    private final JTextField portField = new JTextField("5050", 8);
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton connectButton = UiStyles.primaryButton("连接并登录");
    private final JLabel messageLabel = new JLabel("");
    private final boolean stopDemoOnExit;
    private final AppConfig appConfig;

    LoginFrame(AppConfig config) {
        this(config, false);
    }

    LoginFrame(AppConfig config, boolean stopDemoOnExit) {
        super("设备状态监控与告警系统");
        this.appConfig = config;
        this.stopDemoOnExit = stopDemoOnExit;
        Image icon = UiStyles.appIcon();
        if (icon != null) {
            setIconImage(icon);
        }
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(620, 560);
        setMinimumSize(new Dimension(560, 500));
        setLocationRelativeTo(null);
        setContentPane(buildContent(config));
        loadDemoDefaults(config);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                hostField.select(hostField.getText().length(), hostField.getText().length());
            }

            @Override
            public void windowClosing(WindowEvent event) {
                if (stopDemoOnExit) {
                    DemoEnvironment.stop();
                }
                dispose();
                System.exit(0);
            }
        });
    }

    private JPanel buildContent(AppConfig config) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(UiStyles.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        JPanel card = new JPanel(new GridBagLayout());
        UiStyles.card(card, 28, 32);
        root.add(card, new GridBagConstraints());

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel mark = new JLabel("DEVICE WATCH");
        mark.setForeground(UiStyles.PRIMARY);
        mark.setFont(UiStyles.FONT.deriveFont(Font.BOLD, 11f));
        JLabel title = new JLabel("设备状态监控与告警系统");
        title.setForeground(UiStyles.TEXT);
        title.setFont(UiStyles.FONT.deriveFont(Font.BOLD, 21f));
        column.add(mark);
        column.add(Box.createVerticalStrut(8));
        column.add(title);
        column.add(Box.createVerticalStrut(22));

        addField(column, "服务器地址", hostField);
        addField(column, "端口", portField);
        addField(column, "账号", usernameField);
        addField(column, "密码", passwordField);

        column.add(Box.createVerticalStrut(18));
        connectButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        connectButton.setAlignmentX(LEFT_ALIGNMENT);
        connectButton.addActionListener(event -> connect());
        column.add(connectButton);

        column.add(Box.createVerticalStrut(12));
        messageLabel.setAlignmentX(LEFT_ALIGNMENT);
        messageLabel.setForeground(UiStyles.MUTED);
        messageLabel.setFont(UiStyles.SMALL);
        column.add(messageLabel);

        column.setPreferredSize(new Dimension(400, column.getPreferredSize().height));
        card.add(column);
        getRootPane().setDefaultButton(connectButton);
        return root;
    }

    private static void addField(JPanel column, String labelText, JTextField field) {
        JLabel label = new JLabel(labelText);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        label.setAlignmentX(LEFT_ALIGNMENT);
        column.add(label);
        column.add(Box.createVerticalStrut(4));
        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        column.add(field);
        column.add(Box.createVerticalStrut(12));
    }

    private void loadDemoDefaults(AppConfig config) {
        hostField.setText(config.host().equals("0.0.0.0") ? "127.0.0.1" : config.host());
        portField.setText(Integer.toString(
                config.tlsEnabled() ? config.tlsPort() : config.port()));
        Path demo = Path.of("config", "demo.properties");
        if (!Files.exists(demo)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(demo)) {
            properties.load(input);
            hostField.setText(defaultHost(config, properties));
            portField.setText(Integer.toString(defaultPort(config, properties)));
            usernameField.setText(properties.getProperty("monitor.username", "").trim());
            passwordField.setText(properties.getProperty("monitor.password", "").trim());
            messageLabel.setText("");
        } catch (IOException exception) {
            messageLabel.setText("默认配置读取失败");
        }
    }

    static int defaultPort(AppConfig config, Properties demo) {
        if (config.tlsEnabled()) {
            return config.tlsPort();
        }
        String demoPort = demo.getProperty("server.port", "").trim();
        if (!demoPort.isBlank()) {
            try {
                int port = Integer.parseInt(demoPort);
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the server configuration port.
            }
        }
        return config.port();
    }

    static String defaultHost(AppConfig config, Properties demo) {
        String demoHost = demo.getProperty("server.host", "").trim();
        if (!demoHost.isBlank()) {
            return demoHost;
        }
        return config.host().equals("0.0.0.0") ? "127.0.0.1" : config.host();
    }

    private void connect() {
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (host.isEmpty() || username.isEmpty() || passwordField.getPassword().length == 0) {
                throw new IllegalArgumentException("服务器、账号和密码不能为空");
            }
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    this, exception.getMessage(), "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        connectButton.setEnabled(false);
        messageLabel.setText("正在连接服务器...");
        char[] password = passwordField.getPassword();
        SwingWorker<DashboardFrame, Void> worker = new SwingWorker<>() {
            @Override
            protected DashboardFrame doInBackground() throws Exception {
                DashboardFrame dashboard =
                        new DashboardFrame(
                                host, port, username, password, stopDemoOnExit,
                                appConfig.tlsEnabled(),
                                appConfig.tlsTruststorePath(),
                                appConfig.tlsTruststorePassword());
                dashboard.connectNow();
                return dashboard;
            }

            @Override
            protected void done() {
                connectButton.setEnabled(true);
                try {
                    DashboardFrame dashboard = get();
                    dashboard.setVisible(true);
                    dispose();
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    messageLabel.setText("无法连接 " + host + ":" + port
                            + "（TLS " + (appConfig.tlsEnabled() ? "已启用" : "未启用")
                            + "）：" + cause.getMessage());
                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
                            cause.getMessage(),
                            "连接失败",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };
        worker.execute();
    }
}
