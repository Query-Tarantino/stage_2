#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly BASELINE_CLIENTS="${TARANTINO_BASELINE_CLIENTS:-32}"
readonly BASELINE_THREADS="${TARANTINO_BASELINE_THREADS:-32}"
readonly BASELINE_REPLICAS="${TARANTINO_BASELINE_REPLICAS:-1}"

readonly CLIENTS_LIST="${TARANTINO_CLIENTS_LIST:-1 8 32 64}"
readonly THREADS_LIST="${TARANTINO_THREADS_LIST:-8 32 128}"
readonly FRONTEND_URL="${TARANTINO_FRONTEND_URL:-http://localhost:8080}"
readonly MONGO_URI="${TARANTINO_MONGO_URI:-mongodb://mongo:27017/tarantino}"

trial_started_at=""

to_windows_path() {
    local path=$1
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$path"
    else
        echo "$path"
    fi
}

open_run() {
    local experiment=$1
    shift
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" "$experiment" "$BASELINE_CLIENTS" -- "$@")
    run_dir="$RUNS_DIR/$run_id"
    mkdir -p "$run_dir/logs" "$run_dir/load" "$run_dir/explain" "$run_dir/jmh"
    exec > >(tee -a "$run_dir/log.txt") 2>&1
    trap close_run EXIT
    echo "Opened run $run_id: candidates $TARANTINO_CANDIDATES, repetitions $REPETITIONS, baseline clients $BASELINE_CLIENTS, threads $BASELINE_THREADS, replicas $BASELINE_REPLICAS"
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

state_hypotheses() {
    echo "Factor: 8 configurations across concurrency (1 8 32 64 clients), worker threads (8 32 128) and query replicas (1 2 3), $REPETITIONS repetitions each, interleaved: repetition 1 of all configurations, then repetition 2, so thermal and JVM drift spread across every configuration"
    echo "H1 (concurrency): At 1 and 8 clients, mean latency stays low and flat because requests easily fit on the 8 P-cores of the official machine with no queuing. At 32 clients, requests exceed the 8 P-cores and spill onto slower E-cores, causing queuing; throughput saturates and p95 latency grows significantly. At 64 clients, queuing dominates and latency increases roughly linearly with concurrency (Little's Law under saturation)"
    echo "H2 (HTTP worker threads): With 8 threads, the JVM thread pool matches the 8 P-cores, avoiding E-core thread contention, but under 32 concurrent clients Jetty queues connections in the OS socket backlog, inflating latency tail. With 32 threads, Javalin handles the 32 clients concurrently, utilizing P-cores and spilling into E-cores with moderate context switching. With 128 threads, thread context switching and thread pool contention across the hybrid cores introduce overhead without throughput gains, increasing p95 and p99 jitter"
    echo "H3 (query replicas): With 1 replica, a single JVM handles all search queries and MongoDB round-trips. Scaling to 2 and 3 replicas behind nginx distributes CPU-bound JSON parsing, TF-IDF ranking, and socket I/O across independent JVM heaps, decreasing mean and p95 latency under high concurrency (32 clients) as long as aggregate CPU fits within the 24 Docker CPUs"
}

ensure_bench_jar() {
    if [[ ! -f "$BENCH_JAR" ]]; then
        mvn -f bench/pom.xml package -DskipTests
    fi
}

search_trial_name() {
    local clients=$1 threads=$2 replicas=$3 repetition=$4
    echo "clients-$clients-threads-$threads-replicas-$replicas-rep-$repetition"
}

configure_query() {
    local replicas=$1 threads=$2
    TARANTINO_HTTP_THREADS="$threads" "${COMPOSE[@]}" up -d --scale "query=$replicas" query
}

recreate_query() {
    local replicas=$1 threads=$2
    TARANTINO_HTTP_THREADS="$threads" "${COMPOSE[@]}" up -d --force-recreate --scale "query=$replicas" query
}

wait_for_query_ready() {
    local endpoint_url="${FRONTEND_URL}/search/filters"
    local max_attempts=120
    local attempt=0
    local consecutive_successes=0
    local temp_file
    temp_file=$(mktemp 2>/dev/null || echo "filters_check.json")
    echo "Waiting for ${endpoint_url} to answer 200 with total_books > 0 across 10 consecutive requests..."
    while (( attempt < max_attempts && consecutive_successes < 10 )); do
        local status_code
        status_code=$(curl -s -o "$temp_file" -w "%{http_code}" "${endpoint_url}" 2>/dev/null || echo "000")
        if [[ "$status_code" == "200" && -f "$temp_file" ]]; then
            local total_books
            total_books=$(grep -o '"total_books":[0-9]*' "$temp_file" | cut -d: -f2 || echo 0)
            if [[ -n "$total_books" && "$total_books" -gt 0 ]]; then
                consecutive_successes=$((consecutive_successes + 1))
            else
                consecutive_successes=0
            fi
        else
            consecutive_successes=0
        fi
        sleep 0.2
        attempt=$((attempt + 1))
    done
    rm -f "$temp_file"
    if (( consecutive_successes >= 10 )); then
        echo "Query service is ready across all replicas"
        return 0
    fi
    echo "Error: Query service did not answer 10 consecutive 200s with total_books > 0 within ${max_attempts} attempts" >&2
    exit 1
}

export_explain() {
    echo "Saving explain plans in ${run_dir}/explain..."
    mkdir -p "${run_dir}/explain"
    if command -v docker >/dev/null 2>&1 && "${COMPOSE[@]}" ps query 2>/dev/null | grep -q "query"; then
        "${COMPOSE[@]}" cp --index=1 "$BENCH_JAR" query:/tmp/bench.jar
        MSYS_NO_PATHCONV=1 "${COMPOSE[@]}" exec -T --index=1 query java -cp "/tmp/bench.jar" org.ulpgc.tarantino.bench.explain.ExplainPlanExporter "/tmp/explain" "$MONGO_URI" 1
        "${COMPOSE[@]}" cp --index=1 query:/tmp/explain/. "${run_dir}/explain/"
        MSYS_NO_PATHCONV=1 "${COMPOSE[@]}" exec -T --index=1 query rm -rf /tmp/bench.jar /tmp/explain
    else
        java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.explain.ExplainPlanExporter "${run_dir}/explain" "$MONGO_URI" 1
    fi
}

run_jmh() {
    local absolute_run_dir
    absolute_run_dir=$(to_windows_path "$(cd "$run_dir" && pwd)")
    echo "Running JMH SearchBenchmark..."
    mvn -DskipTests install
    mvn -f services/query/pom.xml exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.query.benchmarks.SearchBenchmark \"${absolute_run_dir}\""
    if [[ -f "${run_dir}/jmh/query.json" ]]; then
        echo "Converting JMH JSON to CSV..."
        java -jar "$BENCH_JAR" jmh-csv "${run_dir}/jmh/query.json" "${run_dir}/jmh/query.csv"
    fi
}

save_trial_logs() {
    local trial_logs="$run_dir/logs/$current_trial"
    mkdir -p "$trial_logs"
    local service
    for service in "${SERVICES[@]}"; do
        "${COMPOSE[@]}" logs --no-color --timestamps --since "$trial_started_at" "$service" > "$trial_logs/$service.log"
    done
    current_trial=""
}

run_search_trial() {
    local clients=$1 threads=$2 replicas=$3 repetition=$4
    local trial
    trial=$(search_trial_name "$clients" "$threads" "$replicas" "$repetition")
    current_trial="$trial"
    trial_started_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    echo "=== Trial $trial: clients=$clients, threads=$threads, replicas=$replicas, rep=$repetition ==="
    recreate_query "$replicas" "$threads"
    wait_for_query_ready
    record_trial
    local trial_csv="$run_dir/load/${trial}.csv.gz"
    java -jar "$BENCH_JAR" load "$FRONTEND_URL" "$run_dir" "$clients" "$trial"
    local non_200
    non_200=$(gzip -dc "$trial_csv" 2>/dev/null | awk -F',' 'NR > 1 && $3 != "200" { count++ } END { print count+0 }' || echo 0)
    echo "Trial $trial non-200 responses: $non_200"
    java -jar "$BENCH_JAR" summary "$run_dir" "$trial" "$trial_csv" search_latency
    save_trial_logs
}

measure() {
    local configurations=(
        "1 32 1"
        "8 32 1"
        "32 32 1"
        "64 32 1"
        "32 8 1"
        "32 128 1"
        "32 32 2"
        "32 32 3"
    )
    local repetition
    for repetition in $(seq 1 "$REPETITIONS"); do
        echo "=== Interleaved Repetition $repetition of $REPETITIONS ==="
        local cfg c t r
        for cfg in "${configurations[@]}"; do
            read -r c t r <<< "$cfg"
            run_search_trial "$c" "$t" "$r" "$repetition"
        done
    done
}

summarise_run() {
    echo "Summarising search latency per configuration across repetitions..."
    local unique_configs=(
        "1 32 1"
        "8 32 1"
        "32 32 1"
        "64 32 1"
        "32 8 1"
        "32 128 1"
        "32 32 2"
        "32 32 3"
    )
    local cfg c t r
    for cfg in "${unique_configs[@]}"; do
        read -r c t r <<< "$cfg"
        local config_label="clients=${c},threads=${t},replicas=${r}"
        local pooled_gz="$run_dir/load/pooled.csv.gz"
        rm -f "$pooled_gz"
        local rep
        for rep in $(seq 1 "$REPETITIONS"); do
            local trial
            trial=$(search_trial_name "$c" "$t" "$r" "$rep")
            local trial_csv="$run_dir/load/${trial}.csv.gz"
            if [[ -f "$trial_csv" ]]; then
                cat "$trial_csv" >> "$pooled_gz"
            fi
        done
        if [[ -f "$pooled_gz" ]]; then
            java -jar "$BENCH_JAR" summary "$run_dir" "$config_label" "$pooled_gz" search_latency
            rm -f "$pooled_gz"
        fi
    done
}

main() {
    ensure_bench_jar
    open_run search-latency "$0" "$@"
    state_hypotheses
    check_corpus
    wait_for PUBLISHED "$(expected_books)"
    build_stack
    configure_query "$BASELINE_REPLICAS" "$BASELINE_THREADS"
    wait_for_query_ready
    export_explain
    run_jmh
    start_stats
    measure
    stop_stats
}

main "$@"
