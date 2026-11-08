package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrendChartPanelTest {

    @Test
    void nearestIndexSnapsToClosestSampleAndClampsAtEdges() {
        int left = 44;
        int width = 200;
        assertEquals(0, TrendChartPanel.nearestIndex(left - 10, 5, left, width));
        assertEquals(0, TrendChartPanel.nearestIndex(left + 5, 5, left, width));
        assertEquals(1, TrendChartPanel.nearestIndex(left + 70, 5, left, width));
        assertEquals(3, TrendChartPanel.nearestIndex(left + 165, 5, left, width));
        assertEquals(4, TrendChartPanel.nearestIndex(left + width + 10, 5, left, width));
    }
}
