#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly BENCH_JAR=bench/target/bench-1.0-SNAPSHOT.jar
readonly RUNS_DIR=benchmarks/runs
readonly WRONG_USAGE=2

state_hypotheses() {
    echo "Machine: the official one, 24 hardware threads, 8 P cores and 16 E cores"
    echo "Input: recovery runs of rows 04, 05 and 12, with recovery.env: TARANTINO_REQUEUE_SECONDS=10, TARANTINO_RECONCILE_SECONDS=3"
    echo "H1: in hazelcast and activemq, requeue_delay falls between 7 and 13 s"
    echo "H2: reclaim_delay minus requeue_delay has a median under 1.5 s"
    echo "H3: in activemq-persistent there is no requeue and reclaim_delay stays under 10 s"
    echo "H4: in orchestrator, requeue_delay (dispatching again) falls between 10 and 10.4 s, REQUEUE plus up to two ticks"
}

main() {
    if (( $# == 0 )); then
        echo "Usage: $0 <recovery run dir>..." >&2
        exit "$WRONG_USAGE"
    fi
    local run_id run_dir
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" recovery-delay 0 -- "$0" "$@")
    run_dir="$RUNS_DIR/$run_id"
    exec > >(tee -a "$run_dir/log.txt") 2>&1
    echo "Opened run $run_id over $# recovery runs"
    state_hypotheses
    java -jar "$BENCH_JAR" recovery-delay "$run_dir" "$@"
    java -jar "$BENCH_JAR" manifest finish "$run_dir"
    echo "Results in $run_dir"
}

main "$@"
