#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

readonly MONGO_DOWN_SECONDS=10

stop_mongo_at_startup() {
    start_stack
    failed_at=$(utc_now)
    "${COMPOSE[@]}" stop mongo
    sleep "$MONGO_DOWN_SECONDS"
    "${COMPOSE[@]}" start mongo
    open_run
    recover
}

run_recovery 1 stop_mongo_at_startup
