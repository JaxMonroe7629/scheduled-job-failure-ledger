package dev.infrai.jobs;

import java.time.Instant;
import java.util.Objects;

/** The audit facts needed to decide whether a scheduled developer-tools run needs attention. */
public record DeveloperToolRun(String jobName, String runId, Operation operation,
                               String revision, Instant startedAt, Instant finishedAt,
                               int exitCode, String diagnostic) {
    public enum Operation { BUILD, RELEASE }

    public DeveloperToolRun {
        Objects.requireNonNull(jobName);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(revision);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
        Objects.requireNonNull(diagnostic);
        if (jobName.isBlank() || runId.isBlank() || revision.isBlank()) {
            throw new IllegalArgumentException("jobName, runId, and revision are required");
        }
        if (finishedAt.isBefore(startedAt)) throw new IllegalArgumentException("finish precedes start");
    }

    public boolean failed() {
        return exitCode != 0;
    }
}
