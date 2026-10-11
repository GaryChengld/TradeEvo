package com.tradeevo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradeevo.AppTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ApiIntegrationTest {

    static Path storeDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        storeDir = Files.createTempDirectory("tradeevo-store");
        registry.add("tradeevo.store-dir", storeDir::toString);
        registry.add("tradeevo.data-dir", () -> AppTestSupport.EXAMPLE_DATA_DIR.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private ResultActions postJson(String url, Object body) throws Exception {
        String content = body instanceof String text ? text : mapper.writeValueAsString(body);
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ObjectNode exampleConfig(String id) throws IOException {
        ObjectNode config = (ObjectNode) mapper.readTree(AppTestSupport.EXAMPLE_CONFIG.toFile());
        config.put("id", id);
        return config;
    }

    @Test
    void catalogListsRulesAndStrategyClasses() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Compare")))
                .andExpect(jsonPath("$", hasSize(7)));
        mvc.perform(get("/api/rules/Between"))
                .andExpect(jsonPath("$.params[3].name").value("inclusive"))
                .andExpect(jsonPath("$.params[3].defaultValue").value(true));
        mvc.perform(get("/api/rules/Nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/strategy-classes"))
                .andExpect(jsonPath("$[*].className", hasItem("com.tradeevo.strategies.example.ThresholdSignalStrategy")));
    }

    @Test
    void createVersionBacktestAndCompare() throws Exception {
        ObjectNode config = exampleConfig("flow");
        postJson("/api/strategies", config)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/strategies/flow/versions/1"))
                .andExpect(jsonPath("$.version").value(1));

        // v2 tightens the profit target
        ((ObjectNode) config.at("/rules/1/params/rules/0/params")).put("value", 1.0);
        config.remove("id");
        postJson("/api/strategies/flow/versions", config)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/strategies/flow")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/strategies/flow/versions")).andExpect(jsonPath("$", hasSize(2)));

        JsonNode v1 = body(postJson("/api/backtests",
                "{\"strategyId\": \"flow\", \"version\": 1, \"startDate\": \"2024-01-01\", \"endDate\": \"2024-01-31\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.dataDates", hasSize(3)))
                .andExpect(jsonPath("$.metrics.signals").value(3))
                .andExpect(jsonPath("$.manifest.closedDates", hasSize(3)))
                .andExpect(jsonPath("$.manifest.missingDates[0]").value("2024-01-04"))
                .andExpect(jsonPath("$.warnings[0]", containsString("have no data file and were skipped: 2024-01-04"))));
        JsonNode v2 = body(postJson("/api/backtests",
                "{\"strategyId\": \"flow\", \"startDate\": \"2024-01-01\", \"endDate\": \"2024-01-31\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.strategy.version").value(2)));

        String id1 = v1.get("runId").asText();
        String id2 = v2.get("runId").asText();
        mvc.perform(get("/api/backtests/" + id1)).andExpect(jsonPath("$.metrics.signals").value(3));
        mvc.perform(get("/api/backtests").param("strategyId", "flow")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/backtests/" + id1))
                .andExpect(jsonPath("$.manifest.dataHash").value(v1.at("/manifest/dataHash").asText()))
                .andExpect(jsonPath("$.manifest.assumptions.model").value("signal-only"));
        mvc.perform(get("/api/backtests/compare").param("runIds", id1 + "," + id2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricNames", hasItem("valueChangeTotal")))
                .andExpect(jsonPath("$.comparable").value(true))
                .andExpect(jsonPath("$.runs[0].version").value(1))
                .andExpect(jsonPath("$.runs[1].version").value(2));
        mvc.perform(get("/api/backtests/compare").param("runIds", id1 + "," + id2).param("strict", "true"))
                .andExpect(status().isOk());

        // A different period is not comparable: reported, and refused when strict.
        String id3 = body(postJson("/api/backtests",
                "{\"strategyId\": \"flow\", \"startDate\": \"2024-01-01\", \"endDate\": \"2024-01-02\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifest.dataFiles", hasSize(1)))
                .andExpect(jsonPath("$.manifest.engineVersion").exists())
                .andExpect(jsonPath("$.ruleStats.entry").exists())).get("runId").asText();
        mvc.perform(get("/api/backtests/compare").param("runIds", id1 + "," + id3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparable").value(false))
                .andExpect(jsonPath("$.issues[*].field", hasItem("endDate")))
                .andExpect(jsonPath("$.issues[*].field", hasItem("dataHash")));
        mvc.perform(get("/api/backtests/compare").param("runIds", id1 + "," + id3).param("strict", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.comparable").value(false));
    }

    @Test
    void evaluatesInlineAndStoredStrategiesWithoutStoringAnything() throws Exception {
        postJson("/api/strategies", exampleConfig("live")).andExpect(status().isCreated());
        long runsBefore = countRuns();
        String data = Files.readString(AppTestSupport.EXAMPLE_DATA_DIR.resolve("example/2024-01-02.json"));

        postJson("/api/evaluations", "{\"strategy\": " + exampleConfig("inline-test") + ", \"data\": " + data + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("EVALUATION"))
                .andExpect(jsonPath("$.metrics.records").value(5))
                .andExpect(jsonPath("$.metrics.signals").value(1));
        postJson("/api/evaluations", "{\"strategy\": {\"strategyId\": \"live\", \"version\": 1}, \"data\": " + data + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy.id").value("live"))
                .andExpect(jsonPath("$.metrics.signals").value(1));

        assertThat(countRuns()).isEqualTo(runsBefore);
    }

    @Test
    void reportsErrors() throws Exception {
        ObjectNode invalid = exampleConfig("broken");
        ((ObjectNode) invalid.at("/rules/0")).put("rule", "Nope");
        postJson("/api/strategies", invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("rules[0].rule: unknown rule type 'Nope'"));

        postJson("/api/strategies", "{\"id\": \"x\", \"strategyClas\": \"typo\"}")
                .andExpect(status().isBadRequest());

        postJson("/api/strategies", exampleConfig("dup")).andExpect(status().isCreated());
        postJson("/api/strategies", exampleConfig("dup")).andExpect(status().isConflict());

        mvc.perform(get("/api/strategies/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/backtests/missing")).andExpect(status().isNotFound());

        postJson("/api/backtests", "{\"strategyId\": \"dup\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("startDate: must not be null")));
        postJson("/api/backtests", "{\"strategyId\": \"dup\", \"startDate\": \"2030-01-01\", \"endDate\": \"2030-01-31\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("No session data")));

        postJson("/api/evaluations", "{\"strategy\": {\"strategyId\": \"dup\"}, \"data\": {\"rows\": []}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error", containsString("'records' array")));
        postJson("/api/evaluations", "{\"strategy\": {\"strategyId\": \"dup\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("data: required"));
        postJson("/api/evaluations", "{\"strategy\": {}, \"data\": {}}").andExpect(status().isBadRequest());
    }

    private static long countRuns() throws IOException {
        Path runs = storeDir.resolve("runs");
        if (!Files.isDirectory(runs)) {
            return 0;
        }
        try (Stream<Path> entries = Files.list(runs)) {
            return entries.count();
        }
    }
}
