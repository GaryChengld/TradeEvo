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

    /** Creates {@code link} as a hard link to {@code existing}; replaceable in tests. */
    @FunctionalInterface
    interface Linker {
        void link(Path link, Path existing) throws IOException;
    }

    /**
     * Writes a new file atomically and never overwrites. The JSON is written and synced to a temp
     * file in the same directory, then published under the final name with a hard link, which is
     * atomic and create-only: readers see either no file or the complete file, and an existing file
     * is never replaced, even under concurrent writers. On any failure the temp file is removed.
     *
     * <p>The store directory must be on a filesystem with hard links (NTFS, ext4, XFS, APFS, ...).
     * There is deliberately no weaker fallback: elsewhere the write fails with an explanatory error.
     *
     * @throws FileAlreadyExistsException if {@code file} already exists; it is left untouched
     * @throws UncheckedIOException if the write fails, including when hard links are not supported
     */
    static void writeNew(ObjectMapper mapper, Path file, Object value) throws FileAlreadyExistsException {
        writeNew(mapper, file, value, Files::createLink);
    }

    static void writeNew(ObjectMapper mapper, Path file, Object value, Linker linker) throws FileAlreadyExistsException {
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
            publish(linker, temp, file);
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

    private static void publish(Linker linker, Path temp, Path file) throws IOException {
        try {
            linker.link(file, temp);
        } catch (FileAlreadyExistsException e) {
            throw e;
        } catch (UnsupportedOperationException | FileSystemException e) {
            throw new IOException("Could not publish " + file + " with a hard link. The store directory must be on a "
                    + "filesystem that supports hard links, which atomic create-only writes rely on", e);
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
