package com.shenliwei.devicewatch.monitor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Short two-tone alert chime played off the event dispatch thread.
 * Failures are ignored: the visual toast still carries the notification.
 */
final class Tone {
    private Tone() {
    }

    static void play() {
        if (!ThemeManager.preference().alertSoundEnabled()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                for (double frequency : new double[] {880.0, 660.0}) {
                    beep(frequency, 160);
                }
            } catch (Exception ignored) {
                // Audio device unavailable: keep the app running silently.
            }
        }, "alert-tone");
        thread.setDaemon(true);
        thread.start();
    }

    private static void beep(double frequency, int millis) throws Exception {
        float sampleRate = 16_000f;
        AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
        int length = (int) (sampleRate * millis / 1000.0);
        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            double angle = 2.0 * Math.PI * frequency * i / sampleRate;
            buffer[i] = (byte) (Math.sin(angle) * 90);
        }
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, length);
            line.start();
            line.write(buffer, 0, length);
            line.drain();
        }
    }
}
