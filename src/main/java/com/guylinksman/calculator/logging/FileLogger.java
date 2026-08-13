package com.guylinksman.calculator.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Appends failure logs to a file, creating its parent directory (and the file
 * itself) on first use. A failure to write the log is reported to stderr but never
 * propagated - losing the log must not crash the program or hide the original
 * failure that was being logged in the first place.
 */
public final class FileLogger implements Logger {

    private final Path file;

    public FileLogger(Path file) {
        this.file = file;
    }

    @Override
    public void logFailure(FailureEvent event) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, LogFormatter.format(event), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("warning: failed to write failure log to " + file + ": " + e.getMessage());
        }
    }
}
