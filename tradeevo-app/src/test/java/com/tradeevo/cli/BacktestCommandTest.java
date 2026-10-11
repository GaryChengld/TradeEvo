package com.tradeevo.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeevo.AppTestSupport;
import com.tradeevo.service.StrategyService;

import picocli.CommandLine;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class BacktestCommandTest {

    static Path storeDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        storeDir = Files.createTempDirectory("tradeevo-cli-store");
        registry.add("tradeevo.store-dir", storeDir::toString);
        registry.add("tradeevo.data-dir", () -> AppTestSupport.EXAMPLE_DATA_DIR.toAbsolutePath().toString());
    }

    @TempDir
    Path out;

    @Autowired
    ObjectProvider<BacktestCommand> commands;

    @Autowired
    StrategyService strategies;

    @Autowired
    ObjectMapper mapper;

    private final StringWriter stdout = new StringWriter();
    private final StringWriter stderr = new StringWriter();

    private int execute(String... args) {
        CommandLine commandLine = new CommandLine(commands.getObject());
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));
        return commandLine.execute(args);
    }

    @Test
    void runsAConfigFileAndWritesTheResult() throws IOException {
        int storedRunsBefore = list(storeDir.resolve("runs")).size();
        int exit = execute("--config", AppTestSupport.EXAMPLE_CONFIG.toString(), "--from", "2024-01-01", "--to", "2024-01-31",
                "--data-dir", AppTestSupport.EXAMPLE_DATA_DIR.toString(), "--out", out.toString());

        assertThat(exit).as(stderr.toString()).isZero();
        assertThat(stdout.toString()).contains("COMPLETED", "threshold-signal", "3 file(s), 3 closed, 25 missing",
                "signals", "Warning   25 date(s) have no data file");
        List<Path> results = list(out);
        assertThat(results).hasSize(1);
        JsonNode result = mapper.readTree(results.getFirst().toFile());
        assertThat(result.at("/metrics/signals").intValue()).isEqualTo(3);
        assertThat(list(storeDir.resolve("runs"))).as("config-file runs are not stored").hasSize(storedRunsBefore);
    }

    @Test
    void runsAStoredStrategyAndStoresTheRun() throws IOException {
        strategies.create(AppTestSupport.exampleConfig().withIdAndVersion("cli-stored", null));
        int storedRunsBefore = list(storeDir.resolve("runs")).size();

        int exit = execute("--strategy-id", "cli-stored", "--strategy-version", "1",
                "--from", "2024-01-02", "--to", "2024-01-02", "--out", out.toString());

        assertThat(exit).as(stderr.toString()).isZero();
        assertThat(stdout.toString()).contains("cli-stored v1", "1 file(s)");
        assertThat(list(storeDir.resolve("runs"))).hasSize(storedRunsBefore + 1);
    }

    @Test
    void invalidInputExitsWithCode2() {
        assertThat(execute("--strategy-id", "missing", "--strategy-version", "3", "--from", "2024-01-01", "--to", "2024-01-31")).isEqualTo(2);
        assertThat(stderr.toString()).contains("Strategy 'missing' not found");

        assertThat(execute("--config", AppTestSupport.EXAMPLE_CONFIG.toString(), "--from", "2030-01-01", "--to", "2030-01-02",
                "--out", out.toString())).isEqualTo(2);
        assertThat(stderr.toString()).contains("No session data");

        assertThat(execute("--from", "2024-01-01", "--to", "2024-01-31")).as("missing strategy source").isEqualTo(2);
    }

    private static List<Path> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.toList();
        }
    }
}
