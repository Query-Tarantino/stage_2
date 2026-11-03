#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly CONFIGURATIONS=("1 1" "10 1" "100 1" "250 1" "100 2" "100 3")
readonly REBUILD_URL=http://localhost:8081/index/rebuild
readonly CONTROL_STATUS_URL=http://localhost:8081/control/status
readonly REBUILD_ATTEMPTS=60

export TARANTINO_BATCH_TIMEOUT_SECONDS=2

trial_started_at=""

state_hypotheses() {
    echo "Machine: the official one, 24 hardware threads, 8 P cores and 16 E cores; each indexer replica indexes one batch at a time on its index-batches thread, so at most 3 indexing threads run and they fit in the P cores"
    echo "Factors: books per batch and indexer replicas, $REPETITIONS repetitions each, interleaved: repetition 1 of every configuration, then repetition 2, so drift over the run spreads across every configuration"
    echo "Trial: the corpus is ingested once; each trial recreates the indexers with its configuration and rebuilds the index into a new generation"
    echo "Window: the whole one only, from the first index claim to the last batch published in the trial's generation; it leaves out the up to 30 s control takes to reindex the books and to switch generation"
    echo "Batch timeout: $TARANTINO_BATCH_TIMEOUT_SECONDS s, so the last partial batch of each replica does not wait 30 s inside the window"
    echo "Series batch: 1, 10, 100 and 250 books per batch with 1 indexer replica"
    echo "H1: books per second rise from 1 to 100 books per batch and flatten from 100 to 250, because each batch pays a fixed cost (its lease, the postings bulkWrites, publishing and marking) that more books share, while tokenizing and writing postings cost the same per book"
    echo "Series replicas: 1, 2 and 3 indexer replicas with batches of 100"
    echo "H2: books per second grow with replicas but less than linearly: three indexing threads do not run out of CPU, and every replica writes its postings to the same Mongo, which becomes the bottleneck"
    echo "H3: batches of 250 fit in -Xmx512m without OutOfMemoryError but spend more time in GC than batches of 100, which adds to the flattening of H1"
}

ingest_corpus() {
    "${COMPOSE[@]}" up -d --wait
    wait_for PUBLISHED "$(expected_books)"
}

measure() {
    local repetition configuration batch replicas
    for repetition in $(seq 1 "$REPETITIONS"); do
        for configuration in "${CONFIGURATIONS[@]}"; do
            read -r batch replicas <<< "$configuration"
            run_rebuild_trial "$batch" "$replicas" "$repetition"
        done
    done
}

run_rebuild_trial() {
    local batch=$1 replicas=$2 repetition=$3 generation target
    trial_started_at=$("${COMPOSE[@]}" run --rm -T bench instant)
    current_trial="batch-$batch-replicas-$replicas-rep-$repetition"
    echo "=== Trial $current_trial: batches of $batch books, $replicas indexer replicas ==="
    TARANTINO_INDEX_BATCH=$batch "${COMPOSE[@]}" up -d --wait --force-recreate --scale "indexer=$replicas" indexer
    record_trial
    generation=$(current_generation)
    target=$(open_rebuild "$(( generation + 1 ))")
    wait_for generation "$target"
    save_trial_logs
}

current_generation() {
    curl -fsS "$CONTROL_STATUS_URL" | grep -o '"generation":[0-9]*' | cut -d: -f2
}

open_rebuild() {
    local expected=$1 attempt target
    for (( attempt = 1; attempt <= REBUILD_ATTEMPTS; attempt++ )); do
        target=$(curl -s -X POST "$REBUILD_URL" | grep -o '"target_generation":[0-9]*' | cut -d: -f2 || true)
        if [[ $target == "$expected" ]]; then
            echo "$target"
            return 0
        fi
        sleep 1
    done
    echo "No rebuild to generation $expected after $REBUILD_ATTEMPTS attempts" >&2
    return 1
}

save_trial_logs() {
    local trial_logs="$run_dir/logs/$current_trial" service
    mkdir -p "$trial_logs"
    for service in "${SERVICES[@]}"; do
        "${COMPOSE[@]}" logs --no-color --timestamps --since "$trial_started_at" "$service" > "$trial_logs/$service.log"
    done
    current_trial=""
}

summarise_run() {
    java -jar "$BENCH_JAR" index-throughput "$run_dir"
}

main() {
    open_run index-throughput "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    ingest_corpus
    start_stats
    measure
}

main "$@"
