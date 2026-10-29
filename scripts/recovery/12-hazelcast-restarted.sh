#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

restart_hazelcast_with_books_queued() {
    seed_mirror
    start_stack
    bench wait books-to-download
    failed_at=$(utc_now)
    "${COMPOSE[@]}" restart hazelcast
    open_run
    recover
}

run_recovery 1 restart_hazelcast_with_books_queued
