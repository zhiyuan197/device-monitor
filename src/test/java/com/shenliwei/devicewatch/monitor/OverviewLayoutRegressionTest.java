package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the overview page: no component may be laid out
 * with a negative size, which previously made the chart cards collapse
 * into the table card and paint on top of it.
 */
class OverviewLayoutRegressionTest {

    @Test
    void overviewNeverGivesComponentsNegativeBounds() throws Exception {
        List<String> broken = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            DashboardFrame frame =
                    new DashboardFrame("127.0.0.1", 5050, "operator", new char[] {'p'});
            frame.setSize(1040, 680);
            frame.setVisible(true);
            frame.validate();
            for (String page : new String[] {
                DashboardFrame.PAGE_OVERVIEW,
                DashboardFrame.PAGE_ALERTS,
                DashboardFrame.PAGE_DEVICES,
                DashboardFrame.PAGE_REPORTS,
                DashboardFrame.PAGE_SYSTEM
            }) {
                frame.showPageForCapture(page);
                frame.validate();
                Component pagePanel = findShownPage(frame.getContentPane());
                collectNegative(pagePanel, page, broken);
            }
            frame.dispose();
        });
        assertEquals(List.of(), broken,
                "page components with negative bounds: " + broken);
    }

    private static Component findShownPage(Component root) {
        if (root instanceof PagePanel && root.isVisible() && root.getWidth() > 0) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findShownPage(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void collectNegative(Component component, String page, List<String> broken) {
        Rectangle bounds = component.getBounds();
        if (bounds.width < 0 || bounds.height < 0) {
            broken.add(page + ":" + component.getClass().getSimpleName()
                    + "=" + bounds.width + "x" + bounds.height);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectNegative(child, page, broken);
            }
        }
    }

}
