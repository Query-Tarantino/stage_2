#!/usr/bin/env bash
set -euo pipefail

readonly WORKLOAD_DIR="${TARANTINO_WORKLOAD:-workload}"
readonly BENCHMARKS_DIR="${TARANTINO_BENCHMARKS:-benchmarks}"
readonly RUNS_DIR="${BENCHMARKS_DIR}/runs"
readonly CORPUS_SHA="${WORKLOAD_DIR}/corpus.sha256"
readonly STOPWORDS_FILE="${WORKLOAD_DIR}/stopwords.txt"
readonly CORPUS_CACHE="${TARANTINO_CACHE:-${BENCHMARKS_DIR}/cache}"
readonly FRONTEND_URL="${TARANTINO_FRONTEND_URL:-http://localhost:8080}"
readonly MONGO_URI="${TARANTINO_MONGO_URI:-mongodb://mongo:27017/tarantino}"
readonly BENCH_JAR="bench/target/bench-1.0-SNAPSHOT.jar"

readonly BASELINE_CLIENTS="${TARANTINO_BASELINE_CLIENTS:-32}"
readonly BASELINE_THREADS="${TARANTINO_BASELINE_THREADS:-32}"
readonly BASELINE_REPLICAS="${TARANTINO_BASELINE_REPLICAS:-1}"

readonly CLIENTS_CONFIG="${TARANTINO_CLIENTS_LIST:-1 8 32 64}"
readonly THREADS_CONFIG="${TARANTINO_THREADS_LIST:-8 32 128}"
readonly REPLICAS_CONFIG="${TARANTINO_REPLICAS_LIST:-1 2 3}"

STATS_PID=""

ensure_bench_jar() {
    if [[ ! -f "$BENCH_JAR" ]]; then
        mvn -f bench/pom.xml package -DskipTests
    fi
}

check_corpus() {
    local run_directory=$1
    if [[ -d "$CORPUS_CACHE" ]]; then
        java -jar "$BENCH_JAR" corpus check "$CORPUS_CACHE" "$CORPUS_SHA" "$STOPWORDS_FILE" "$run_directory"
    else
        echo "Notice: Corpus cache directory $CORPUS_CACHE not found; skipping cache verification"
    fi
}

save_service_logs() {
    local run_directory=$1
    echo "Saving service logs in ${run_directory}/logs..."
    mkdir -p "${run_directory}/logs"
    if command -v docker >/dev/null 2>&1; then
        for service_name in crawler indexer query control nginx mongo hazelcast; do
            docker compose logs --no-color --timestamps "$service_name" > "${run_directory}/logs/${service_name}.log" 2>/dev/null || true
        done
    fi
}

start_stats() {
    local run_directory=$1
    if command -v docker >/dev/null 2>&1; then
        java -jar "$BENCH_JAR" stats "$run_directory" &
        STATS_PID=$!
    fi
}

stop_stats() {
    if [[ -n "${STATS_PID:-}" ]]; then
        kill "$STATS_PID" 2>/dev/null || true
        wait "$STATS_PID" 2>/dev/null || true
        STATS_PID=""
    fi
}

wait_for_query_ready() {
    local endpoint_url="${FRONTEND_URL}/search/filters"
    local max_attempts=120
    local attempt=0
    local consecutive_successes=0
    echo "Waiting for ${endpoint_url} to answer 200 with total_books > 0 across 10 consecutive requests..."
    while (( attempt < max_attempts && consecutive_successes < 10 )); do
        local status_code
        status_code=$(curl -s -o /tmp/filters_check.json -w "%{http_code}" "${endpoint_url}" 2>/dev/null || echo "000")
        if [[ "$status_code" == "200" && -f /tmp/filters_check.json ]]; then
            local total_books
            total_books=$(grep -o '"total_books":[0-9]*' /tmp/filters_check.json | cut -d: -f2 || echo 0)
            if [[ -n "$total_books" && "$total_books" -gt 0 ]]; then
                (( consecutive_successes++ ))
            else
                consecutive_successes=0
            fi
        else
            consecutive_successes=0
        fi
        sleep 0.2
        (( attempt++ ))
    done
    rm -f /tmp/filters_check.json
    if (( consecutive_successes >= 10 )); then
        echo "Query service is ready across all replicas"
        return 0
    fi
    echo "Error: Query service did not answer 10 consecutive 200s with total_books > 0 within ${max_attempts} attempts" >&2
    exit 1
}

export_explain() {
    local run_directory=$1
    echo "Saving explain plans in ${run_directory}/explain..."
    if command -v docker >/dev/null 2>&1 && docker compose ps query 2>/dev/null | grep -q "query"; then
        docker compose run --rm bench explain "${run_directory}/explain" "$MONGO_URI" 1 2>/dev/null || \
        java -jar "$BENCH_JAR" explain "${run_directory}/explain" "$MONGO_URI" 1 2>/dev/null || true
    else
        java -jar "$BENCH_JAR" explain "${run_directory}/explain" "$MONGO_URI" 1 2>/dev/null || true
    fi
}

