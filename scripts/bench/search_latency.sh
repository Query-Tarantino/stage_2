#!/usr/bin/env bash
set -euo pipefail

readonly WORKLOAD_DIR="${TARANTINO_WORKLOAD:-workload}"
readonly BENCHMARKS_DIR="${TARANTINO_BENCHMARKS:-benchmarks}"
readonly CORPUS_SHA="${WORKLOAD_DIR}/corpus.sha256"
readonly FRONTEND_URL="${TARANTINO_FRONTEND_URL:-http://localhost:8080}"
readonly MONGO_URI="${TARANTINO_MONGO_URI:-mongodb://localhost:27017}"
readonly BENCH_JAR="bench/target/bench-1.0-SNAPSHOT.jar"
readonly REQUESTS_COUNT="${TARANTINO_BENCH_REQUESTS:-100}"

readonly CLIENTS_CONFIG="${TARANTINO_CLIENTS_LIST:-1 4 16 32}"
readonly THREADS_CONFIG="${TARANTINO_THREADS_LIST:-8 16 32}"
readonly REPLICAS_CONFIG="${TARANTINO_REPLICAS_LIST:-1 2 3}"

verify_corpus() {
    if [[ ! -f "$CORPUS_SHA" ]]; then
        echo "Error: Corpus checksum file $CORPUS_SHA not found" >&2
        exit 1
    fi
    echo "Verifying corpus against $CORPUS_SHA..."
    while read -r expected_hash file_path; do
        [[ -z "$expected_hash" || "$expected_hash" =~ ^# ]] && continue
        if [[ ! -f "$file_path" ]]; then
            echo "Error: Missing corpus file $file_path" >&2
            exit 1
        fi
        actual_hash=$(sha256sum "$file_path" | awk '{print $1}')
        if [[ "$actual_hash" != "$expected_hash" ]]; then
            echo "Error: SHA-256 mismatch for $file_path (expected $expected_hash, got $actual_hash)" >&2
            exit 1
        fi
    done < "$CORPUS_SHA"
    echo "Corpus checksums verified successfully"
}

ensure_bench_jar() {
    if [[ ! -f "$BENCH_JAR" ]]; then
        mvn -f bench/pom.xml package -DskipTests
    fi
}

init_run() {
    local timestamp commit run_id run_dir
    timestamp=$(date +%Y%m%d-%H%M%S)
    commit=$(git rev-parse --short HEAD 2>/dev/null || echo "0000000")
    run_id="${timestamp}-search-latency-${commit}"
    run_dir="${BENCHMARKS_DIR}/runs/${run_id}"
    mkdir -p "${run_dir}/load" "${run_dir}/explain" "${run_dir}/jmh"
    echo "$run_dir"
}

write_manifest() {
    local run_dir=$1 run_id=$2
    local is_clean
    is_clean=$(git diff --quiet && echo "true" || echo "false")
    cat <<EOF > "${run_dir}/manifest.json"
{
  "run_id": "${run_id}",
  "commit": "$(git rev-parse HEAD 2>/dev/null || echo "unknown")",
  "clean_tree": ${is_clean},
  "experiment": "search-latency",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "os": "$(uname -s 2>/dev/null || echo "unknown")",
  "variables": {
    "TARANTINO_FRONTEND_URL": "${FRONTEND_URL}",
    "TARANTINO_MONGO_URI": "${MONGO_URI}",
    "clients": "${CLIENTS_CONFIG}",
    "threads": "${THREADS_CONFIG}",
    "replicas": "${REPLICAS_CONFIG}"
  }
}
EOF
}

export_explain() {
    local run_dir=$1
    echo "Saving explain plans in ${run_dir}/explain..."
    java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.explain.ExplainPlanExporter "${run_dir}/explain" "$MONGO_URI" 1 || true
}

measure_latency() {
    local run_dir=$1
    for replicas in $REPLICAS_CONFIG; do
        echo "Scaling query service to ${replicas} replicas..."
        docker compose up -d --scale "query=${replicas}" --no-recreate 2>/dev/null || true
        for threads in $THREADS_CONFIG; do
            export TARANTINO_HTTP_THREADS="$threads"
            for clients in $CLIENTS_CONFIG; do
                echo "Running load test: replicas=${replicas}, threads=${threads}, clients=${clients}"
                java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.load.SearchLoadGenerator "$FRONTEND_URL" "$run_dir" "$clients" "$REQUESTS_COUNT"
                java -cp "$BENCH_JAR" org.ulpgc.tarantino.bench.stats.SummaryStats "$run_dir" "replicas=${replicas},threads=${threads},clients=${clients}"
            done
        done
    done
}

main() {
    verify_corpus
    ensure_bench_jar
    local run_dir run_id
    run_dir=$(init_run)
    run_id=$(basename "$run_dir")
    write_manifest "$run_dir" "$run_id"
    export_explain "$run_dir"
    measure_latency "$run_dir"
    echo "Benchmark completed. Results in ${run_dir}"
}

main "$@"
