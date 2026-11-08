package com.shenliwei.devicewatch.monitor;

import javax.swing.JPanel;

/**
 * Base class for dashboard pages. The frame calls the hooks on the EDT.
 */
abstract class PagePanel extends JPanel {
    protected final DashboardFrame frame;

    PagePanel(DashboardFrame frame) {
        this.frame = frame;
        setOpaque(false);
    }

    void onDevicesChanged() {
    }

    void onAlertsChanged() {
    }

    void onConnectionChanged(boolean connected) {
    }

    void onServiceChecksChanged() {
    }
}
