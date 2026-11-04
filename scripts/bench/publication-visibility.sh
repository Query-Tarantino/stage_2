#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly LISTENER_SETTINGS="on off"

state_hypotheses() {
    echo "Machine: the official one, 24 hardware threads, 8 P cores and 16 E cores"
    echo "Factor: how query learns of a batch, with the published-batches listener on or off, 2 query replicas, $REPETITIONS repetitions each, interleaved: on 1, off 1, on 2, and so on"
    echo "H1: with the listener, the median publication visibility is under 100 ms"
    echo "H2: without it, the delays spread evenly between 0 and TARANTINO_SNAPSHOT_SECONDS (5 s): median about 2.5 s, maximum about 5 s"
    echo "H3: query uses the same CPU in both configurations, within their 95 % CIs"
}

snapshot_on_publish() {
    if [[ $1 == on ]]; then echo true; else echo false; fi
}

start_listener_trial() {
    local trial=$1 listener=$2
    echo "=== Trial $trial: listener $listener, 1 crawler, 1 indexer and 2 query replicas ==="
    "${COMPOSE[@]}" down -v
    export TARANTINO_SNAPSHOT_ON_PUBLISH
    TARANTINO_SNAPSHOT_ON_PUBLISH=$(snapshot_on_publish "$listener")
    "${COMPOSE[@]}" up -d --scale crawler=1 --scale indexer=1 --scale query=2
    current_trial=$trial
}

measure() {
    local books listener repetition
    books=$(expected_books)
    for repetition in $(seq 1 "$REPETITIONS"); do
        for listener in $LISTENER_SETTINGS; do
            start_listener_trial "listener-$listener-rep-$repetition" "$listener"
            record_trial
            wait_for PUBLISHED "$books"
            save_trial_logs
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" visibility "$run_dir"
    java -jar "$BENCH_JAR" coordination "$run_dir"
}

main() {
    open_run publication-visibility "$0" "$@"
    state_hypotheses
    check_corpus
    build_stack
    start_stats
    measure
}

main "$@"
