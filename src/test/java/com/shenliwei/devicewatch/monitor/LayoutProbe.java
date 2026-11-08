package com.shenliwei.devicewatch.monitor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

/**
 * Temporary diagnostic: prints layout bounds of the overview page so
 * visual overlap can be identified from real geometry instead of guesswork.
 */
public final class LayoutProbe {
    private LayoutProbe() {
    }

    public static void main(String[] args) throws Exception {
        UiStyles.apply();
        DashboardFrame frame =
                new DashboardFrame("127.0.0.1", 5050, "operator", new char[] {'p'});
        int width = args.length > 0 ? Integer.parseInt(args[0]) : 1426;
        int height = args.length > 1 ? Integer.parseInt(args[1]) : 822;
        frame.setSize(width, height);
        SwingUtilities.invokeAndWait(() -> {
            frame.setVisible(true);
            frame.validate();
        });

        for (String page : new String[] {
            DashboardFrame.PAGE_OVERVIEW,
            DashboardFrame.PAGE_ALERTS,
            DashboardFrame.PAGE_DEVICES,
            DashboardFrame.PAGE_REPORTS,
            DashboardFrame.PAGE_SYSTEM
        }) {
            SwingUtilities.invokeAndWait(() -> {
                frame.showPageForCapture(page);
                frame.validate();
            });
            System.out.println("=== PAGE " + page + " ===");
            SwingUtilities.invokeAndWait(() ->
                    printTree(frame.getContentPane(), 0));
        }

        System.out.println("=== BOUNDS ===");
        frame.dispose();
        System.exit(0);
    }

    private static void printTree(Component component, int depth) {
        Rectangle bounds = component.getBounds();
        java.awt.Dimension pref = component.getPreferredSize();
        boolean broken = bounds.width < 0 || bounds.height < 0;
        System.out.printf("%s%s [%d,%d %dx%d] pref=%dx%d%s%s%n",
                "  ".repeat(depth),
                component.getClass().getSimpleName(),
                bounds.x, bounds.y, bounds.width, bounds.height,
                pref.width, pref.height,
                component.isOpaque() ? "" : " (transparent)",
                broken ? "  <-- NEGATIVE SIZE" : "");
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                printTree(child, depth + 1);
            }
        }
    }

    private static void reportOverlaps(Component component) {
        if (!(component instanceof Container container)) {
            return;
        }
        Component[] children = container.getComponents();
        for (int i = 0; i < children.length; i++) {
            for (int j = i + 1; j < children.length; j++) {
                Rectangle a = children[i].getBounds();
                Rectangle b = children[j].getBounds();
                Rectangle intersection = a.intersection(b);
                if (!intersection.isEmpty() && intersection.width > 4
                        && intersection.height > 4) {
                    System.out.printf("%s ∩ %s = %dx%d @ (%d,%d)%n",
                            children[i].getClass().getSimpleName(),
                            children[j].getClass().getSimpleName(),
                            intersection.width, intersection.height,
                            intersection.x, intersection.y);
                }
            }
            reportOverlaps(children[i]);
        }
    }
}
