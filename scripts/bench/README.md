# Benchmarks

Every command runs from the root of the repository.

## Corpus

`scripts/fill_cache.sh` copies the benchmark corpus, the first 2,800 downloadable books of
`workload/book_ids.txt`, from Gutenberg's official mirrors into `benchmarks/cache/`, and leaves it in
`benchmarks/mirror/` with the `<id>/pg<id>.txt` layout that `TARANTINO_MIRROR` expects. It can be interrupted
and run again; books that are missing or lack the Gutenberg markers are listed in `benchmarks/cache/skipped.txt`.

```bash
scripts/fill_cache.sh                              # with rsync
TARANTINO_CACHE_SOURCE=http scripts/fill_cache.sh  # one HTTP request per book, where port 873 is blocked
```

## Experiments

Each experiment of `SPEC.md` §9 is one script, run from a clean commit with `mvn -DskipTests package` done; each
writes its own `benchmarks/runs/<run_id>/`:

| Experiment | Command | Official run |
| --- | --- | --- |
| Ingestion throughput by crawler replicas and workers | `./scripts/bench/ingest-throughput.sh` | `20261105-215351-ingest-throughput-c1376c7` |
| Indexing throughput by batch size and indexer replicas, rebuilding the index once per trial | `./scripts/bench/index-throughput.sh` | `20261105-220915-index-throughput-c1376c7`, with the indexer's JMH of `20261103-204454-index-throughput-6925d4e` |
| Search latency by clients, HTTP threads and `query` replicas | `./scripts/bench/search-latency.sh` | `20261104-140900-search-latency-8e1c1e8`, reused |
| Coordination cost by replicas | `./scripts/bench/coordination-cost.sh` | The `hazelcast` trials of `20261106-012054-coordination-variants-c1376c7` |
| CPU and memory per service, of the whole stack and of the host | `./scripts/bench/resource-usage.sh` | `20261105-235758-resource-usage-c1376c7` |

[benchmarks/README.md](../../benchmarks/README.md) reads the results of these runs and of the recovery runs: the figures of each
configuration, the hypotheses they confirm or refute, and the bottlenecks they show.

The JMH benchmarks also run on their own, after `mvn -DskipTests install`, with `<service>` and its main class
`crawler` `org.ulpgc.tarantino.crawler.benchmarks.CrawlerBenchmarks`, `indexer` `org.ulpgc.tarantino.indexer.benchmarks.IndexerBenchmarks`
or `query` `org.ulpgc.tarantino.query.benchmarks.SearchBenchmark`, and an absolute run folder:

```bash
mvn -f services/<service>/pom.xml exec:exec -Dexec.executable=java -Dexec.classpathScope=test "-Dexec.args=-classpath %classpath <main class> <absolute run dir>"
```

To summarise an existing run again, delete its `summary.csv` and run its command on the run folder, then redraw its charts; a `search-latency` run keeps its per-trial rows and only needs `bench search-latency`, which appends the per-configuration ones (SPEC §9):

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar coordination benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar ingest-workers benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar resources benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar charts benchmarks/runs/<run_id>
```

## Coordination experiments and campaign

The scripts take the coordination mode from `TARANTINO_VARIANT`, `hazelcast` by default, and add its Compose files
themselves; [docs/running.md](../../docs/running.md#coordination-modes) has the files of each mode.

| Experiment | Command | Official run |
| --- | --- | --- |
| `publication-visibility`: listener against periodic refresh | `scripts/bench/publication-visibility.sh` | `20261106-002208-publication-visibility-c1376c7` |
| `broker-latency`: Hazelcast against ActiveMQ, in isolation | `scripts/bench/broker-latency.sh` | `20261106-123534-broker-latency-b4667d8` |
| `recovery-delay`: how lost work comes back, over recovery runs | `scripts/bench/recovery-delay.sh benchmarks/runs/<recovery run>...` | `20261106-073911-recovery-delay-c1376c7` |
| `coordination-variants`: every mode with 1, 2 and 3 replicas | `scripts/bench/coordination-variants.sh` | `20261106-012054-coordination-variants-c1376c7` |
| `campaign`: the experiments and the recovery matrix in every mode, in about 10 hours | `scripts/bench/all.sh --dry-run`, then `scripts/bench/all.sh` | `20261105-215348-campaign-c1376c7`, and `20261106-123526-campaign-b4667d8` for `broker-latency` |

The 165 recovery runs of the campaign end in `-c1376c7`, and `20261106-073908-recovery-summary-c1376c7` lists them.
