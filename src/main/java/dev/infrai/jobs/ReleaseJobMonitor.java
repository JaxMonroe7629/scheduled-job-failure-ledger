package dev.infrai.jobs;

import java.time.Instant;

/** Executable boundary intended to run after a scheduled build or release command. */
public final class ReleaseJobMonitor {
    private ReleaseJobMonitor() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("usage: <job-name> <run-id> <build|release> <revision> <started-at> <exit-code> <diagnostic>");
            System.exit(64);
        }
        ScheduledJobConfig config = ScheduledJobConfig.load();
        DeveloperToolRun run = new DeveloperToolRun(
                args[0], args[1], DeveloperToolRun.Operation.valueOf(args[2].toUpperCase()), args[3],
                Instant.parse(args[4]), Instant.now(), Integer.parseInt(args[5]), args[6]);
        JobFailureReporter.Outcome outcome = new JobFailureReporter(
                new InfraiErrorClient(config), config.environment()).record(run);
        System.out.println("monitor_outcome=" + outcome.name().toLowerCase());
        System.exit(run.exitCode());
    }
}
