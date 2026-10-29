#!/usr/bin/env bash
set -euo pipefail

readonly WORKLOAD_DIR="${TARANTINO_WORKLOAD:-workload}"
readonly BENCHMARKS_DIR="${TARANTINO_BENCHMARKS:-benchmarks}"
readonly CORPUS_SHA="${WORKLOAD_DIR}/corpus.sha256"
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

verify_corpus() {
    ensure_bench_jar
    java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.run.RunMetadataWriter verify-corpus "$CORPUS_SHA"
}

init_run() {
    local timestamp commit run_id run_dir
    timestamp=$(date +%Y%m%d-%H%M%S)
    commit=$(git rev-parse --short HEAD 2>/dev/null || echo "0000000")
    run_id="${timestamp}-search-latency-${commit}"
    run_dir="${BENCHMARKS_DIR}/runs/${run_id}"
    mkdir -p "${run_dir}/load" "${run_dir}/explain" "${run_dir}/jmh" "${run_dir}/logs"
    echo "$run_dir"
}

write_manifest() {
    local run_dir=$1 run_id=$2 command_invoked=$3 started_at=$4 finished_at=$5
    java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.run.RunMetadataWriter write-manifest \
        "$run_dir" "$run_id" "search-latency" "$command_invoked" "$started_at" "$finished_at" \
        "$CLIENTS_CONFIG" "$THREADS_CONFIG" "$REPLICAS_CONFIG"
}

generate_corpus_json() {
    local run_dir=$1
    java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.run.RunMetadataWriter write-corpus \
        "$run_dir" "$CORPUS_SHA" "$MONGO_URI" 1
}

save_service_logs() {
    local run_dir=$1
    echo "Saving service logs in ${run_dir}/logs..."
    mkdir -p "${run_dir}/logs"
    if command -v docker >/dev/null 2>&1; then
        for service in crawler indexer query control nginx mongo hazelcast; do
            docker compose logs --no-color --timestamps "$service" > "${run_dir}/logs/${service}.log" 2>/dev/null || true
        done
    fi
}

start_docker_stats() {
    local stats_file=$1
    echo "timestamp,container,cpu_percent,mem_usage,mem_percent,net_io,block_io,pids" > "$stats_file"
    (
        while true; do
            local current_time
            current_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)
            if command -v docker >/dev/null 2>&1; then
                docker stats --no-stream --format "${current_time},{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}" 2>/dev/null >> "$stats_file" || true
            fi
            sleep 1
        done
    ) &
    STATS_PID=$!
}

stop_docker_stats() {
    if [[ -n "${STATS_PID}" ]]; then
        kill "$STATS_PID" 2>/dev/null || true
        wait "$STATS_PID" 2>/dev/null || true
        STATS_PID=""
    fi
}

wait_for_query_ready() {
    local url="${FRONTEND_URL}/search/filters"
    local max_attempts=120
    local attempt=0
    local consecutive_successes=0
    echo "Waiting for ${url} to answer 200 with total_books > 0 across 10 consecutive requests..."
    while (( attempt < max_attempts && consecutive_successes < 10 )); do
        local status_code
        status_code=$(curl -s -o /tmp/filters_check.json -w "%{http_code}" "${url}" 2>/dev/null || echo "000")
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
    local run_dir=$1
    echo "Saving explain plans in ${run_dir}/explain..."
    if command -v docker >/dev/null 2>&1 && docker compose ps query 2>/dev/null | grep -q "query"; then
        docker compose cp --index=1 "$BENCH_JAR" query:/tmp/bench.jar
        docker compose exec -T --index=1 query java -cp "/tmp/bench.jar" org.ulpgc.tarantino.bench.explain.ExplainPlanExporter "/tmp/explain" "$MONGO_URI" 1
        docker compose cp --index=1 query:/tmp/explain/. "${run_dir}/explain/"
        docker compose exec -T --index=1 query rm -rf /tmp/bench.jar /tmp/explain
    else
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.explain.ExplainPlanExporter "${run_dir}/explain" "$MONGO_URI" 1
    fi
}

run_jmh() {
    local run_dir=$1
    local abs_run_dir
    abs_run_dir="$(cd "$run_dir" && pwd)"
    echo "Running JMH SearchBenchmark..."
    mvn -DskipTests install
    mvn -f services/query/pom.xml exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.query.benchmarks.SearchBenchmark ${abs_run_dir}"
    if [[ -f "${run_dir}/jmh/query.json" ]]; then
        echo "Converting JMH JSON to CSV..."
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.jmh.JmhCsvConverter "${run_dir}/jmh/query.json" "${run_dir}/jmh/query.csv"
    fi
}

