package com.tradeevo.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;

import com.tradeevo.core.json.CanonicalJson;

/**
 * Lines up runs by metric name and checks they are comparable: same dataset, period, input data,
 * engine build and execution assumptions as the first (baseline) run, and all completed. Different
 * strategy configs are expected — that is what is being compared.
 */
public final class RunComparator {

    private RunComparator() {
    }

    public static RunComparison compare(List<RunRecord> runs) {
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("at least one run is required");
        }
        RunRecord baseline = runs.getFirst();
        List<RunComparison.Issue> issues = new ArrayList<>();
        for (RunRecord run : runs) {
            if (run.status() != RunStatus.COMPLETED) {
                issues.add(new RunComparison.Issue(run.runId(), "status", null, String.valueOf(run.status())));
            }
            if (run.manifest() == null) {
                issues.add(new RunComparison.Issue(run.runId(), "manifest", null, "missing (run recorded before manifests)"));
            }
            if (run == baseline) {
                continue;
            }
            check(issues, baseline, run, "startDate", r -> r.startDate());
            check(issues, baseline, run, "endDate", r -> r.endDate());
            if (baseline.manifest() != null && run.manifest() != null) {
                check(issues, baseline, run, "dataset", r -> r.manifest().dataset());
                check(issues, baseline, run, "missingDates", r -> r.manifest().missingDates());
                check(issues, baseline, run, "dataHash", r -> r.manifest().dataHash());
                check(issues, baseline, run, "engineVersion", r -> r.manifest().engineVersion());
                check(issues, baseline, run, "engineCommit", r -> r.manifest().engineCommit());
                check(issues, baseline, run, "assumptions", r -> r.manifest().assumptions() == null
                        ? null : CanonicalJson.toCanonicalString(r.manifest().assumptions()));
            }
        }

        TreeSet<String> names = new TreeSet<>();
        runs.forEach(run -> names.addAll(run.metrics().keySet()));
        List<RunComparison.ComparedRun> compared = runs.stream()
                .map(run -> new RunComparison.ComparedRun(run.runId(), run.strategy().id(), run.strategy().version(),
                        run.status(), run.startDate(), run.endDate(),
                        run.manifest() != null ? run.manifest().dataHash() : null, run.metrics()))
                .toList();
        return new RunComparison(List.copyOf(names), issues.isEmpty(), issues, compared);
    }

    private static void check(List<RunComparison.Issue> issues, RunRecord baseline, RunRecord run, String field,
            Function<RunRecord, Object> value) {
        Object expected = value.apply(baseline);
        Object actual = value.apply(run);
        if (!Objects.equals(expected, actual)) {
            issues.add(new RunComparison.Issue(run.runId(), field, String.valueOf(expected), String.valueOf(actual)));
        }
    }
}
