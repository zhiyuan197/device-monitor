package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Non-modal demo control: injects faults into the live server pipeline so
 * alerts fire on demand during presentations.
 */
final class DemoControlDialog extends JDialog {
    private final DashboardFrame frame;
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton cpuButton = UiStyles.secondaryButton("模拟 CPU 高负载");
    private final JButton offlineButton = UiStyles.secondaryButton("模拟设备离线");
    private final JButton serviceButton = UiStyles.secondaryButton("模拟服务故障");
    private final List<JButton> buttons = List.of(cpuButton, offlineButton, serviceButton);

    DemoControlDialog(JFrame owner, DashboardFrame frame) {
        super(owner, "演示控制", false);
        this.frame = frame;
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        pack();
        setResizable(false);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(UiStyles.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel title = UiStyles.pageTitle("演示控制");
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        JLabel hint = new JLabel("选择一种故障，由真实规则触发告警并自动恢复");
        hint.setFont(UiStyles.SMALL);
        hint.setForeground(UiStyles.MUTED);
        column.add(hint);
        column.add(Box.createVerticalStrut(14));

        cpuButton.setAlignmentX(LEFT_ALIGNMENT);
        cpuButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cpuButton.addActionListener(event -> inject(DemoFault.CPU_SPIKE));
        column.add(cpuButton);
        column.add(Box.createVerticalStrut(8));

        offlineButton.setAlignmentX(LEFT_ALIGNMENT);
        offlineButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        offlineButton.addActionListener(event -> inject(DemoFault.OFFLINE));
        column.add(offlineButton);
        column.add(Box.createVerticalStrut(8));

        serviceButton.setAlignmentX(LEFT_ALIGNMENT);
        serviceButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        serviceButton.addActionListener(event -> inject(DemoFault.SERVICE_FAIL));
        column.add(serviceButton);
        column.add(Box.createVerticalStrut(14));

        statusLabel.setFont(UiStyles.SMALL);
        statusLabel.setForeground(UiStyles.MUTED);
        column.add(statusLabel);

        root.add(column, BorderLayout.CENTER);
        return root;
    }

    private void inject(DemoFault fault) {
        DeviceView device = pickOnlineDevice();
        ServiceCheckView check = null;
        if (fault == DemoFault.SERVICE_FAIL) {
            check = pickServiceCheck();
            if (check == null) {
                status("请先在系统设置中添加健康检查");
                return;
            }
        } else if (device == null) {
            status("没有可注入的在线设备");
            return;
        }
        if (!frame.client().isConnected()) {
            status("尚未连接服务器");
            return;
        }
        setBusy(true);
        frame.client()
                .demoInject(
                        fault,
                        device == null ? null : device.deviceId(),
                        check == null ? null : check.checkId())
                .whenComplete((view, error) -> SwingUtilities.invokeLater(() -> {
                    setBusy(false);
                    if (error != null) {
                        status("注入失败：" + shortMessage(error));
                    } else {
                        status("已注入 " + faultName(view) + "，等待告警触发");
                    }
                }));
    }

    private DeviceView pickOnlineDevice() {
        return frame.devices.values().stream()
                .filter(DeviceView::online)
                .filter(DeviceView::enabled)
                .min(Comparator.comparing(DeviceView::deviceId))
                .orElse(null);
    }

    private ServiceCheckView pickServiceCheck() {
        return frame.serviceChecks.values().stream()
                .min(Comparator.comparing(ServiceCheckView::checkId))
                .orElse(null);
    }

    private void setBusy(boolean busy) {
        for (JButton button : buttons) {
            button.setEnabled(!busy);
        }
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    private static String faultName(DemoInjectionView view) {
        return switch (view.fault()) {
            case "CPU_SPIKE" -> "CPU 高负载";
            case "OFFLINE" -> "设备离线";
            case "SERVICE_FAIL" -> "服务故障";
            default -> view.fault();
        };
    }

    private static String shortMessage(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        return message.length() > 80 ? message.substring(0, 80) : message;
    }
}
