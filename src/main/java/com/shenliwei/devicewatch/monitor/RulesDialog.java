package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class RulesDialog {
    private RulesDialog() {
    }

    static DeviceRulesView show(Window owner, DeviceView device) {
        JDialog dialog = new JDialog(owner, "告警规则 - " + device.deviceId(),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(UiStyles.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));

        JLabel header = UiStyles.pageTitle("设备专属告警阈值");
        header.setFont(UiStyles.SECTION);
        JLabel note = new JLabel("触发阈值必须高于恢复阈值。");
        note.setForeground(UiStyles.MUTED);
        note.setFont(UiStyles.SMALL);
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(header, BorderLayout.NORTH);
        heading.add(note, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        DeviceRulesView current = device.rules();
        NumberField cpuTrigger = new NumberField(current.cpuTrigger());
        NumberField cpuRecover = new NumberField(current.cpuRecover());
        NumberField cpuSamples = new NumberField(current.cpuSamples());
        NumberField memoryTrigger = new NumberField(current.memoryTrigger());
        NumberField memoryRecover = new NumberField(current.memoryRecover());
        NumberField memorySamples = new NumberField(current.memorySamples());
        NumberField tempTrigger = new NumberField(current.temperatureTrigger());
        NumberField tempRecover = new NumberField(current.temperatureRecover());

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(4, 0, 4, 0);

        addRow(form, constraints, "CPU 触发阈值 (%)", cpuTrigger);
        addRow(form, constraints, "CPU 恢复阈值 (%)", cpuRecover);
        addRow(form, constraints, "CPU 连续采样数", cpuSamples);
        addRow(form, constraints, "内存触发阈值 (%)", memoryTrigger);
        addRow(form, constraints, "内存恢复阈值 (%)", memoryRecover);
        addRow(form, constraints, "内存连续采样数", memorySamples);
        addRow(form, constraints, "温度触发阈值 (°C)", tempTrigger);
        addRow(form, constraints, "温度恢复阈值 (°C)", tempRecover);
        content.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = UiStyles.secondaryButton("取消");
        JButton confirm = UiStyles.primaryButton("保存规则");
        buttons.add(cancel);
        buttons.add(confirm);
        content.add(buttons, BorderLayout.SOUTH);

        DeviceRulesView[] result = new DeviceRulesView[1];
        confirm.addActionListener(event -> {
            try {
                result[0] = new DeviceRulesView(
                        cpuTrigger.value(), cpuRecover.value(), (int) cpuSamples.value(),
                        memoryTrigger.value(), memoryRecover.value(), (int) memorySamples.value(),
                        tempTrigger.value(), tempRecover.value());
                dialog.dispose();
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(dialog, exception.getMessage(),
                        "规则无效", JOptionPane.WARNING_MESSAGE);
            }
        });
        cancel.addActionListener(event -> dialog.dispose());

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(460, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    private static void addRow(
            JPanel form, GridBagConstraints constraints, String caption, NumberField field) {
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.insets = new Insets(6, 0, 6, 0);
        constraints.weightx = 0;
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.TEXT);
        label.setFont(UiStyles.FONT);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(field, constraints);
        constraints.gridx = 0;
        constraints.weightx = 1;
    }

    private static final class NumberField extends JTextField {
        NumberField(double initial) {
            super(10);
            setText(numberText(initial));
            setPreferredSize(new Dimension(0, 32));
        }

        double value() {
            return Double.parseDouble(getText().trim());
        }

        private static String numberText(double value) {
            return value == Math.rint(value)
                    ? Long.toString((long) value)
                    : Double.toString(value);
        }
    }
}
