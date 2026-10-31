#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

state_hypotheses() {
    echo "Factor: replicas of crawler and indexer together ($REPLICAS_LIST), $REPETITIONS repetitions each, interleaved: repetition 1 of every N, then repetition 2, so drift over the run spreads across every N"
    echo "Phases: ingestion with no indexer until every book is DOWNLOADED, then indexing with the indexers until every book is PUBLISHED"
    echo "H1: during ingestion the total CPU of the crawlers grows with N while the CPU per crawler stays flat, until the 24 hardware threads of the official machine saturate"
    echo "H2: during ingestion the crawlers use far more CPU than Mongo, as in the preliminary ingest-throughput run (crawlers 400-790 %, Mongo about 100 %), because the crawler spends its CPU processing the text"
    echo "H3: during indexing Mongo takes a larger share of the CPU than during ingestion, and it grows with N, because every indexer writes its postings to the same Mongo"
    echo "H4: the memory of each JVM stays flat below its -Xmx512m whatever N is"
}

measure() {
    local books replicas repetition
    books=$(expected_books)
    for repetition in $(seq 1 "$REPETITIONS"); do
        for replicas in $REPLICAS_LIST; do
            start_trial "$(trial_name "$replicas" "$repetition")" "$replicas" 0
            wait_for DOWNLOADED "$books"
            scale_stack "$replicas" "$replicas"
            record_trial
            wait_for PUBLISHED "$books"
            save_trial_logs
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" resources "$run_dir"
}

main() {
    open_run resource-usage "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    start_stats
    measure
}

main "$@"
