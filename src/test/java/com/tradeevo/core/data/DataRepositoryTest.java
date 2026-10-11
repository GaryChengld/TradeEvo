package com.tradeevo.core.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tradeevo.TestSupport;
import com.tradeevo.core.error.BadRequestException;
import com.tradeevo.core.error.InvalidDataException;
import com.tradeevo.core.error.NotFoundException;

class DataRepositoryTest {

    @TempDir
    Path dataDir;

    private DataRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new DataRepository(dataDir, TestSupport.MAPPER);
        Path dataset = Files.createDirectories(dataDir.resolve("ds"));
        Files.writeString(dataset.resolve("2024-01-02.json"), "{\"day\": 2}");
        Files.writeString(dataset.resolve("2024-01-04.json"), "{\"day\": 4}");
        Files.writeString(dataset.resolve("2024-02-01.json"), "{not json");
        Files.writeString(dataset.resolve("2024-03-01.json"), "");
    }

    @Test
    void loadsFilesInRangeInDateOrderSkippingMissingDates() {
        List<DataFile> files = repository.load("ds", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10));

        assertThat(files).extracting(DataFile::date).containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 4));
        assertThat(files.get(1).content().get("day").intValue()).isEqualTo(4);
    }

    @Test
    void noFilesInRangeIsNotFound() {
        assertThatThrownBy(() -> repository.load("ds", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31)))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("No data files for dataset 'ds'");
        assertThatThrownBy(() -> repository.load("other", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void invalidOrEmptyJsonNamesTheFile() {
        assertThatThrownBy(() -> repository.load("ds", LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 1)))
                .isInstanceOf(InvalidDataException.class).hasMessageContaining("2024-02-01.json is not valid JSON");
        assertThatThrownBy(() -> repository.load("ds", LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 1)))
                .isInstanceOf(InvalidDataException.class).hasMessageContaining("2024-03-01.json is empty");
    }

    @Test
    void rejectsBadDatasetsAndRanges() {
        LocalDate day = LocalDate.of(2024, 1, 2);
        assertThatThrownBy(() -> repository.load("../ds", day, day)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> repository.load("ds", day, day.minusDays(1)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("is before startDate");
        assertThatThrownBy(() -> repository.load("ds", day, day.plusYears(100)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("longer than");
    }
}
