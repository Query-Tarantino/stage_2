#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

kill_control_while_it_seeds() {
    seed_mirror
    start_stack
    bench wait QUEUED
    failed_at=$(utc_now)
    "${COMPOSE[@]}" kill control
    "${COMPOSE[@]}" start control
    open_run
    recover
}

run_recovery 1 kill_control_while_it_seeds
