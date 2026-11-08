package com.shenliwei.devicewatch.monitor;

import java.io.File;
import java.io.IOException;

/**
 * Stops the background demo server and simulators so the app can close
 * the whole environment on exit, like an installed application.
 */
final class DemoEnvironment {
    private DemoEnvironment() {
    }

    static void stop() {
        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-WindowStyle",
                        "Hidden",
                        "-File",
                        "scripts" + File.separator + "stop-demo.ps1");
                builder.directory(new File(System.getProperty("user.dir")));
                builder.redirectErrorStream(true);
                builder.start();
            } catch (IOException ignored) {
                // The demo processes keep running; stop-demo.ps1 remains available.
            }
        }, "demo-environment-stop");
        thread.setDaemon(true);
        thread.start();
    }
}
