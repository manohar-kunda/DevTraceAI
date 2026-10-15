package com.devtrace.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class EnvLoader {

    private EnvLoader() {}

    /**
     * Loads key-value pairs from a local .env file into System properties
     * if not already present in the operating system environment or existing System properties.
     */
    public static void loadLocalEnv() {
        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (IOException ignored) {
            // Graceful fallback to OS environment variables
        }
    }
}
