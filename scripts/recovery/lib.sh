#!/usr/bin/env bash
set -euo pipefail

SCRIPT_COMMAND=("$0" "$@")
readonly SCRIPT_COMMAND
readonly SCRIPT_NUMBER=$(basename "$0" | cut -c1-2)

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly BENCH_JAR=bench/target/bench-1.0-SNAPSHOT.jar
readonly RUNS_DIR=benchmarks/runs
readonly MAX_REPLICAS=3
readonly CRASH_EXIT_CODE=1
readonly CRASH_EXIT_TIMEOUT_SECONDS=120
readonly PAUSE_LONGER_THAN_LEASE_SECONDS=10
readonly WRONG_USAGE=2
SERVICES=(mongo hazelcast crawler indexer query control nginx)
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.mirror.yml --env-file scripts/recovery/recovery.env)
readonly SERVICES COMPOSE

replicas=""
run_dir=""
run_dir_file=""
failed_at=""

run_recovery() {
    local minimum_replicas=$1
    shift
    read_replicas "$minimum_replicas"
    local log status
    log=$(mktemp)
    run_dir_file=$(mktemp)
    set +e
    (set -e; recovery_run "$@") 2>&1 | tee "$log"
    status=$?
    set -e
    keep_log "$log"
    exit "$status"
}

read_replicas() {
    local minimum=$1 requested=${SCRIPT_COMMAND[1]:-}
    if [[ ! $requested =~ ^[0-9]+$ ]] || (( requested < minimum || requested > MAX_REPLICAS )); then
        echo "Usage: ${SCRIPT_COMMAND[0]} <replicas of crawler, indexer and query, $minimum to $MAX_REPLICAS>" >&2
        exit "$WRONG_USAGE"
    fi
    replicas=$requested
}

recovery_run() {
    trap close_run EXIT
    "$@"
}

seed_mirror() {
    export TARANTINO_CANDIDATES=mirror
}

start_stack() {
    "${COMPOSE[@]}" down -v
    "${COMPOSE[@]}" up -d --build --scale "crawler=$replicas" --scale "indexer=$replicas" --scale "query=$replicas"
}

open_run() {
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" "recovery-$SCRIPT_NUMBER-r$replicas" 0 -- "${SCRIPT_COMMAND[@]}")
    run_dir="$RUNS_DIR/$run_id"
    mkdir -p "$run_dir/logs"
    echo "$run_dir" > "$run_dir_file"
    echo "Opened run $run_id with failed_at $failed_at"
}

recover_from_crash() {
    local service=$1 point=$2
    local crash_variable
    crash_variable="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')_CRASH_AT"
    export "$crash_variable=$point"
    start_stack
    unset "$crash_variable"
    failed_at=$(await_crash "$service")
    open_run
    bring_back "$service"
    recover
}

recover_from_pause() {
    local service=$1 state_in_flight=$2
    seed_mirror
    start_stack
    bench wait "$state_in_flight"
    failed_at=$(utc_now)
    "${COMPOSE[@]}" pause "$service"
    sleep "$PAUSE_LONGER_THAN_LEASE_SECONDS"
    "${COMPOSE[@]}" unpause "$service"
    open_run
    recover
}

await_crash() {
    local service=$1 deadline=$((SECONDS + CRASH_EXIT_TIMEOUT_SECONDS)) crashed
    while (( SECONDS < deadline )); do
        crashed=$(first_crash "$service")
        if [[ -n $crashed ]]; then
            echo "$crashed"
            return
        fi
        sleep 1
    done
    echo "No $service replica exited with code $CRASH_EXIT_CODE within $CRASH_EXIT_TIMEOUT_SECONDS s" >&2
    return 1
}

first_crash() {
    "${COMPOSE[@]}" ps --all --quiet "$1" \
        | xargs docker inspect --format "{{if and (eq .State.Status \"exited\") (eq .State.ExitCode $CRASH_EXIT_CODE)}}{{.State.FinishedAt}}{{end}}" \
        | sort | sed -n '/./{p;q;}'
}

bring_back() {
    local service=$1
    "${COMPOSE[@]}" logs --no-color --timestamps "$service" > "$run_dir/logs/$service-crashed.log"
    "${COMPOSE[@]}" up -d --scale "$service=$replicas" "$service"
}

utc_in() {
    java -jar "$BENCH_JAR" instant "$1"
}

utc_now() {
    java -jar "$BENCH_JAR" instant
}

bench() {
    "${COMPOSE[@]}" run --rm -T bench "$@"
}

recover() {
    bench recovery "$SCRIPT_NUMBER" "$(basename "$run_dir")" "$failed_at"
}

close_run() {
    if [[ -z $run_dir ]]; then
        return
    fi
    local service
    for service in "${SERVICES[@]}"; do
        "${COMPOSE[@]}" logs --no-color --timestamps "$service" > "$run_dir/logs/$service.log"
    done
    java -jar "$BENCH_JAR" coordination "$run_dir"
    java -jar "$BENCH_JAR" manifest finish "$run_dir"
}

keep_log() {
    local log=$1 opened_run
    opened_run=$(cat "$run_dir_file")
    rm -f "$run_dir_file"
    if [[ -z $opened_run ]]; then
        echo "No run was opened; the output is in $log" >&2
        return
    fi
    mv "$log" "$opened_run/log.txt"
}
