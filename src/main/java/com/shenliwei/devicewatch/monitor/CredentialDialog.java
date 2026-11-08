package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

final class CredentialDialog {
    private CredentialDialog() {
    }

    static void show(Window owner, String deviceId, String credential) {
        JDialog dialog = new JDialog(owner, "设备接入凭证",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UiStyles.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 16, 18));

        JLabel title = UiStyles.pageTitle("设备 " + deviceId + " 的接入凭证");
        title.setFont(UiStyles.SECTION);
        content.add(title, BorderLayout.NORTH);

        JLabel hint = new JLabel("凭证仅显示一次，请立即复制并妥善保存。");
        hint.setForeground(UiStyles.MUTED);
        hint.setFont(UiStyles.FONT);
        content.add(hint, BorderLayout.CENTER);

        JTextArea credentialArea = new JTextArea(credential);
        credentialArea.setEditable(false);
        credentialArea.setFont(UiStyles.FONT.deriveFont(java.awt.Font.BOLD, 15f));
        credentialArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyles.BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        content.add(credentialArea, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton close = UiStyles.primaryButton("我已保存");
        close.addActionListener(event -> dialog.dispose());
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(480, 220));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
