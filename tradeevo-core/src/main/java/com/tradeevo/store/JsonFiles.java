package com.tradeevo.store;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small helpers for reading and writing pretty-printed JSON files. */
final class JsonFiles {

    private JsonFiles() {
    }

    /**
     * Writes a new file atomically and never overwrites: the JSON is written and synced to a temp
     * file in the same directory, then published under the final name with a hard link (atomic and
     * create-only), falling back to a non-replacing move where hard links are unsupported. Readers
     * therefore see either no file or the complete file. On any failure the temp file is removed.
     *
     * @throws FileAlreadyExistsException if {@code file} already exists; it is left untouched
     */
    static void writeNew(ObjectMapper mapper, Path file, Object value) throws FileAlreadyExistsException {
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                throw new FileAlreadyExistsException(file.toString());
            }
            temp = file.resolveSibling("." + file.getFileName() + ".tmp-" + UUID.randomUUID());
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                OutputStream out = Channels.newOutputStream(channel);
                mapper.writerWithDefaultPrettyPrinter()
                        .without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                        .writeValue(out, value);
                out.flush();
                channel.force(true);
            }
            publish(temp, file);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort; temp names never match files the stores read
                }
            }
        }
    }

    private static void publish(Path temp, Path file) throws IOException {
        try {
            Files.createLink(file, temp);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (UnsupportedOperationException | FileSystemException e) {
            // No hard links here: a move without REPLACE_EXISTING is still create-only.
            Files.move(temp, file);
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
