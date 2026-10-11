package com.tradeevo.store;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Small helpers for reading and writing pretty-printed JSON files. */
final class JsonFiles {

    private JsonFiles() {
    }

    /** Writes a new file; fails with {@link FileAlreadyExistsException} rather than overwriting. */
    static void writeNew(ObjectMapper mapper, Path file, Object value) throws FileAlreadyExistsException {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, value);
            }
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }

    static <T> T read(ObjectMapper mapper, Path file, Class<T> type) {
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
