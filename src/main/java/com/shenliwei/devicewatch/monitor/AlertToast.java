package com.shenliwei.devicewatch.monitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Small top-right toast used for alert notifications. Slides in, stays
 * visible for a few seconds, then slides out and removes itself.
 */
final class AlertToast {
    private static final int VISIBLE_MILLIS = 4_500;
    private static final int SLIDE_STEPS = 14;
    private static final int SLIDE_INTERVAL_MILLIS = 12;

    private AlertToast() {
    }

    static void show(JFrame frame, String text) {
        Tone.play();
        SwingUtilities.invokeLater(() -> {
            JLayeredPane layered = frame.getLayeredPane();
            for (Component existing : layered.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
                if (existing instanceof JComponent component
                        && component.getClientProperty("AlertToast") == Boolean.TRUE) {
                    layered.remove(existing);
                }
            }

            JPanel toast = new JPanel(new BorderLayout());
            JLabel label = new JLabel(text);
            label.setForeground(Color.WHITE);
            label.setFont(UiStyles.FONT_BOLD);
            label.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            toast.add(label, BorderLayout.CENTER);
            toast.setBackground(new Color(15, 23, 42));
            toast.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 45)));
            toast.putClientProperty("AlertToast", Boolean.TRUE);

            Dimension size = toast.getPreferredSize();
            int margin = 18;
            int targetX = Math.max(margin, frame.getContentPane().getWidth() - size.width - margin);
            int targetY = margin;
            int startX = targetX + size.width + 48;
            toast.setBounds(startX, targetY, size.width, size.height);
            layered.add(toast, JLayeredPane.POPUP_LAYER);

            int slideDistance = startX - targetX;
            int[] step = {0};
            Timer slideIn = new Timer(SLIDE_INTERVAL_MILLIS, event -> {
                step[0]++;
                int x = startX - slideDistance * Math.min(step[0], SLIDE_STEPS) / SLIDE_STEPS;
                toast.setLocation(x, targetY);
                if (step[0] >= SLIDE_STEPS) {
                    ((Timer) event.getSource()).stop();
                }
            });
            slideIn.start();

            Timer dismiss = new Timer(VISIBLE_MILLIS, event -> {
                Timer slideOut = new Timer(SLIDE_INTERVAL_MILLIS, slideEvent -> {
                    toast.setLocation(toast.getX() + 26, targetY);
                    if (toast.getX() > frame.getContentPane().getWidth()) {
                        ((Timer) slideEvent.getSource()).stop();
                        layered.remove(toast);
                        layered.repaint();
                    }
                });
                slideOut.start();
            });
            dismiss.setRepeats(false);
            dismiss.start();
        });
    }
}
