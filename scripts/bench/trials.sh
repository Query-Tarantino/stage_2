#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly BENCH_JAR=bench/target/bench-1.0-SNAPSHOT.jar
readonly RUNS_DIR=benchmarks/runs
readonly CACHE_DIR=benchmarks/cache
readonly MIRROR_DIR=benchmarks/mirror
readonly CORPUS_SHA=workload/corpus.sha256
readonly STOPWORDS=workload/stopwords.txt
readonly REPLICAS_LIST="${TARANTINO_REPLICAS_LIST:-1 2 3}"
readonly REPETITIONS="${TARANTINO_REPETITIONS:-5}"
readonly PHASE_TIMEOUT_SECONDS="${TARANTINO_PHASE_TIMEOUT_SECONDS:-1800}"
readonly VARIANT="${TARANTINO_VARIANT:-hazelcast}"

source scripts/bench/modes.sh

COMPOSE=()
SERVICES=()
LOGGED_SERVICES=()

use_mode() {
    local mode=$1 files
    files=$(compose_files "$mode")
    read -r -a COMPOSE <<< "docker compose $files"
    export_profiles_of_mode "$mode"
    mapfile -t SERVICES < <("${COMPOSE[@]}" config --services)
    LOGGED_SERVICES=("${SERVICES[@]}")
}

use_mode "$VARIANT"

export TARANTINO_CANDIDATES="${TARANTINO_CANDIDATES:-mirror}"

run_dir=""
current_trial=""
trial_started_at=""
stats_pid=""

open_run() {
    local experiment=$1
    shift
    down_every_mode "${COMPOSE[@]}"
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" "$experiment" 0 -- "$@")
    run_dir="$RUNS_DIR/$run_id"
    mkdir -p "$run_dir/logs"
    exec > >(tee -a "$run_dir/log.txt") 2>&1
    trap close_run EXIT
    echo "Opened run $run_id: candidates $TARANTINO_CANDIDATES, replicas $REPLICAS_LIST, $REPETITIONS repetitions, phases up to $PHASE_TIMEOUT_SECONDS s"
}

check_corpus() {
    java -jar "$BENCH_JAR" corpus check "$CACHE_DIR" "$CORPUS_SHA" "$STOPWORDS" "$run_dir"
}

build_stack() {
    "${COMPOSE[@]}" build
}

expected_books() {
    if [[ $TARANTINO_CANDIDATES == mirror ]]; then
        find "$MIRROR_DIR" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' '
    else
        tr -d '\r' < "workload/$TARANTINO_CANDIDATES" | grep -c '[0-9]'
    fi
}

trial_name() {
    echo "replicas-$1-rep-$2"
}

start_trial() {
    local trial=$1 crawlers=$2 indexers=$3
    echo "=== Trial $trial: crawler=$crawlers, indexer=$indexers ==="
    "${COMPOSE[@]}" down -v
    "${COMPOSE[@]}" up -d --scale "crawler=$crawlers" --scale "indexer=$indexers"
    current_trial=$trial
}

scale_stack() {
    local crawlers=$1 indexers=$2
    "${COMPOSE[@]}" up -d --no-recreate --scale "crawler=$crawlers" --scale "indexer=$indexers"
}

record_trial() {
    java -jar "$BENCH_JAR" manifest trial "$run_dir" "$current_trial"
}

wait_for() {
    local state=$1 books=$2
    "${COMPOSE[@]}" run --rm -T bench wait "$state" "$books" "$PHASE_TIMEOUT_SECONDS"
}

save_trial_logs() {
    local trial_logs="$run_dir/logs/$current_trial" service since=()
    [[ -z $trial_started_at ]] || since=(--since "$trial_started_at")
    mkdir -p "$trial_logs"
    for service in "${LOGGED_SERVICES[@]}"; do
        "${COMPOSE[@]}" logs --no-color --timestamps ${since[@]+"${since[@]}"} "$service" > "$trial_logs/$service.log"
    done
    current_trial=""
}

ingest_corpus() {
    "${COMPOSE[@]}" up -d --wait
    wait_for PUBLISHED "$(expected_books)"
}

run_jmh_of() {
    local service=$1 main_class=$2 absolute_run_dir
    mkdir -p "$run_dir/jmh"
    absolute_run_dir=$(host_path "$(cd "$run_dir" && pwd)")
    mvn -DskipTests install
    TARANTINO_MIRROR=$(host_path "$PWD/$MIRROR_DIR") TARANTINO_WORKLOAD=$(host_path "$PWD/workload") \
        mvn -f "services/$service/pom.xml" exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath $main_class \"$absolute_run_dir\""
    java -jar "$BENCH_JAR" jmh-csv "$run_dir/jmh/$service.json" "$run_dir/jmh/$service.csv"
}

host_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        printf '%s\n' "$1"
    fi
}

start_stats() {
    java -jar "$BENCH_JAR" stats "$run_dir" &
    stats_pid=$!
}

stop_stats() {
    if [[ -n $stats_pid ]]; then
        kill "$stats_pid" 2>/dev/null || true
        wait "$stats_pid" 2>/dev/null || true
        stats_pid=""
    fi
}

close_run() {
    stop_stats
    if [[ -n $current_trial ]]; then
        echo "Trial $current_trial did not finish; saving its logs"
        save_trial_logs
    fi
    summarise_run
    java -jar "$BENCH_JAR" manifest finish "$run_dir"
    java -jar "$BENCH_JAR" charts "$run_dir"
    echo "Results in $run_dir"
}
