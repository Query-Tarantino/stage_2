#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

source scripts/bench/modes.sh

readonly BENCH_JAR=bench/target/bench-1.0-SNAPSHOT.jar
readonly RUNS_DIR=benchmarks/runs
readonly MIRROR_DIR=benchmarks/mirror
readonly CORPUS_SHA=workload/corpus.sha256
readonly BRANCHES=(dev arch-testing)
readonly OFFICIAL_CPUS=24
readonly MINIMUM_MEMORY_GIB=15
readonly BYTES_PER_GIB=$((1024 * 1024 * 1024))
readonly WRONG_USAGE=2
readonly OVERRIDING_VARIABLES='^(TARANTINO_.*|JAVA_TOOL_OPTIONS|CRAWLER_CRASH_AT|INDEXER_CRASH_AT)$'
readonly ROWS=(01 02 03 04 05 06 07 08 09 10 11 12 13 14)
readonly MIRROR_ROWS=(09 10 11 12)
readonly DELAY_ROWS=(04 05 12)
readonly EXPERIMENTS=(ingest-throughput index-throughput resource-usage publication-visibility broker-latency
    coordination-variants)
readonly WITHOUT_JMH=--without-jmh

dry_run=false
only_step=""
campaign_dir=""
runs_txt=""

usage() {
    echo "Usage: $0 [--dry-run] [--only <step>]" >&2
    exit "$WRONG_USAGE"
}

read_flags() {
    while (( $# > 0 )); do
        case $1 in
            --dry-run) dry_run=true ;;
            --only)
                (( $# > 1 )) || usage
                only_step=$2
                shift
                ;;
            *) usage ;;
        esac
        shift
    done
}

row_applies() {
    local mode=$1 row=$2
    case $mode:$row in
        polling:04 | polling:05 | polling:12 | orchestrator:12) return 1 ;;
        *) return 0 ;;
    esac
}

replicas_of_row() {
    local mode=$1 row=$2
    if [[ $row == 14 ]]; then
        echo "2 3"
    elif [[ $mode != hazelcast ]] && is_mirror_row "$row"; then
        echo "1"
    else
        echo "1 2 3"
    fi
}

is_mirror_row() {
    [[ " ${MIRROR_ROWS[*]} " == *" $1 "* ]]
}

recovery_script() {
    ls scripts/recovery/"$1"-*.sh
}

recovery_steps() {
    local mode row replicas
    for mode in "${MODES[@]}"; do
        for row in "${ROWS[@]}"; do
            row_applies "$mode" "$row" || continue
            for replicas in $(replicas_of_row "$mode" "$row"); do
                echo "recovery-$mode-$row-r$replicas"
            done
        done
    done
}

steps() {
    printf '%s\n' "${EXPERIMENTS[@]}"
    recovery_steps
    printf 'recovery-compare-%s\n' "${ROWS[@]}"
    echo recovery-summary
    echo recovery-delay
}

command_of() {
    local step=$1
    case $step in
        recovery-compare-*) echo "java -jar $BENCH_JAR recovery-compare ${step#recovery-compare-}" ;;
        recovery-summary) echo "recovery summary over every recovery run: bench manifest start, bench recovery-chart, bench manifest finish" ;;
        recovery-delay) echo "scripts/bench/recovery-delay.sh <the runs of rows ${DELAY_ROWS[*]} in every mode where they apply>" ;;
        recovery-*) recovery_command "$step" ;;
        index-throughput) echo "scripts/bench/$step.sh $WITHOUT_JMH" ;;
        *) echo "scripts/bench/$step.sh" ;;
    esac
}

