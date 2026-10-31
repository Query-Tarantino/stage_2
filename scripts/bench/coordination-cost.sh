#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

state_hypotheses() {
    echo "Factor: replicas of crawler and indexer together ($REPLICAS_LIST), $REPETITIONS repetitions each, interleaved: repetition 1 of every N, then repetition 2, so drift over the run spreads across every N"
    echo "H1: with 1 replica the wait in books-to-download dominates: its median is far above the download time"
    echo "H2: the download queue wait falls roughly as 1/N while download and indexing times per book stay flat, sublinearly at N=3 because Mongo is shared"
    echo "H3: the wait in books-to-index falls less, because a book also waits for its batch to fill"
}

measure() {
    local books replicas repetition
    books=$(expected_books)
    for repetition in $(seq 1 "$REPETITIONS"); do
        for replicas in $REPLICAS_LIST; do
            start_trial "$(trial_name "$replicas" "$repetition")" "$replicas" "$replicas"
            record_trial
            wait_for PUBLISHED "$books"
            save_trial_logs
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" coordination "$run_dir"
}

main() {
    open_run coordination-cost "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    measure
}

main "$@"