run_jmh() {
    local run_directory=$1
    local abs_run_dir
    abs_run_dir="$(cd "$run_directory" && pwd)"
    echo "Running JMH SearchBenchmark..."
    mvn -DskipTests install
    mvn -f services/query/pom.xml exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.query.benchmarks.SearchBenchmark ${abs_run_dir}"
    if [[ -f "${run_directory}/jmh/query.json" ]]; then
        echo "Converting JMH JSON to CSV..."
        java -jar "$BENCH_JAR" jmh-csv "${run_directory}/jmh/query.json" "${run_directory}/jmh/query.csv"
    fi
}

measure_latency() {
    local run_directory=$1

    echo "Setting up baseline configuration: replicas=${BASELINE_REPLICAS}, threads=${BASELINE_THREADS}"
    TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --scale "query=${BASELINE_REPLICAS}" query
    wait_for_query_ready

    export_explain "$run_directory"

    echo "=== Series 1: Varying concurrent clients (${CLIENTS_CONFIG}) with threads=${BASELINE_THREADS}, replicas=${BASELINE_REPLICAS} ==="
    echo "Hypothesis: As concurrent clients increase, throughput scales until concurrency saturation while mean and p95 latency increase due to queuing."
    for client_count in $CLIENTS_CONFIG; do
        local config_name="clients_${client_count}"
        local series_config="series=clients,clients=${client_count},threads=${BASELINE_THREADS},replicas=${BASELINE_REPLICAS}"
        echo "Running load test: ${series_config}"
        java -jar "$BENCH_JAR" load "$FRONTEND_URL" "$run_directory" "$client_count" "$config_name"
        java -jar "$BENCH_JAR" summary "$run_directory" "$series_config" "${run_directory}/load/${config_name}.csv" "search_latency"
    done

    echo "=== Series 2: Varying TARANTINO_HTTP_THREADS (${THREADS_CONFIG}) with clients=${BASELINE_CLIENTS}, replicas=${BASELINE_REPLICAS} ==="
    echo "Hypothesis: Increasing Javalin HTTP worker threads improves concurrent request handling until CPU core saturation."
    for thread_count in $THREADS_CONFIG; do
        echo "Recreating query with TARANTINO_HTTP_THREADS=${thread_count}..."
        TARANTINO_HTTP_THREADS="$thread_count" docker compose up -d --force-recreate --scale "query=${BASELINE_REPLICAS}" query
        wait_for_query_ready
        local config_name="threads_${thread_count}"
        local series_config="series=threads,clients=${BASELINE_CLIENTS},threads=${thread_count},replicas=${BASELINE_REPLICAS}"
        echo "Running load test: ${series_config}"
        java -jar "$BENCH_JAR" load "$FRONTEND_URL" "$run_directory" "$BASELINE_CLIENTS" "$config_name"
        java -jar "$BENCH_JAR" summary "$run_directory" "$series_config" "${run_directory}/load/${config_name}.csv" "search_latency"
    done

    echo "Restoring baseline threads before series 3..."
    TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --force-recreate --scale "query=${BASELINE_REPLICAS}" query
    wait_for_query_ready

    echo "=== Series 3: Varying query replicas (${REPLICAS_CONFIG}) with clients=${BASELINE_CLIENTS}, threads=${BASELINE_THREADS} ==="
    echo "Hypothesis: Scaling query replicas behind nginx distributes CPU-bound search operations, decreasing p95 latency under high load."
    for replica_count in $REPLICAS_CONFIG; do
        echo "Scaling query service to ${replica_count} replicas..."
        TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --scale "query=${replica_count}" query
        wait_for_query_ready
        local config_name="replicas_${replica_count}"
        local series_config="series=replicas,clients=${BASELINE_CLIENTS},threads=${BASELINE_THREADS},replicas=${replica_count}"
        echo "Running load test: ${series_config}"
        java -jar "$BENCH_JAR" load "$FRONTEND_URL" "$run_directory" "$BASELINE_CLIENTS" "$config_name"
        java -jar "$BENCH_JAR" summary "$run_directory" "$series_config" "${run_directory}/load/${config_name}.csv" "search_latency"
    done
}

main() {
    ensure_bench_jar

    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" search-latency "$BASELINE_CLIENTS" -- "$0" "$@")
    local run_directory="${RUNS_DIR}/${run_id}"

    mkdir -p "${run_directory}/load" "${run_directory}/explain" "${run_directory}/jmh" "${run_directory}/logs"

    exec > >(tee -a "${run_directory}/log.txt") 2>&1
    trap stop_stats EXIT

    check_corpus "$run_directory"

    run_jmh "$run_directory"

    start_stats "$run_directory"
    measure_latency "$run_directory"
    stop_stats

    save_service_logs "$run_directory"

    java -jar "$BENCH_JAR" manifest finish "$run_directory"
    java -jar "$BENCH_JAR" charts "$run_directory" || true

    echo "Benchmark completed successfully. Results in ${run_directory}"
}

main "$@"
