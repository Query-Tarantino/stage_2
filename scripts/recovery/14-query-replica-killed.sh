#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

readonly MINIMUM_QUERY_REPLICAS=2
readonly SECONDS_BEFORE_KILL=20

kill_a_query_replica_while_searching() {
    start_stack
    failed_at=$(utc_in "$SECONDS_BEFORE_KILL")
    open_run
    recover &
    local recovery=$!
    sleep "$SECONDS_BEFORE_KILL"
    docker kill "$("${COMPOSE[@]}" ps --quiet query | head -1)"
    wait "$recovery"
}

run_recovery "$MINIMUM_QUERY_REPLICAS" kill_a_query_replica_while_searching
