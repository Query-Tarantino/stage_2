#!/usr/bin/env bash
set -euo pipefail

# Same generated collection either way: rsync is Project Gutenberg's documented way to copy it in bulk, and
# HTTP, one request per book, is for networks that block the rsync port (873)
readonly RSYNC_SOURCE="${TARANTINO_RSYNC_SOURCE:-rsync.ibiblio.org::gutenberg-epub}"
readonly MIRROR_URL_TEMPLATE="https://mirror.cs.odu.edu/gutenberg-epub/%d/pg%d.txt"
readonly START_MARKER='\*\*\* ?START OF (THE|THIS) PROJECT GUTENBERG EBOOK'
readonly END_MARKER='\*\*\* ?END OF (THE|THIS) PROJECT GUTENBERG EBOOK'

readonly source="${TARANTINO_CACHE_SOURCE:-rsync}"
readonly workload_dir="${TARANTINO_WORKLOAD:-workload}"
readonly benchmarks_dir="${TARANTINO_BENCHMARKS:-benchmarks}"
readonly cache_dir="$benchmarks_dir/cache"
readonly mirror_dir="$benchmarks_dir/mirror"
readonly target_books="${TARANTINO_CACHE_BOOKS:-2800}"
readonly parallel_downloads="${TARANTINO_CACHE_PARALLEL:-16}"
readonly skipped_file="$cache_dir/skipped.txt"

cached_ids() {
    find "$cache_dir" -maxdepth 1 -name '*.txt' | sed -n 's|.*/\([0-9][0-9]*\)\.txt$|\1|p'
}

cached_count() {
    cached_ids | wc -l | tr -d ' '
}

known_count() {
    echo $(( $(cached_count) + $(wc -l < "$skipped_file") ))
}

# Candidates of book_ids.txt, in order, neither cached nor skipped; at most $1 of them when given
pending_ids() {
    { echo "#"; cached_ids; cat "$skipped_file"; } |
        awk -v needed="${1:-0}" 'NR == FNR { known[$1]; next }
                                 NF && !($1 in known) { print $1; if (needed && ++count == needed) exit }' - "$workload_dir/book_ids.txt"
}

has_markers() {
    grep -Eq "$START_MARKER" "$1" && grep -Eq "$END_MARKER" "$1"
}

# A downloaded file joins the cache if it has both markers; a missing one or one without markers is skipped
store_or_skip() {
    local id=$1 downloaded=$2
    if [[ -f $downloaded ]] && has_markers "$downloaded"; then
        mv "$downloaded" "$cache_dir/$id.txt"
    else
        rm -f "$downloaded"
        echo "$id" >> "$skipped_file"
    fi
}

# Rounds of one rsync transfer each, asking for twice the books still needed since some are skipped
fill_with_rsync() {
    while (( $(cached_count) < target_books )); do
        local batch list status=0 known_before
        batch=$(pending_ids $(( 2 * (target_books - $(cached_count)) )))
        [[ -n $batch ]] || fail "$workload_dir/book_ids.txt has fewer than $target_books downloadable books"
        known_before=$(known_count)
        list=$(mktemp)
        echo "$batch" | sed 's|.*|&/pg&.txt|' > "$list"
        echo "Copying $(wc -l < "$list" | tr -d ' ') candidate books from $RSYNC_SOURCE in one transfer"
        # 23 and 24: some files did not exist or vanished; those books are skipped below
        rsync -a --files-from="$list" "$RSYNC_SOURCE/" "$mirror_dir/" || status=$?
        rm -f "$list"
        (( status == 0 || status == 23 || status == 24 )) \
            || fail "rsync failed with status $status; where port 873 is blocked run with TARANTINO_CACHE_SOURCE=http"
        for id in $batch; do
            (( $(cached_count) < target_books )) || break
            store_or_skip "$id" "$mirror_dir/$id/pg$id.txt"
        done
        (( $(known_count) > known_before )) || fail "No progress in the last round; check the network and run again"
        echo "Cached $(cached_count)/$target_books books ($(wc -l < "$skipped_file" | tr -d ' ') skipped)"
    done
}

download_book() {
    local id=$1 temporary="$cache_dir/$1.txt.tmp" status
    status=$(curl -sSL --retry 3 --retry-delay 2 -o "$temporary" -w '%{http_code}' \
        "$(printf "$MIRROR_URL_TEMPLATE" "$id" "$id")") || status=000
    if [[ $status == 200 || $status == 404 ]]; then
        [[ $status == 200 ]] || rm -f "$temporary"
        store_or_skip "$id" "$temporary"
    else
        rm -f "$temporary"
        echo "Book $id failed with HTTP $status; it will be retried on the next run" >&2
    fi
}

fill_with_http() {
    export MIRROR_URL_TEMPLATE START_MARKER END_MARKER cache_dir skipped_file
    export -f download_book store_or_skip has_markers
    while (( $(cached_count) < target_books )); do
        local batch known_before
        batch=$(pending_ids $(( target_books - $(cached_count) )))
        [[ -n $batch ]] || fail "$workload_dir/book_ids.txt has fewer than $target_books downloadable books"
        known_before=$(known_count)
        echo "$batch" | xargs -P "$parallel_downloads" -I {} bash -c 'download_book "$1"' _ {}
        (( $(known_count) > known_before )) || fail "No progress in the last batch; check the network and run again"
        echo "Cached $(cached_count)/$target_books books ($(wc -l < "$skipped_file" | tr -d ' ') skipped)"
    done
}

fail() {
    echo "$1" >&2
    exit 1
}

main() {
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    mkdir -p "$cache_dir" "$mirror_dir"
    touch "$skipped_file"
    case "$source" in
        rsync) fill_with_rsync ;;
        http) fill_with_http ;;
        *) fail "Unknown TARANTINO_CACHE_SOURCE: $source (expected rsync or http)" ;;
    esac
    (( $(cached_count) >= target_books )) || fail "$workload_dir/book_ids.txt has fewer than $target_books downloadable books"
    echo "Cache ready in $cache_dir: $(cached_count) books ($(wc -l < "$skipped_file" | tr -d ' ') skipped)"
}

main "$@"
