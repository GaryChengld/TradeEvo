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

    /**
     * Loads the files for every date in {@code [from, to]}, in date order. Dates without a file are
     * skipped.
     *
     * @throws NotFoundException if no file exists in the range
     * @throws InvalidDataException if a file is not valid JSON
     */
    public List<DataFile> load(String dataset, LocalDate from, LocalDate to) {
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
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Path file = directory.resolve(date + ".json");
            if (Files.isRegularFile(file)) {
                files.add(new DataFile(date, read(file)));
            }
        }
        if (files.isEmpty()) {
            throw new NotFoundException("No data files for dataset '" + dataset + "' from " + from + " to " + to
                    + " (expected " + directory.resolve("yyyy-MM-dd.json") + ")");
        }
        return files;
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
