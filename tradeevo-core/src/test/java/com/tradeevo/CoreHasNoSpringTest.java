package com.tradeevo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * tradeevo-core must stay usable without Spring. Its pom has no Spring dependency, so imports would
 * not compile; this also catches fully qualified references and annotations in comments that suggest
 * otherwise.
 */
class CoreHasNoSpringTest {

    @Test
    void coreSourcesDoNotReferenceSpring() throws IOException {
        Path sources = Path.of("src", "main", "java");
        assertThat(sources).isDirectory();
        try (Stream<Path> files = Files.walk(sources)) {
            List<Path> offenders = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> read(file).contains("org.springframework"))
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
