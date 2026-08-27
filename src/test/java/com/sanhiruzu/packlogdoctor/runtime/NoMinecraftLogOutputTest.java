package com.sanhiruzu.packlogdoctor.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoMinecraftLogOutputTest {
    @Test
    void packLogDoctorDoesNotWriteToMinecraftLogOrStdErr() throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/sanhiruzu/packlogdoctor");
        List<String> forbidden = List.of(
                "System.err.println",
                "LogManager.getLogger(\"PackLogDoctor\")",
                "LOGGER.warn",
                "LOGGER.error",
                "LOGGER.info"
        );

        try (var paths = Files.walk(sourceRoot)) {
            List<String> violations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> forbidden.stream()
                            .filter(token -> contains(path, token))
                            .map(token -> sourceRoot.relativize(path) + " contains " + token))
                    .toList();

            assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
        }
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
