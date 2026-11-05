#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

restart_broker_with_books_queued() {
    local broker
    broker=$(broker_of_mode "$VARIANT")
    seed_mirror
    start_stack
    bench wait books-to-download
    failed_at=$(utc_now)
    "${COMPOSE[@]}" restart "$broker"
    open_run
    recover
}

if [[ -z $(broker_of_mode "$VARIANT") ]]; then
    echo "Row 12 does not apply: there is no broker"
    exit 0
fi

run_recovery 1 restart_broker_with_books_queued