measure_latency() {
    local run_dir=$1

    echo "Setting up baseline configuration: replicas=${BASELINE_REPLICAS}, threads=${BASELINE_THREADS}"
    TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --scale "query=${BASELINE_REPLICAS}" query
    wait_for_query_ready

    export_explain "$run_dir"

    echo "=== Series 1: Varying concurrent clients (${CLIENTS_CONFIG}) with threads=${BASELINE_THREADS}, replicas=${BASELINE_REPLICAS} ==="
    echo "Hypothesis: As concurrent clients increase, throughput scales until concurrency saturation while mean and p95 latency increase due to queuing."
    for clients in $CLIENTS_CONFIG; do
        local config_name="clients_${clients}"
        local config="series=clients,clients=${clients},threads=${BASELINE_THREADS},replicas=${BASELINE_REPLICAS}"
        echo "Running load test: ${config}"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.load.SearchLoadGenerator "$FRONTEND_URL" "$run_dir" "$clients" "$config_name"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.stats.SummaryStats "$run_dir" "$config" "${run_dir}/load/${config_name}.csv" "search_latency"
    done

    echo "=== Series 2: Varying TARANTINO_HTTP_THREADS (${THREADS_CONFIG}) with clients=${BASELINE_CLIENTS}, replicas=${BASELINE_REPLICAS} ==="
    echo "Hypothesis: Increasing Javalin HTTP worker threads improves concurrent request handling until CPU core saturation."
    for threads in $THREADS_CONFIG; do
        echo "Recreating query with TARANTINO_HTTP_THREADS=${threads}..."
        TARANTINO_HTTP_THREADS="$threads" docker compose up -d --force-recreate --scale "query=${BASELINE_REPLICAS}" query
        wait_for_query_ready
        local config_name="threads_${threads}"
        local config="series=threads,clients=${BASELINE_CLIENTS},threads=${threads},replicas=${BASELINE_REPLICAS}"
        echo "Running load test: ${config}"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.load.SearchLoadGenerator "$FRONTEND_URL" "$run_dir" "$BASELINE_CLIENTS" "$config_name"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.stats.SummaryStats "$run_dir" "$config" "${run_dir}/load/${config_name}.csv" "search_latency"
    done

    echo "Restoring baseline threads before series 3..."
    TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --force-recreate --scale "query=${BASELINE_REPLICAS}" query
    wait_for_query_ready

    echo "=== Series 3: Varying query replicas (${REPLICAS_CONFIG}) with clients=${BASELINE_CLIENTS}, threads=${BASELINE_THREADS} ==="
    echo "Hypothesis: Scaling query replicas behind nginx distributes CPU-bound search operations, decreasing p95 latency under high load."
    for replicas in $REPLICAS_CONFIG; do
        echo "Scaling query service to ${replicas} replicas..."
        TARANTINO_HTTP_THREADS="${BASELINE_THREADS}" docker compose up -d --scale "query=${replicas}" query
        wait_for_query_ready
        local config_name="replicas_${replicas}"
        local config="series=replicas,clients=${BASELINE_CLIENTS},threads=${BASELINE_THREADS},replicas=${replicas}"
        echo "Running load test: ${config}"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.load.SearchLoadGenerator "$FRONTEND_URL" "$run_dir" "$BASELINE_CLIENTS" "$config_name"
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.stats.SummaryStats "$run_dir" "$config" "${run_dir}/load/${config_name}.csv" "search_latency"
    done
}

main() {
    local command_invoked="$0 $*"
    local started_at finished_at run_dir run_id
    started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    ensure_bench_jar
    verify_corpus
    run_dir=$(init_run)
    run_id=$(basename "$run_dir")

    exec > >(tee -a "${run_dir}/log.txt") 2>&1
    trap stop_docker_stats EXIT

    write_manifest "$run_dir" "$run_id" "$command_invoked" "$started_at" ""

    run_jmh "$run_dir"

    start_docker_stats "${run_dir}/stats.csv"
    measure_latency "$run_dir"
    stop_docker_stats

    generate_corpus_json "$run_dir"
    save_service_logs "$run_dir"

    finished_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    write_manifest "$run_dir" "$run_id" "$command_invoked" "$started_at" "$finished_at"

    echo "Benchmark completed successfully. Results in ${run_dir}"
}

main "$@"
