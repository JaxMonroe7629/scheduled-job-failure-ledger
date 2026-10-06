package dev.infrai.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class JobFailureReporterTest {
    public static void main(String[] args) throws Exception {
        List<String> captures = new ArrayList<>();
        FailureSink sink = (exception, key) -> captures.add(key + "\n" + exception);
        JobFailureReporter reporter = new JobFailureReporter(sink, "staging");
        Instant start = Instant.parse("2026-09-30T02:00:00Z");

        JobFailureReporter.Outcome healthy = reporter.record(new DeveloperToolRun(
                "artifact-build", "run-41", DeveloperToolRun.Operation.BUILD, "a91d2f0",
                start, start.plusSeconds(8), 0, "artifact signed"));
        require(healthy == JobFailureReporter.Outcome.HEALTHY, "successful build must stay healthy");
        require(captures.isEmpty(), "successful build must not create a diagnostic");

        JobFailureReporter.Outcome failed = reporter.record(new DeveloperToolRun(
                "ledger-release", "run-42", DeveloperToolRun.Operation.RELEASE, "b14ac90",
                start, start.plusSeconds(12), 23, "checksum approval missing"));
        require(failed == JobFailureReporter.Outcome.FAILURE_REPORTED, "failed release must be reported");
        require(captures.size() == 1, "failed release must create exactly one diagnostic");
        require(captures.get(0).contains("scheduled-job:ledger-release:run-42"), "run identity must be stable");
        require(captures.get(0).contains("operation=release"), "operation must reach the diagnostic");
        require(captures.get(0).contains("exit_code=23"), "exit code must reach the diagnostic");
        System.out.println("JobFailureReporterTest passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
