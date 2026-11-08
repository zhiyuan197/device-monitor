package com.shenliwei.devicewatch.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shenliwei.devicewatch.protocol.MessageObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class MessageLogModelTest {

    @Test
    void keepsEventsInOrderAndNotifiesListeners() {
        MessageLogModel model = new MessageLogModel();
        List<MessageEvent> received = new ArrayList<>();
        model.addListener(received::add);
        model.add(new MessageEvent(
                MessageObserver.Direction.SENT, "AUTH", 120, 1_000L));
        model.add(new MessageEvent(
                MessageObserver.Direction.RECEIVED, "AUTH_OK", 90, 1_001L));
        List<MessageEvent> snapshot = model.snapshot();
        assertEquals(2, snapshot.size());
        assertEquals("AUTH", snapshot.get(0).type());
        assertEquals("AUTH_OK", snapshot.get(1).type());
        assertEquals(2, received.size());
    }

    @Test
    void capsEventsAt500DroppingOldest() {
        MessageLogModel model = new MessageLogModel();
        for (int i = 0; i < 501; i++) {
            model.add(new MessageEvent(
                    MessageObserver.Direction.SENT, "T-" + i, 1, i));
        }
        List<MessageEvent> snapshot = model.snapshot();
        assertEquals(500, snapshot.size());
        assertEquals("T-1", snapshot.get(0).type());
        assertEquals("T-500", snapshot.get(499).type());
    }

    @Test
    void clearRemovesAllEvents() {
        MessageLogModel model = new MessageLogModel();
        model.add(new MessageEvent(
                MessageObserver.Direction.SENT, "AUTH", 120, 1_000L));
        model.clear();
        assertTrue(model.snapshot().isEmpty());
    }

    @Test
    void removeListenerStopsNotifications() {
        MessageLogModel model = new MessageLogModel();
        List<MessageEvent> received = new ArrayList<>();
        Consumer<MessageEvent> listener = received::add;
        model.addListener(listener);
        model.removeListener(listener);
        model.add(new MessageEvent(
                MessageObserver.Direction.SENT, "AUTH", 120, 1_000L));
        assertTrue(received.isEmpty());
    }
}
