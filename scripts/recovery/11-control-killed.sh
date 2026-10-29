#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

readonly SECONDS_INTO_SEEDING=1

kill_control_while_it_seeds() {
    seed_mirror
    start_stack
    sleep "$SECONDS_INTO_SEEDING"
    failed_at=$(utc_now)
    "${COMPOSE[@]}" kill control
    "${COMPOSE[@]}" start control
    open_run
    recover
}

run_recovery 1 kill_control_while_it_seeds
