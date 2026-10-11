package com.tradeevo.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tradeevo.TestSupport;

class JsonFilesTest {

    @TempDir
    Path dir;

    /** Serialization fails partway: the first property is written, the second throws. */
    public static class Exploding {
        public String getA() {
            return "written first";
        }

        public String getB() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void writesCompleteFileAndLeavesNoTempFiles() throws IOException {
        Path file = dir.resolve("sub/v1.json");

        JsonFiles.writeNew(TestSupport.MAPPER, file, Map.of("k", 1));

        assertThat(TestSupport.MAPPER.readTree(file.toFile()).get("k").intValue()).isEqualTo(1);
        assertThat(list(file.getParent())).containsExactly(file);
    }

    @Test
    void failedSerializationLeavesNeitherTargetNorTempFile() throws IOException {
        Path file = dir.resolve("v1.json");

        assertThatThrownBy(() -> JsonFiles.writeNew(TestSupport.MAPPER, file, new Exploding()))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(list(dir)).isEmpty();
    }

    @Test
    void neverOverwritesAnExistingFile() throws IOException {
        Path file = dir.resolve("v1.json");
        Files.writeString(file, "{\"original\": true}");

        assertThatThrownBy(() -> JsonFiles.writeNew(TestSupport.MAPPER, file, Map.of("k", 2)))
                .isInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(file)).isEqualTo("{\"original\": true}");
        assertThat(list(dir)).containsExactly(file);
    }

    private static java.util.List<Path> list(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.toList();
        }
    }
}
