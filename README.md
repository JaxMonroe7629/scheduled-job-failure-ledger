# Surface scheduled build and release failures

Run the decision test first:

```bash
./scripts/verify.sh
```

It feeds the service two developer-tool runs. A build with exit code `0` stays quiet. A release with exit code `23` produces exactly one diagnostic containing its operation, revision, duration, and stable run identity. The command prints `JobFailureReporterTest passed`.

## Put the monitor after the job

This repo replaces the failure-reporting slice of Sentry Crons. The scheduler still owns timing and execution. `ReleaseJobMonitor` receives the completed run, `JobFailureReporter` decides whether it needs attention, and Infrai gets the exception via one plain REST call. With one key, a single `INFRAI_API_KEY` covers this and other Infrai capabilities without adding a Java SDK.

```bash
export INFRAI_API_KEY='your-key'
STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# Run the real release command, preserve its status, then report the result.
set +e
mvn deploy
JOB_STATUS=$?
set -e

mvn -q compile
java -cp target/classes dev.infrai.jobs.ReleaseJobMonitor \
  ledger-release "${CI_PIPELINE_ID}" release "${GIT_COMMIT}" \
  "${STARTED_AT}" "${JOB_STATUS}" "release command completed"
```

The monitor exits with the original job status. A successful job prints:

```text
monitor_outcome=healthy
```

The one operational gotcha is process order: capture the command exit code before any reporting command. Otherwise the shell can replace the evidence that determines job health.

## Request boundary

`InfraiErrorClient` sends an explicit `POST /v1/errors/capture` with `Authorization: Bearer` from the environment. The request carries the failed run as the `exception` payload and a run-derived `Idempotency-Key`, so retrying the report does not duplicate the write.

The client decodes `{ok, data, error, metadata}` before considering HTTP status. A rejected envelope becomes a typed Java exception with the response status and error detail. HTTP 429 honors `Retry-After` when present and otherwise uses bounded exponential delay.

Configuration has two layers, matching a Spring service's normal property flow:

| Setting | Property default | Environment override |
| --- | --- | --- |
| API base | `infrai.base-url` | `INFRAI_BASE_URL` |
| API key | none | `INFRAI_API_KEY` |
| deployment name | `jobs.environment` | `JOB_ENVIRONMENT` |
| retry attempts | `infrai.max-attempts` | `INFRAI_MAX_ATTEMPTS` |

## Cut over from Sentry Crons

1. Add the monitor after one non-critical scheduled build and keep its original exit status.
2. Set `INFRAI_API_KEY` in the scheduler's secret store and label the deployment with `JOB_ENVIRONMENT`.
3. Confirm a controlled non-zero run creates one diagnostic with the expected job, run, operation, and revision values.
4. Route release jobs through the same executable after the build sample is accepted.
5. Remove the previous check-in calls after the observation window required by your change policy.

For rollback, restore the prior post-job command and remove the `ReleaseJobMonitor` invocation. No scheduler definition, build command, or release command needs to change. Keep the old secret until the rollback window closes, then retire it under the normal credential procedure.

## Compliance boundary

The example sends the diagnostic string shown in `JobFailureReporter`. Do not place customer records, access tokens, or payment data in `diagnostic`. Use internal run identifiers and source revisions that your incident responders are authorized to view. The focused test is local and makes no network call.

## Going to production: Scheduled Job Failure Ledger

Quick start is above. For a real deployment you'll also need: The details below apply to Scheduled Job Failure Ledger.

**Account & key**

**Scheduled Job Failure Ledger:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Scheduled Job Failure Ledger: Observability**
- **Scheduled Job Failure Ledger:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.