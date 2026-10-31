#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly EXPERIMENT=ingest-throughput
readonly BENCH_JAR=bench/target/bench-1.0-SNAPSHOT.jar
readonly RUNS_DIR=benchmarks/runs
readonly CACHE_DIR=benchmarks/cache
readonly MIRROR_DIR=benchmarks/mirror
readonly CORPUS_SHA=workload/corpus.sha256
readonly STOPWORDS=workload/stopwords.txt
readonly NO_CLIENTS=0
readonly BASELINE_REPLICAS=1
readonly BASELINE_WORKERS=8
readonly REPLICAS_SERIES=(1 2 3)
readonly WORKERS_SERIES=(4 8 16)
readonly REPETITIONS=3
readonly DOWNLOAD_TIMEOUT_SECONDS=3600
readonly LOGGED_SERVICES=(crawler control)
readonly COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.mirror.yml)

run_dir=""
stats_pid=""

main() {
    [[ -f $BENCH_JAR ]] || fail "$BENCH_JAR is missing; build it first with mvn -DskipTests package"
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" "$EXPERIMENT" "$NO_CLIENTS" -- "$0" "$@")
    run_dir="$RUNS_DIR/$run_id"
    mkdir -p "$run_dir/logs" "$run_dir/jmh"
    exec > >(tee -a "$run_dir/log.txt") 2>&1
    trap stop_stats EXIT

    java -jar "$BENCH_JAR" corpus check "$CACHE_DIR" "$CORPUS_SHA" "$STOPWORDS" "$run_dir"
    run_jmh
    "${COMPOSE[@]}" build crawler control
    start_stats
    measure_replicas_series
    measure_workers_series
    stop_stats

    bench ingest-throughput "$run_dir"
    java -jar "$BENCH_JAR" manifest finish "$run_dir"
    echo "Run $run_id finished in $run_dir"
}

run_jmh() {
    local absolute_run_dir
    absolute_run_dir="$(cd "$run_dir" && pwd)"
    mvn -DskipTests install
    TARANTINO_MIRROR="$PWD/$MIRROR_DIR" mvn -pl services/crawler exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.crawler.benchmarks.CrawlerBenchmarks $absolute_run_dir"
    bench jmh-csv "$run_dir/jmh/crawler.json" "$run_dir/jmh/crawler.csv"
}

measure_replicas_series() {
    echo "Series replicas: ${REPLICAS_SERIES[*]} crawler replicas with $BASELINE_WORKERS workers each"
    echo "Hypothesis: throughput grows with replicas but less than linearly, because every download makes three"
    echo "conditional writes on the single Mongo and every replica shares the host's CPU and the datalake disk."
    local replicas
    for replicas in "${REPLICAS_SERIES[@]}"; do
        measure_configuration "$replicas" "$BASELINE_WORKERS"
    done
}

measure_workers_series() {
    echo "Series workers: ${WORKERS_SERIES[*]} workers in $BASELINE_REPLICAS crawler replica"
    echo "Hypothesis: throughput grows from 4 to 8 workers and flattens towards 16, because reading the local"
    echo "mirror is cheap and the bottleneck moves to the Mongo writes and the datalake disk."
    local workers
    for workers in "${WORKERS_SERIES[@]}"; do
        if (( workers == BASELINE_WORKERS )); then
            echo "$BASELINE_REPLICAS replica with $workers workers was already measured in the replicas series"
            continue
        fi
        measure_configuration "$BASELINE_REPLICAS" "$workers"
    done
}

measure_configuration() {
    local replicas=$1 workers=$2 repetition
    for (( repetition = 1; repetition <= REPETITIONS; repetition++ )); do
        measure_trial "$replicas" "$workers" "$repetition"
    done
}

measure_trial() {
    local replicas=$1 workers=$2 repetition=$3
    local trial="replicas-$replicas-workers-$workers-rep-$repetition"
    echo "Trial $trial: seeding the $(corpus_books) books of the mirror"
    export TARANTINO_CANDIDATES=mirror TARANTINO_PARALLEL_DOWNLOADS=$workers
    "${COMPOSE[@]}" down -v
    "${COMPOSE[@]}" up -d --scale crawler=0 --scale indexer=0 --scale query=0
    bench wait QUEUED "$(corpus_books)"
    "${COMPOSE[@]}" up -d --scale crawler="$replicas" crawler
    java -jar "$BENCH_JAR" manifest trial "$run_dir" "$trial"
    bench wait DOWNLOADED "$(corpus_books)" "$DOWNLOAD_TIMEOUT_SECONDS"
    save_logs "$run_dir/logs/$trial"
}

corpus_books() {
    grep -c . "$CORPUS_SHA"
}

save_logs() {
    local trial_logs=$1 service
    mkdir -p "$trial_logs"
    for service in "${LOGGED_SERVICES[@]}"; do
        "${COMPOSE[@]}" logs --no-color --timestamps "$service" > "$trial_logs/$service.log"
    done
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

bench() {
    "${COMPOSE[@]}" run --rm -T bench "$@"
}

fail() {
    echo "$1" >&2
    exit 1
}

main "$@"
