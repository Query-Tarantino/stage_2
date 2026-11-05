#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

state_hypotheses() {
    echo "Machine: the official one, 24 hardware threads, 8 P cores and 16 E cores"
    echo "Factors: coordination mode (${MODES[*]}) and replicas of crawler and indexer together ($REPLICAS_LIST), $REPETITIONS repetitions each, interleaved: the 15 configurations of repetition 1, then repetition 2, so drift spreads across every mode"
    echo "H0: download_ms does not differ between any pair of modes, nor indexing_ms between any pair without polling (differs false in pairs.csv), so only the coordination changed"
    echo "H0 in polling: a batch claims each book as it polls it, so the wait for the batch to fill counts in indexing_ms instead of index_queue_wait; their sum compares through time_to_publication"
    echo "polling H1: throughput within the CI of hazelcast, since the work per book dominates"
    echo "polling H2: MongoDB CPU higher than in hazelcast at every N: one sorted findOneAndUpdate per claim, plus the idle polls"
    echo "polling H3: publication visibility median about 2.5 s"
    echo "activemq H1: throughput and time to publication within the CI of hazelcast at every N"
    echo "activemq-persistent H2: throughput within the CI of activemq, with more broker CPU"
    echo "orchestrator H1: throughput within the CI of hazelcast at N = 1 and 2; at N = 3 it may fall below, because one thread sends one POST at a time"
    echo "orchestrator H2: time to publication up to two ticks (400 ms) above hazelcast"
    echo "orchestrator H3: publication visibility under 300 ms"
}

down_every_mode() {
    COMPOSE_PROFILES=activemq "${COMPOSE[@]}" down -v --remove-orphans
}

start_mode_trial() {
    local trial=$1 mode=$2 replicas=$3 broker
    echo "=== Trial $trial: mode $mode, $replicas crawlers and indexers ready before control seeds ==="
    down_every_mode
    use_mode "$mode"
    broker=$(broker_of_mode "$mode")
    "${COMPOSE[@]}" up -d --wait --scale "crawler=$replicas" --scale "indexer=$replicas" mongo $broker crawler indexer query nginx
    "${COMPOSE[@]}" up -d control
    current_trial=$trial
}

measure() {
    local books replicas repetition mode
    books=$(expected_books)
    for repetition in $(seq 1 "$REPETITIONS"); do
        for mode in "${MODES[@]}"; do
            for replicas in $REPLICAS_LIST; do
                start_mode_trial "mode-$mode-replicas-$replicas-rep-$repetition" "$mode" "$replicas"
                record_trial
                wait_for PUBLISHED "$books"
                wait_for snapshot "$books"
                save_trial_logs
            done
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" coordination "$run_dir"
    java -jar "$BENCH_JAR" visibility "$run_dir"
    java -jar "$BENCH_JAR" variants "$run_dir"
}

main() {
    open_run coordination-variants "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    start_stats
    measure
    down_every_mode
}

main "$@"
