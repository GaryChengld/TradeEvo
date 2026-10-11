package com.tradeevo.core.engine;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradeevo.core.strategy.RunMode;
import com.tradeevo.core.strategy.StrategyConfig;

class RunComparatorTest {

    private static final LocalDate JAN_1 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2024, 1, 31);

    private static RunRecord run(String id, int version, LocalDate end, String dataHash, String commit,
            JsonNode assumptions, RunStatus status, Map<String, Number> metrics) {
        return run(id, version, end, dataHash, commit, assumptions, status, metrics, List.of());
    }

    private static RunRecord run(String id, int version, LocalDate end, String dataHash, String commit,
            JsonNode assumptions, RunStatus status, Map<String, Number> metrics, List<LocalDate> missing) {
        RunManifest manifest = new RunManifest("1.0", commit, false, "c", "sh" + version, "ds", dataHash, List.of(),
                List.of(), missing, assumptions);
        return new RunRecord(id, status, RunMode.BACKTEST, new StrategyConfig("s", version, null, "c", null, null, List.of()),
                JAN_1, end, List.of(), metrics, null, List.of(), null, Instant.EPOCH, Instant.EPOCH, Map.of(), manifest);
    }

    private static RunRecord run(String id, int version, Map<String, Number> metrics) {
        return run(id, version, JAN_31, "dh", "abc", json("{'costs': 'none'}"), RunStatus.COMPLETED, metrics);
    }

    @Test
    void sameDataPeriodEngineAndAssumptionsAreComparable() {
        RunComparison comparison = RunComparator.compare(List.of(run("a", 1, Map.of("x", 1)), run("b", 2, Map.of("y", 2))));

        assertThat(comparison.comparable()).isTrue();
        assertThat(comparison.issues()).isEmpty();
        assertThat(comparison.metricNames()).containsExactly("x", "y");
        assertThat(comparison.runs()).extracting(RunComparison.ComparedRun::version).containsExactly(1, 2);
    }

    @Test
    void reportsEachDifferenceFromTheBaseline() {
        RunRecord baseline = run("a", 1, Map.of());
        RunRecord other = run("b", 2, LocalDate.of(2024, 2, 29), "other-data", "def", json("{'costs': 'some'}"),
                RunStatus.FAILED, Map.of());

        RunComparison comparison = RunComparator.compare(List.of(baseline, other));

        assertThat(comparison.comparable()).isFalse();
        assertThat(comparison.issues()).extracting(RunComparison.Issue::field)
                .containsExactly("status", "endDate", "dataHash", "engineCommit", "assumptions");
        assertThat(comparison.issues().get(2)).isEqualTo(new RunComparison.Issue("b", "dataHash", "dh", "other-data"));
    }

    @Test
    void runsWithoutManifestAreFlagged() {
        RunRecord old = new RunRecord("old", RunStatus.COMPLETED, RunMode.BACKTEST,
                new StrategyConfig("s", 1, null, "c", null, null, List.of()), JAN_1, JAN_31, List.of(), Map.of(),
                null, List.of(), null, Instant.EPOCH, Instant.EPOCH, null, null);

        RunComparison comparison = RunComparator.compare(List.of(run("a", 1, Map.of()), old));

        assertThat(comparison.comparable()).isFalse();
        assertThat(comparison.issues()).extracting(RunComparison.Issue::field).containsExactly("manifest");
    }

    @Test
    void differentMissingDatesAreAnIssue() {
        RunRecord complete = run("a", 1, Map.of());
        RunRecord gap = run("b", 1, JAN_31, "dh", "abc", json("{'costs': 'none'}"), RunStatus.COMPLETED, Map.of(),
                List.of(LocalDate.of(2024, 1, 4)));

        assertThat(RunComparator.compare(List.of(complete, gap)).issues())
                .containsExactly(new RunComparison.Issue("b", "missingDates", "[]", "[2024-01-04]"));
    }
}
