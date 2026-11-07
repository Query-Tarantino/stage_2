#!/usr/bin/env bash
source "$(dirname "${BASH_SOURCE[0]}")/trials.sh"

readonly EXPERIMENT=ingest-throughput
readonly NO_CLIENTS=0
readonly CONFIGURATIONS=("1 8" "2 8" "3 8" "1 4" "1 16")
readonly DOWNLOAD_TIMEOUT_SECONDS=3600
LOGGED_SERVICES=(crawler control)

main() {
    [[ -f $BENCH_JAR ]] || fail "$BENCH_JAR is missing; build it first with mvn -DskipTests package"
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" "$EXPERIMENT" "$NO_CLIENTS" -- "$0" "$@")
    run_dir="$RUNS_DIR/$run_id"
    mkdir -p "$run_dir/logs" "$run_dir/jmh"
    exec > >(tee -a "$run_dir/log.txt") 2>&1
    trap close_run EXIT
    print_hypotheses

    java -jar "$BENCH_JAR" corpus check "$CACHE_DIR" "$CORPUS_SHA" "$STOPWORDS" "$run_dir"
    run_jmh_of crawler org.ulpgc.tarantino.crawler.benchmarks.CrawlerBenchmarks
    "${COMPOSE[@]}" build crawler control
    start_stats
    measure_interleaved_repetitions
    stop_stats
}

summarise_run() {
    bench ingest-throughput "$run_dir"
    bench ingest-workers "$run_dir"
    bench resources "$run_dir"
}

print_hypotheses() {
    echo "Machine: the official one, with 24 hardware threads, 8 fast P cores and 16 slower E cores, without hyperthreading"
    echo "Premise: ingestion is bound by the crawlers' CPU, not by Mongo. In the preliminary run the crawlers used 400-790 % CPU and Mongo about 100 %, and JFR put over half of the crawler samples in GutenbergText.bookText"
    echo "Series replicas: 1, 2 and 3 crawler replicas with 8 workers each"
    echo "H1: throughput grows with replicas but less than linearly: 8 workers fit in the 8 P cores, while 16 and 24 spill onto the slower E cores, so the gain from 2 to 3 replicas is smaller than from 1 to 2"
    echo "Series workers: 4, 8 and 16 workers in 1 crawler replica"
    echo "H2: throughput grows from 4 to 8 workers, which still fit in the P cores, and flattens towards 16, whose extra workers run on the E cores"
    echo "Time windows: the whole one, from the first download claim to the last book downloaded, is the main figure; the steady one, between the downloads that complete 10 % and 90 % of the books, shows how much start-up and tail weigh"
}

measure_interleaved_repetitions() {
    echo "$REPETITIONS interleaved repetitions of ${#CONFIGURATIONS[@]} configurations"
    local repetition configuration replicas workers
    for (( repetition = 1; repetition <= REPETITIONS; repetition++ )); do
        for configuration in "${CONFIGURATIONS[@]}"; do
            read -r replicas workers <<< "$configuration"
            measure_trial "$replicas" "$workers" "$repetition"
        done
    done
}

measure_trial() {
    local replicas=$1 workers=$2 repetition=$3
    current_trial="replicas-$replicas-workers-$workers-rep-$repetition"
    echo "Trial $current_trial: seeding the $(corpus_books) books of the mirror"
    export TARANTINO_CANDIDATES=mirror TARANTINO_PARALLEL_DOWNLOADS=$workers
    "${COMPOSE[@]}" down -v
    "${COMPOSE[@]}" up -d --scale crawler=0 --scale indexer=0 --scale query=0
    bench wait QUEUED "$(corpus_books)"
    "${COMPOSE[@]}" up -d --scale crawler="$replicas" crawler
    java -jar "$BENCH_JAR" manifest trial "$run_dir" "$current_trial"
    bench wait DOWNLOADED "$(corpus_books)" "$DOWNLOAD_TIMEOUT_SECONDS"
    save_trial_logs
}

corpus_books() {
    grep -c . "$CORPUS_SHA"
}

bench() {
    "${COMPOSE[@]}" run --rm -T bench "$@"
}

fail() {
    echo "$1" >&2
    exit 1
}

main "$@"
