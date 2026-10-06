package dev.infrai.jobs;

import java.io.IOException;
import java.time.Duration;

/** Makes the business decision: successful runs stay quiet; failed runs become grouped diagnostics. */
public final class JobFailureReporter {
    public enum Outcome { HEALTHY, FAILURE_REPORTED }

    private final FailureSink sink;
    private final String environment;

    public JobFailureReporter(FailureSink sink, String environment) {
        this.sink = sink;
        this.environment = environment;
    }

    public Outcome record(DeveloperToolRun run) throws IOException, InterruptedException {
        if (!run.failed()) return Outcome.HEALTHY;

        long durationMillis = Duration.between(run.startedAt(), run.finishedAt()).toMillis();
        String exception = "ScheduledDeveloperToolFailure" +
                "\njob=" + run.jobName() +
                "\nrun=" + run.runId() +
                "\noperation=" + run.operation().name().toLowerCase() +
                "\nenvironment=" + environment +
                "\nrevision=" + run.revision() +
                "\nexit_code=" + run.exitCode() +
                "\nduration_ms=" + durationMillis +
                "\ndiagnostic=" + run.diagnostic();
        sink.capture(exception, "scheduled-job:" + run.jobName() + ":" + run.runId());
        return Outcome.FAILURE_REPORTED;
    }
}
