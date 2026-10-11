package com.tradeevo.core.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.core.Identifiers;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.error.InvalidDataException;
import com.tradeevo.core.error.NotFoundException;

/**
 * Reads raw data files laid out as {@code {dataDir}/{dataset}/{yyyy-MM-dd}.json}. Contents are
 * returned as raw JSON; only the strategy knows their format.
 *
 * <p>Coverage convention: a data provider can write a file for every calendar day, marking
 * non-trading days with {@code {"session": "closed"}} (other fields, such as a reason, are allowed).
 * Closed days are skipped and recorded as closed. Days with no file are skipped too, but recorded as
 * missing, because an unmarked gap may be an accidentally missing trading day.
 */
public class DataRepository {

    /** Upper bound on a requested range, to catch typos such as a wrong year. */
    static final long MAX_DAYS = 366L * 50;

    private final Path dataDir;
    private final ObjectMapper mapper;

    public DataRepository(Path dataDir, ObjectMapper mapper) {
        this.dataDir = dataDir;
        this.mapper = mapper;
    }

    public Path dataDir() {
        return dataDir;
    }

    /** The JSON field and value that mark a day without a trading session. */
    public static final String SESSION_FIELD = "session";
    public static final String CLOSED = "closed";

    /**
     * Loads the session files for every date in {@code [from, to]}, in date order, and records which
     * dates were closed or had no file.
     *
     * @throws NotFoundException if the range has no session file
     * @throws InvalidDataException if a file is not valid JSON
     */
    public DataSet load(String dataset, LocalDate from, LocalDate to) {
        if (!Identifiers.isValid(dataset)) {
            throw new BadRequestException("dataset '" + dataset + "' is invalid: " + Identifiers.RULE);
        }
        if (from == null || to == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("endDate " + to + " is before startDate " + from);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BadRequestException("date range " + from + " to " + to + " is longer than " + MAX_DAYS + " days");
        }
        Path directory = dataDir.resolve(dataset);
        List<DataFile> files = new ArrayList<>();
        List<LocalDate> closed = new ArrayList<>();
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Path file = directory.resolve(date + ".json");
            if (!Files.isRegularFile(file)) {
                missing.add(date);
                continue;
            }
            JsonNode content = read(file);
            if (isClosedMarker(content)) {
                closed.add(date);
            } else {
                files.add(new DataFile(date, content));
            }
        }
        if (files.isEmpty()) {
            throw new NotFoundException("No session data for dataset '" + dataset + "' from " + from + " to " + to
                    + " (" + closed.size() + " closed, " + missing.size() + " without a file; expected "
                    + directory.resolve("yyyy-MM-dd.json") + ")");
        }
        return new DataSet(files, new DataCoverage(closed, missing));
    }

    /** Whether the content is a closed-day marker: an object with {@code "session": "closed"}. */
    public static boolean isClosedMarker(JsonNode content) {
        JsonNode session = content.get(SESSION_FIELD);
        return content.isObject() && session != null && session.isTextual() && CLOSED.equals(session.textValue());
    }

    private JsonNode read(Path file) {
        try {
            JsonNode content = mapper.readTree(file.toFile());
            if (content == null || content.isMissingNode()) {
                throw new InvalidDataException("Data file " + file + " is empty");
            }
            return content;
        } catch (JsonProcessingException e) {
            throw new InvalidDataException("Data file " + file + " is not valid JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read data file " + file, e);
        }
    }
}
