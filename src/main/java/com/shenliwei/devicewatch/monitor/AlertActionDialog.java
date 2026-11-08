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

/**
 * Small comment dialog used for acknowledging or resolving alerts.
 */
final class AlertActionDialog {
    private AlertActionDialog() {
    }

    /**
     * Shows a modal dialog and returns the comment, or {@code null} if cancelled.
     */
    static String ask(Window owner, String title, String message) {
        JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(UiStyles.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));

        JLabel label = new JLabel("<html>" + message + "</html>");
        label.setForeground(UiStyles.TEXT);
        label.setFont(UiStyles.FONT);
        content.add(label, BorderLayout.NORTH);

        JTextArea commentArea = new JTextArea(4, 32);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        commentArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyles.BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        content.add(commentArea, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = UiStyles.secondaryButton("取消");
        JButton confirm = UiStyles.primaryButton("确定");
        buttons.add(cancel);
        buttons.add(confirm);
        content.add(buttons, BorderLayout.SOUTH);

        String[] result = new String[1];
        confirm.addActionListener(event -> {
            result[0] = commentArea.getText().trim();
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }
}
