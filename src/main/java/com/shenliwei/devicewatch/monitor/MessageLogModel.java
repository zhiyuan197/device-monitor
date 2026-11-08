package com.shenliwei.devicewatch.monitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bounded, thread-safe buffer of observed protocol messages.
 */
public final class MessageLogModel {

    public static final int MAX_EVENTS = 500;

    private final Deque<MessageEvent> events = new ArrayDeque<>();
    private final List<Consumer<MessageEvent>> listeners =
            new CopyOnWriteArrayList<>();

    public synchronized void add(MessageEvent event) {
        events.addLast(event);
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
        for (Consumer<MessageEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    public synchronized List<MessageEvent> snapshot() {
        return new ArrayList<>(events);
    }

    public synchronized void clear() {
        events.clear();
    }

    public void addListener(Consumer<MessageEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<MessageEvent> listener) {
        listeners.remove(listener);
    }
}
