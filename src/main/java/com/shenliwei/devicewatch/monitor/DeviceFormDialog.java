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

final class DeviceFormDialog {
    record Result(String deviceId, String displayName, String location) {
    }

    private DeviceFormDialog() {
    }

    static Result showRegister(Window owner) {
        return show(owner, "注册设备", null, null, null, true);
    }

    static Result showEdit(Window owner, DeviceView device) {
        return show(owner, "编辑设备信息", device.deviceId(), device.displayName(),
                device.location(), false);
    }

    private static Result show(
            Window owner,
            String title,
            String deviceId,
            String displayName,
            String location,
            boolean register) {
        JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(UiStyles.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(4, 0, 4, 0);

        JTextField idField = new JTextField(20);
        JTextField nameField = new JTextField(20);
        JTextField locationField = new JTextField(20);
        if (deviceId != null) {
            idField.setText(deviceId);
        }
        if (displayName != null) {
            nameField.setText(displayName);
        }
        if (location != null) {
            locationField.setText(location);
        }
        idField.setEditable(register);
        if (register) {
            idField.putClientProperty("JTextField.placeholderText", "如 DEV-006，仅字母数字和短横线");
        }
        nameField.putClientProperty("JTextField.placeholderText", "设备显示名称");
        locationField.putClientProperty("JTextField.placeholderText", "如 机房 A 区 / 教学实验室");

        addField(form, constraints, "设备编号", idField);
        addField(form, constraints, "设备名称", nameField);
        addField(form, constraints, "所在位置", locationField);
        content.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = UiStyles.secondaryButton("取消");
        JButton confirm = UiStyles.primaryButton(register ? "注册并生成凭证" : "保存修改");
        buttons.add(cancel);
        buttons.add(confirm);
        content.add(buttons, BorderLayout.SOUTH);

        Result[] result = new Result[1];
        Runnable validate = () -> {
            String id = idField.getText().trim().toUpperCase(java.util.Locale.ROOT);
            String name = nameField.getText().trim();
            String locationText = locationField.getText().trim();
            if (id.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "设备编号和名称不能为空", "输入不完整", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!id.matches("[A-Z0-9\\-]{2,64}")) {
                JOptionPane.showMessageDialog(dialog,
                        "设备编号只能包含大写字母、数字和短横线（2-64 位）",
                        "编号格式错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            result[0] = new Result(id, name, locationText);
            dialog.dispose();
        };
        confirm.addActionListener(event -> validate.run());
        cancel.addActionListener(event -> dialog.dispose());
        idField.addActionListener(event -> validate.run());

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(440, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    private static void addField(
            JPanel form, GridBagConstraints constraints, String caption, JTextField field) {
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.insets = new Insets(8, 0, 2, 0);
        JLabel label = new JLabel(caption);
        label.setForeground(UiStyles.MUTED);
        label.setFont(UiStyles.SMALL);
        form.add(label, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 6, 0);
        field.setPreferredSize(new Dimension(0, 34));
        form.add(field, constraints);
    }
}