recovery_command() {
    local step=${1#recovery-} mode row replicas
    replicas=${step##*-r}
    step=${step%-r*}
    row=${step##*-}
    mode=${step%-*}
    echo "TARANTINO_VARIANT=$mode $(recovery_script "$row") $replicas"
}

fail() {
    echo "Not starting the campaign: $1" >&2
    exit 1
}

check_preconditions() {
    [[ " ${BRANCHES[*]} " == *" $(git rev-parse --abbrev-ref HEAD) "* ]] || fail "the current branch is not one of ${BRANCHES[*]}"
    local overrides
    overrides=$(compgen -e | grep -E "$OVERRIDING_VARIABLES" | tr '\n' ' ')
    [[ -z $overrides ]] || fail "$overrides would change the defaults every step and its hypotheses rely on; unset them"
    [[ -z $(git status --porcelain -- ":(top,exclude)$RUNS_DIR") ]] || fail "git status shows changes outside $RUNS_DIR"
    local cpus memory
    cpus=$(docker info --format '{{.NCPU}}')
    memory=$(docker info --format '{{.MemTotal}}')
    (( cpus == OFFICIAL_CPUS )) || fail "docker info shows $cpus CPUs, not the $OFFICIAL_CPUS of the official machine"
    (( memory >= MINIMUM_MEMORY_GIB * BYTES_PER_GIB )) || fail "docker info shows less than $MINIMUM_MEMORY_GIB GiB"
    local missing
    missing=$(awk '{print $1}' "$CORPUS_SHA" | while read -r book; do [[ -f $MIRROR_DIR/$book/pg$book.txt ]] || echo "$book"; done | wc -l)
    (( missing == 0 )) || fail "$MIRROR_DIR lacks $missing books of $CORPUS_SHA"
    [[ -f $BENCH_JAR ]] || fail "$BENCH_JAR does not exist; run mvn -DskipTests package"
}

list_runs() {
    ls -1 "$RUNS_DIR"
}

run_step() {
    local step=$1 before after status new_runs
    echo "=== Step $step: $(command_of "$step") ==="
    before=$(list_runs)
    execute "$step" < /dev/null
    status=$?
    after=$(list_runs)
    new_runs=$(comm -13 <(echo "$before") <(echo "$after") | grep -vx "$(basename "$campaign_dir")" | tr '\n' ' ')
    echo "$step $status $new_runs" >> "$runs_txt"
}

execute() {
    local step=$1
    case $step in
        recovery-compare-*) java -jar "$BENCH_JAR" recovery-compare "${step#recovery-compare-}" ;;
        recovery-summary) recovery_summary ;;
        recovery-delay) recovery_delay ;;
        recovery-*) bash -c "$(recovery_command "$step")" ;;
        index-throughput) "scripts/bench/$step.sh" "$WITHOUT_JMH" ;;
        *) "scripts/bench/$step.sh" ;;
    esac
}

recovery_summary() {
    local run_id
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" recovery-summary 0 -- "$0" recovery-summary) || return 1
    java -jar "$BENCH_JAR" recovery-chart "$RUNS_DIR/$run_id"
    local status=$?
    java -jar "$BENCH_JAR" manifest finish "$RUNS_DIR/$run_id"
    return "$status"
}

recovery_delay() {
    local runs=() row
    for row in "${DELAY_ROWS[@]}"; do
        mapfile -t -O "${#runs[@]}" runs < <(grep -E "^recovery-[a-z-]+-$row-r[0-9] 0 " "$runs_txt" | awk '{for (i = 3; i <= NF; i++) print "'"$RUNS_DIR"'/" $i}')
    done
    if (( ${#runs[@]} == 0 )); then
        echo "No recovery run of rows ${DELAY_ROWS[*]} in $runs_txt; using every one in $RUNS_DIR"
        mapfile -t runs < <(list_runs | grep -E -- "-recovery-($(IFS='|'; echo "${DELAY_ROWS[*]}"))-" | sed "s|^|$RUNS_DIR/|")
    fi
    (( ${#runs[@]} > 0 )) || { echo "No recovery run of rows ${DELAY_ROWS[*]} in $RUNS_DIR" >&2; return 1; }
    scripts/bench/recovery-delay.sh "${runs[@]}"
}

print_dry_run() {
    echo "Preconditions: branch ${BRANCHES[0]} or ${BRANCHES[1]}; no TARANTINO_*, JAVA_TOOL_OPTIONS or *_CRASH_AT variable set; nothing changed outside $RUNS_DIR; docker info with $OFFICIAL_CPUS CPUs and at least $MINIMUM_MEMORY_GIB GiB; every book of $CORPUS_SHA in $MIRROR_DIR; $BENCH_JAR built"
    echo "Opening: java -jar $BENCH_JAR manifest start $RUNS_DIR campaign 0 -- $0"
    local step number=0
    while read -r step; do
        number=$((number + 1))
        echo "$number. $step: $(command_of "$step")"
    done < <(selected_steps)
    echo "Closing: java -jar $BENCH_JAR manifest finish <campaign run>"
}

selected_steps() {
    if [[ -n $only_step ]]; then
        echo "$only_step"
    else
        steps
    fi
}

check_only_step() {
    [[ -z $only_step ]] || steps | grep -qx "$only_step" || fail "$only_step is not a step; see $0 --dry-run"
}

main() {
    read_flags "$@"
    check_only_step
    if $dry_run; then
        print_dry_run
        return
    fi
    check_preconditions
    local run_id step
    run_id=$(java -jar "$BENCH_JAR" manifest start "$RUNS_DIR" campaign 0 -- "$0" "$@") || fail "could not open the campaign run"
    campaign_dir="$RUNS_DIR/$run_id"
    runs_txt="$campaign_dir/runs.txt"
    touch "$runs_txt"
    exec > >(tee -a "$campaign_dir/log.txt") 2>&1
    echo "Opened run $run_id"
    local campaign_steps=()
    mapfile -t campaign_steps < <(selected_steps)
    for step in "${campaign_steps[@]}"; do
        run_step "$step"
    done
    java -jar "$BENCH_JAR" manifest finish "$campaign_dir"
    echo "Campaign in $campaign_dir; steps and run folders in $runs_txt"
}

main "$@"
