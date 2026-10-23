# Benchmarks

How to run the data structure benchmarks of Stage 1 and build the comparison report, step by step. What each
structure is and why it is compared is in the [README](README.md#data-structure-comparisons); how every metric
is measured, and the rules that keep results comparable across languages, are in
[SPEC.md](SPEC.md#11-benchmarks).

Every command runs from the **project root**.

## Quick reference

```bash
scripts/fill_cache.sh                                     # 1. dataset, once
mvn -q install -DskipTests                                # 2. build
TARANTINO_BENCHMARK_QUICK=true TARANTINO_BENCHMARK_BOOKS=20 \
    mvn verify -Pbenchmark -DskipTests                    # 3. check the setup in a few minutes
caffeinate -i mvn verify -Pbenchmark -DskipTests          # 4. full run (Linux: systemd-inhibit --what=idle:sleep)
python3 scripts/compare_results.py                        # 5. comparison report
```

The report is `benchmarks/report/comparison.md`.

## 1. Prepare the machine

- Java 25, Maven 3.9+ and Python 3.10+, as in the [README](README.md#requirements).
- **MongoDB 7.0** running on the machine itself, not inside a virtual machine, with its cache fixed at 1 GB
  (SPEC §11). The [README](README.md#requirements) shows how to install it and set the cache on Linux and
  macOS. Stop the development container first (`docker compose down`) if it is not the one you set up, since
  both use port 27017. To run without MongoDB, set `TARANTINO_BENCHMARK_SKIP_MONGO=true` (see
  [Variables](#variables)).
- **matplotlib**, optionally, to add charts to the report: `python3 -m pip install matplotlib`.

## 2. Fill the dataset cache

Benchmarks read the books from a local cache so the network is never measured. Fill it once; it can be
interrupted and resumes where it stopped:

```bash
scripts/fill_cache.sh
```

It follows `workload/book_ids.txt` until `benchmarks/cache/` holds 2 800 books with Gutenberg markers (ids
missing or without them are listed in `benchmarks/cache/skipped.txt`). By default it copies the candidates
from `rsync.ibiblio.org::gutenberg-epub` in one rsync transfer, into `benchmarks/mirror/`, and moves the valid
ones to the cache. Where the rsync port (873) is blocked, download them over HTTP from the official mirror
instead:

```bash
TARANTINO_CACHE_SOURCE=http scripts/fill_cache.sh
```

| Variable                   | Effect                                                              |
|----------------------------|---------------------------------------------------------------------|
| `TARANTINO_CACHE_BOOKS`    | Books to cache (default 2800)                                       |
| `TARANTINO_CACHE_SOURCE`   | `rsync` (default) or `http`                                         |
| `TARANTINO_RSYNC_SOURCE`   | rsync module (default `rsync.ibiblio.org::gutenberg-epub`)          |
| `TARANTINO_CACHE_PARALLEL` | Simultaneous HTTP downloads (default 16)                            |

The dataset is about 1.3 GB (2 800 books of 470 KB on average). Over HTTP it downloads in about 4 minutes
with 16 parallel downloads (measured: about 12 books per second); rsync copies it in one transfer, limited
only by the bandwidth.

## 3. Build

```bash
mvn -q install -DskipTests
```

This builds the services and the test-jars with the benchmark support code that the indexer and query
benchmarks share. Run it again after changing any code, but never while a benchmark is running: it replaces
the jars the run is using.

## 4. Check the setup with a quick run

Before a full run, check that the dataset, MongoDB and every benchmark work. A quick run takes 1 process, 1
warm-up and 1 measured iteration per configuration, so its numbers are not meaningful, only its errors:

```bash
TARANTINO_BENCHMARK_QUICK=true TARANTINO_BENCHMARK_BOOKS=20 mvn verify -Pbenchmark -DskipTests
```

It overwrites the results of an earlier run in `benchmarks/results/`; keep a copy of them if you need them.

## 5. Run the benchmarks

```bash
mvn verify -Pbenchmark -DskipTests                         # every service: crawler, indexer, query
mvn verify -Pbenchmark -DskipTests -pl services/indexer    # a single service
```

Keep the machine as SPEC §11 requires: plugged in, out of any power-saving mode, with nothing else running,
and awake for the whole run. Wrap the command so the system does not sleep until it ends:

```bash
systemd-inhibit --what=idle:sleep mvn verify -Pbenchmark -DskipTests   # Linux
caffeinate -i mvn verify -Pbenchmark -DskipTests                       # macOS
```

Laptops, above all fanless ones, lower their clock speed under sustained load, so a cool machine on a hard
surface gives steadier results. The scratch files go under `benchmarks/tmp.noindex/`, which Spotlight skips
on macOS; on a Linux desktop with KDE Baloo, which indexes the whole home directory, exclude `benchmarks/` in
its settings.

Each service runs its benchmarks in **two passes** of one process per configuration (SPEC §11); the second
runs every parameter in reverse order, so whatever drifts during a run, such as the temperature, weighs alike
on every structure and size. A service writes its results file only when both passes end: an interrupted
service writes nothing, and running it again, alone with `-pl`, replaces only its own results.

A full run with 100, 300 and 1 000 books took **about 2 hours 10 minutes** on a fanless Apple M4 laptop with
an SSD (measured): 7 minutes for the crawler, 1 hour 52 minutes for the indexer and 10 minutes for the query
service. Most of the time goes to building and updating the `folders` index, one file per term, so it depends
mostly on the disk. On that laptop the indexer's second pass took twice as long as the first (75 against 37
minutes), the slowdown under sustained load described above.

| Comparison               | Structures                 | Metrics                                                                  | Benchmarks                                                          |
|--------------------------|----------------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------|
| Datalake (PDF 3.1)       | `time`, `book`, `batch`    | write throughput, lookup, new books detection, recovery and leftover files, files, disk | crawler `DatalakeWriteBenchmark`, `DatalakeLookupBenchmark`, `NewBooksDetectionBenchmark`, `RecoveryScenario` |
| Inverted index (PDF 4.2) | `json`, `folders`, `mongo` | full build, incremental update book by book and in batch, index open, query, build and index memory, allocations, terms, disk | indexer `FullIndexBuildBenchmark`, `IncrementalUpdateBenchmark`, query `IndexOpenBenchmark`, `QueryTimeBenchmark` |
| Metadata (PDF 4.1)       | `sqlite`, `mongo`          | bulk insertion, book by id, books by author                              | indexer `MetadataInsertionBenchmark`, query `MetadataQueryBenchmark` |

### Variables

| Variable                         | Effect                                                        |
|----------------------------------|---------------------------------------------------------------|
| `TARANTINO_BENCHMARK_BOOKS`      | Sizes to run, e.g. `100,300` (default `100,300,1000`)          |
| `TARANTINO_BENCHMARK_QUICK`      | `true`: 1 pass of 1 process, 1 warm-up and 1 measured iteration, to check the setup |
| `TARANTINO_BENCHMARK_SKIP_MONGO` | `true`: skip the `mongo` index and metadata structures        |
| `TARANTINO_MONGO_URI`            | MongoDB server (default `mongodb://localhost:27017`)          |
| `TARANTINO_BENCHMARKS`           | Directory of the cache, results and report (default `benchmarks`) |
| `TARANTINO_WORKLOAD`             | Directory of the workload files (default `workload`)          |

## 6. Build the comparison report

```bash
python3 scripts/compare_results.py
```

It prints `Comparison written to .../benchmarks/report/comparison.md`. It works from any directory, since it
resolves paths from the project root, and follows `TARANTINO_BENCHMARKS` like the benchmarks. It reads every
CSV in `benchmarks/results/` and writes, for each comparison (datalake, inverted index, metadata):

- **Best structure**: the winner of each metric at the largest size, or `tie: ...` when the confidence
  intervals of several structures overlap with the best one.
- **One table per metric**: structures against sizes, as the mean ± the half-width of its 95% confidence
  interval, with the best value and every value tied with it in **bold**.
- **One chart per metric**, `benchmarks/report/<comparison>-<metric>.png`, only if matplotlib is installed;
  without it the report has the same tables and no charts.

If `benchmarks/results/` holds no CSV, it stops with `No benchmark results found; run the benchmarks first`.

**Comparing languages.** Copy the `python-*.csv` and `cpp-*.csv` results of the other implementations, run
on the same machine, into `benchmarks/results/` and run the script again: every table then has a row per
language and structure, and the winners are chosen per language. Python has no way to count every
allocation, so `memory_allocated` has no Python rows (SPEC §11).

The analysis of the Java results is in [ANALYSIS.md](ANALYSIS.md).

## Output files

Everything is under `benchmarks/`, which is not versioned:

| Path                                         | Content                                                        |
|----------------------------------------------|----------------------------------------------------------------|
| `cache/<id>.txt`, `cache/skipped.txt`        | The dataset, and the candidate ids left out of it              |
| `mirror/`                                    | rsync copy of the candidates, before they move to the cache    |
| `results/java-<service>.csv`                 | Results shared with the other languages (SPEC §11 format)      |
| `<service>/jmh-results-pass-<1\|2>.csv`      | Raw JMH output of each pass, to compare pass 1 against pass 2  |
| `report/comparison.md`, `report/*.png`       | The comparison report and its charts                           |
| `tmp.noindex/`                               | Scratch datalakes and indexes, deleted and rebuilt by each run |

The results files have one row per structure, metric and size:

```
language,structure,metric,n_books,value,error,unit
java,json,full_build_time,100,1795.838,683.268,ms
```

`error` is the half-width of the 95% confidence interval, `0` for exact values such as file counts.

## Notes for comparing languages

The Java tokenizer scans code points with `Character.isLetter` instead of matching `\p{L}+` with a regular
expression. It finds exactly the same terms (`TokenizerEquivalenceTest` checks it against the regular
expression on thousands of texts, and the SPEC conformance cases pass), but it is about 1.6 times faster and
allocates half the memory (200 books: 1.15 s and 1.2 GB instead of 1.9 s and 2.6 GB), which shortens Java's
build and update times and lowers its `memory_allocated`.
