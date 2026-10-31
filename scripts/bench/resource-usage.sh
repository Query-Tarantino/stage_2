#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

state_hypotheses() {
    echo "Factor: replicas of crawler and indexer together ($REPLICAS_LIST), $REPETITIONS repetitions each"
    echo "Phases: ingestion with no indexer until every book is DOWNLOADED, then indexing with the indexers until every book is PUBLISHED"
    echo "H1: during ingestion the total CPU of the crawlers grows with N while the CPU per crawler stays flat"
    echo "H2: indexing is the CPU-heavy phase, and from N=2 Mongo is the container with the most CPU"
    echo "H3: the memory of each JVM stays flat below its -Xmx512m whatever N is"
}

measure() {
    local books replicas repetition
    books=$(expected_books)
    for replicas in $REPLICAS_LIST; do
        for repetition in $(seq 1 "$REPETITIONS"); do
            start_trial "$(trial_name "$replicas" "$repetition")" "$replicas" 0
            wait_for DOWNLOADED "$books"
            scale_stack "$replicas" "$replicas"
            record_trial
            wait_for PUBLISHED "$books"
            save_trial_logs
        done
    done
}

main() {
    open_run resource-usage "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    start_stats
    measure
    stop_stats
    java -jar "$BENCH_JAR" resources "$run_dir"
}

main "$@"
