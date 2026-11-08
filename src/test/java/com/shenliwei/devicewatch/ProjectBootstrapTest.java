package com.shenliwei.devicewatch;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ProjectBootstrapTest {

    @Test
    void bootstrapMessageIsAvailable() {
        assertFalse(Main.BOOTSTRAP_MESSAGE.isBlank());
    }
}

