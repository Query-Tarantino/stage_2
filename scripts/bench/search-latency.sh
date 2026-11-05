#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly FRONTEND_URL=http://localhost:8080
readonly MONGO_URI=mongodb://mongo:27017/tarantino
readonly CONTROL_STATUS_URL=http://localhost:8081/control/status

readonly CONFIGURATIONS=(
    "1 32 1"
    "8 32 1"
    "32 32 1"
    "64 32 1"
    "32 8 1"
    "32 128 1"
    "32 32 2"
    "32 32 3"
)

trial_started_at=""

state_hypotheses() {
    echo "Factor: 8 configurations across concurrency (1 8 32 64 clients), worker threads (8 32 128) and query replicas (1 2 3), $REPETITIONS repetitions each, interleaved: repetition 1 of all configurations, then repetition 2, so thermal and JVM drift spread across every configuration"
    echo "H1 (concurrency): At 1 and 8 clients, mean latency stays low and flat because requests easily fit on the 8 P-cores of the official machine with no queuing. At 32 clients, requests exceed the 8 P-cores and spill onto slower E-cores, causing queuing; throughput saturates and p95 latency grows significantly. At 64 clients, queuing dominates and latency increases roughly linearly with concurrency (Little's Law under saturation)"
    echo "H2 (HTTP worker threads): With 8 threads, the JVM thread pool matches the 8 P-cores, avoiding E-core thread contention, but under 32 concurrent clients Jetty queues connections in the OS socket backlog, inflating latency tail. With 32 threads, Javalin handles the 32 clients concurrently, utilizing P-cores and spilling into E-cores with moderate context switching. With 128 threads, thread context switching and thread pool contention across the hybrid cores introduce overhead without throughput gains, increasing p95 and p99 jitter"
    echo "H3 (query replicas): With 1 replica, a single JVM handles all search queries and MongoDB round-trips. Scaling to 2 and 3 replicas behind nginx distributes CPU-bound JSON parsing, TF-IDF ranking, and socket I/O across independent JVM heaps, decreasing mean and p95 latency under high concurrency (32 clients) as long as aggregate CPU fits within the 24 Docker CPUs"
}

to_windows_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "$1"
    else
        echo "$1"
    fi
}

run_jmh() {
    local absolute_run_dir
    absolute_run_dir=$(to_windows_path "$(cd "$run_dir" && pwd)")
    echo "Running JMH SearchBenchmark..."
    mvn -f services/query/pom.xml exec:exec \
        -Dexec.executable=java \
        -Dexec.classpathScope=test \
        "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.query.benchmarks.SearchBenchmark \"${absolute_run_dir}\""
    echo "Converting JMH JSON to CSV..."
    java -jar "$BENCH_JAR" jmh-csv "${run_dir}/jmh/query.json" "${run_dir}/jmh/query.csv"
}

ingest_corpus() {
    echo "Ingesting mirror corpus until all books are PUBLISHED..."
    "${COMPOSE[@]}" up -d --wait
    wait_for PUBLISHED "$(expected_books)"
}

export_explain() {
    local generation
    generation=$(curl -fsS "$CONTROL_STATUS_URL" | grep -o '"generation":[0-9]*' | cut -d: -f2)
    echo "Saving explain plans for generation $generation in ${run_dir}/explain..."
    mkdir -p "${run_dir}/explain"
    "${COMPOSE[@]}" run --rm -T bench explain "$run_dir/explain" "$MONGO_URI" "$generation"
}

search_trial_name() {
    local clients=$1 threads=$2 replicas=$3 repetition=$4
    echo "clients-$clients-threads-$threads-replicas-$replicas-rep-$repetition"
}

recreate_query() {
    local replicas=$1 threads=$2
    TARANTINO_HTTP_THREADS="$threads" "${COMPOSE[@]}" up -d --wait --force-recreate --scale "query=$replicas" query
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
    local repetition
    for repetition in $(seq 1 "$REPETITIONS"); do
        echo "=== Interleaved Repetition $repetition of $REPETITIONS ==="
        local cfg c t r
        for cfg in "${CONFIGURATIONS[@]}"; do
            read -r c t r <<< "$cfg"
            run_search_trial "$c" "$t" "$r" "$repetition"
        done
    done
}

summarise_run() {
    java -jar "$BENCH_JAR" search-latency "$run_dir"
}

main() {
    open_run search-latency "$0" "$@"
    mkdir -p "$run_dir/load" "$run_dir/explain" "$run_dir/jmh"
    state_hypotheses
    check_corpus
    mvn -DskipTests install
    run_jmh
    build_stack
    ingest_corpus
    export_explain
    start_stats
    measure
}

main "$@"
