#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly BROKERS="hazelcast activemq-nonpersistent activemq-persistent"
readonly CONSUMERS_LIST="1 3 8"

export COMPOSE_PROFILES=activemq

state_hypotheses() {
    echo "Machine: the official one, 24 hardware threads, 8 P cores and 16 E cores"
    echo "Factors: broker ($BROKERS) and consumers ($CONSUMERS_LIST), $REPETITIONS repetitions each, interleaved: the 9 configurations of repetition 1, then repetition 2"
    echo "Each trial: 1,000 messages/s for 35 s, the first 5 s discarded, then 50,000 messages as fast as possible, each round on its own queue bench-broker-<trial>-<round>"
    echo "H1: every p50 is under 1 ms"
    echo "H2: the activemq-persistent p50 is at least 3 times the activemq-nonpersistent one, because of the synchronous disk write"
    echo "H3: even the slowest p50 is at least 100 times smaller than the median queue wait of coordination-cost with 1 replica in this campaign"
}

measure() {
    local repetition broker consumers
    "${COMPOSE[@]}" up -d hazelcast activemq
    for repetition in $(seq 1 "$REPETITIONS"); do
        for broker in $BROKERS; do
            for consumers in $CONSUMERS_LIST; do
                echo "=== Trial broker-$broker-consumers-$consumers-rep-$repetition ==="
                "${COMPOSE[@]}" run --rm -T bench broker "$run_dir" "broker-$broker-consumers-$consumers-rep-$repetition" "$broker" "$consumers"
            done
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" broker "$run_dir"
}

main() {
    open_run broker-latency "$0" "$@"
    state_hypotheses
    start_stats
    measure
}

main "$@"
