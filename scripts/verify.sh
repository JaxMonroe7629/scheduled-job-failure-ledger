#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/scheduled-job-failure-ledger-classes"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java src/test/java -name '*.java' -print)
java -cp "$BUILD_DIR:src/main/resources" dev.infrai.jobs.JobFailureReporterTest
