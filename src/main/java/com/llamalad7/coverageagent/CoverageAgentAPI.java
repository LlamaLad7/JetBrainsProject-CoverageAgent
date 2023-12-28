package com.llamalad7.coverageagent;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CoverageAgentAPI {
    private static final String FILE = "executedClasses.txt";
    private static final Set<String> executedClasses = new LinkedHashSet<>();
    private static int lastSize = 0;

    @SuppressWarnings("unused")
    @ApiStatus.Internal
    public static void markExecutedClass(Class<?> clazz) {
        synchronized (executedClasses) {
            // For now, we ignore the classloader, but if we wanted to use it later we could.
            executedClasses.add(clazz.getName());
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(CoverageAgentAPI::saveFile));
        new Timer(true).scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                saveFile();
            }
        }, 0, 5000);
    }

    private static void saveFile() {
        List<String> currentClasses;
        synchronized (executedClasses) {
            currentClasses = executedClasses.stream().toList();
        }
        var currentSize = currentClasses.size();
        if (currentSize != lastSize) {
            lastSize = currentSize;
            try {
                Files.write(Path.of(FILE), executedClasses);
            } catch (IOException e) {
                throw new RuntimeException("Failed to dump classes list: ", e);
            }
        }
    }
}
