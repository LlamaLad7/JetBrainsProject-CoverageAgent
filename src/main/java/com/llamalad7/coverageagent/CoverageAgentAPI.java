package com.llamalad7.coverageagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoverageAgentAPI {
    private static final String FILE = "executedClasses.txt";
    private static final Set<String> executedClasses = new LinkedHashSet<>();
    private static final AtomicBoolean needsToDump = new AtomicBoolean(false);

    static synchronized void markExecutedClass(Class<?> clazz) {
        // For now, we ignore the classloader, but if we wanted to use it later we could.
        if (executedClasses.add(clazz.getName())) {
            needsToDump.set(true);
        }
    }

    static {
        // Save the file on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(CoverageAgentAPI::saveFile));
        // Save the file every 5 seconds
        new Timer(true).scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveFile();
            }
        }, 0, 5000);
    }

    private static void saveFile() {
        if (needsToDump.getAndSet(false)) {
            List<String> currentClasses;
            // Only block for as long as needed, certainly not for the actual IO
            synchronized (CoverageAgentAPI.class) {
                currentClasses = executedClasses.stream().toList();
            }
            try {
                Files.write(Path.of(FILE), currentClasses);
            } catch (IOException e) {
                throw new RuntimeException("Failed to dump classes list: ", e);
            }
        }
    }
}
