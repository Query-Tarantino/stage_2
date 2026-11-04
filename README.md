# Query Tarantino · Stage 2

[![CI](https://github.com/Query-Tarantino/stage_2/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/Query-Tarantino/stage_2/actions/workflows/ci.yml?query=branch%3Adev)
[![CodeQL](https://github.com/Query-Tarantino/stage_2/actions/workflows/codeql.yml/badge.svg?branch=dev)](https://github.com/Query-Tarantino/stage_2/actions/workflows/codeql.yml?query=branch%3Adev)

Search engine over [Project Gutenberg](https://www.gutenberg.org/) books in Java 25, with Javalin and Jackson.
`crawler` downloads books, `indexer` indexes them in batches, `query` searches them with TF-IDF and `control`
seeds and reconciles. The services coordinate through Hazelcast, keep the texts in a datalake and the source
of truth in MongoDB, and everything runs on one machine with Docker Compose.

- [SPEC.md](SPEC.md) holds every contract: collections, messages, states, conditional writes, the HTTP API,
  the search algorithm, the failure matrix and the benchmark runs.
- [api/openapi.yaml](api/openapi.yaml) is the HTTP API in OpenAPI 3.1, derived from `SPEC.md` §6.

## Status

The system runs end to end: `control` seeds the candidates and reconciles, `crawler` downloads them, `indexer` publishes
them in batches and `query` answers the search API behind nginx. Every row of the failure matrix recovers with 1, 2 and
3 replicas, and the official runs of the five experiments, `ingest-throughput`, `index-throughput`, `search-latency`,
`coordination-cost` and `resource-usage`, are in `benchmarks/runs/` with the 41 recovery runs.

## Requirements

- Java 25 and Maven 3.9+
- Docker with Compose, for MongoDB and Hazelcast and for the Testcontainers tests

On macOS with [Colima](https://github.com/abiosoft/colima) instead of Docker Desktop, export first:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Build and test

```bash
mvn clean verify                              # every module, with the MongoDB tests on Testcontainers
mvn -pl services/indexer -am verify           # one module with what it needs
```

Without Docker, the Testcontainers tests are skipped, not failed. `mvn verify` also runs the cases of
`workload/conformance/` that still apply (`SPEC.md` §10).

GitHub Actions runs `mvn clean verify`, the functional checks against the stack with the 20 sample books and the
workflow and shell linters on every push, and fails if the MongoDB tests were skipped. CodeQL scans every pull request
to `dev` and `main`. Every row of the failure matrix runs with 1, 2 and 3 replicas on the benchmark corpus, as the
official runs do, on every pull request to `main`, and to `dev` when it changes `scripts/recovery/`; those runs are
test runs (`SPEC.md` §9), kept as workflow artifacts and never committed. The workflows are in `.github/workflows/`.

## Running

```bash
mvn -DskipTests package
docker compose -f docker-compose.yml -f docker-compose.mirror.yml up -d --build   # books from benchmarks/mirror
curl 'http://localhost:8080/search?q=pride%20prejudice'
curl 'http://localhost:8080/books/1342'
curl 'http://localhost:8080/search/filters'
curl http://localhost:8081/index/status                                           # generation, books indexed and rebuild in progress
curl http://localhost:8081/index/status/1342                                      # state of one book
curl -X POST http://localhost:8081/index/rebuild                                  # reindex every book into the next generation
docker compose down -v                                                             # stop and delete the data
```

Without `docker-compose.mirror.yml`, `crawler` downloads over HTTP from Gutenberg's official mirror. `control` seeds
`workload/sample_ids.txt`, and a book shows up in `/search` once its batch is published: at most
`TARANTINO_BATCH_TIMEOUT_SECONDS` (30 s) after the first download of the batch.

| Setting | How |
| --- | --- |
| Entry ports | Only nginx publishes ports: 8080 sends `/search`, `/search/filters` and `/books/` to `query`, and 8081 sends `/ingest/`, `/index/` and `/control/` to their service; any other path gives `404` |
| Service ports | `TARANTINO_PORT`: 7001 `crawler`, 7002 `indexer`, 7003 `query` and 7004 `control`, inside the Compose network, each with its `/health`: `docker compose exec query curl -s localhost:7003/health` |
| Addresses | `TARANTINO_MONGO_URI` and `TARANTINO_HAZELCAST`, which `docker-compose.yml` points to `mongo` and `hazelcast` |
| Replicas | `docker compose up -d --scale crawler=3 --scale indexer=3 --scale query=2`; `control` always has one. Downloads at once: `crawler` replicas × `TARANTINO_PARALLEL_DOWNLOADS` (8) |
| HTTP threads | `TARANTINO_HTTP_THREADS`; without it, Javalin's own pool, from 8 to 250 threads |
| Timing | The `TARANTINO_*_SECONDS` variables of `SPEC.md` §4.7, read from the shell by `docker-compose.yml` and the same for every service |
| JVM | `JAVA_TOOL_OPTIONS`, by default `-Xmx512m -XX:+ExitOnOutOfMemoryError` in every service: a JVM that runs out of memory exits instead of losing one thread |
| Download retries | `TARANTINO_MAX_ATTEMPTS` (3): `NETWORK_ERROR` and `STORAGE_ERROR` send the book back to `books-to-download` until then; `NOT_FOUND` and `MISSING_MARKERS` leave it `FAILED` |
| CORS | `TARANTINO_CORS_ORIGIN`, `*` by default, the origin `query` allows |
| Crash points | `CRAWLER_CRASH_AT` and `INDEXER_CRASH_AT` set `TARANTINO_CRASH_AT` of that service only, for the recovery tests (`SPEC.md` §8): `CRAWLER_CRASH_AT=after-claim docker compose up -d --scale crawler=1 crawler` |
| Tools | `docker compose run --rm bench <command>` runs `bench/target/bench-1.0-SNAPSHOT.jar`; it is under the `tools` profile, so `up` never starts it |

Colima's default VM has 2 GiB, which fits one replica of each service; give it more before scaling, for example
`colima start --memory 6`.


### Functional checks and benchmarks

Run the functional API checks (F4) against the running stack and validate every response against `api/openapi.yaml`:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar
```

Regenerate `checks/expected.json` when the corpus or schema changes:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar --generate-expected
```

Each experiment of `SPEC.md` §9 has its script. It needs `benchmarks/mirror/` filled (see Corpus) and
`mvn -DskipTests package`, checks the corpus first, and writes its run to `benchmarks/runs/<run_id>/`:

```bash
scripts/bench/ingest-throughput.sh   # books per second by crawler replicas and workers, after the crawler JMH
scripts/bench/index-throughput.sh    # books per second by batch size and indexer replicas, after the indexer JMH
scripts/bench/search-latency.sh      # /search latency and throughput by clients, HTTP threads and query replicas, after the query JMH
scripts/bench/coordination-cost.sh   # queue waits and time to publication by crawler and indexer replicas
scripts/bench/resource-usage.sh      # CPU and memory per service and for the whole stack, by replicas
```

`TARANTINO_REPETITIONS` (5) sets the repetitions of each configuration, which run interleaved. To run the JMH benchmarks
of one service alone, into the `jmh/` folder of a run, install the modules first; `exec:exec` runs from the module's
folder, so the run directory goes absolute:

```bash
mvn -DskipTests install
mvn -f services/indexer/pom.xml exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
    "-Dexec.args=-classpath %classpath org.ulpgc.tarantino.indexer.benchmarks.IndexerBenchmarks $PWD/benchmarks/runs/<run_id>"
```

The main classes of the other two are `org.ulpgc.tarantino.crawler.benchmarks.CrawlerBenchmarks` in `services/crawler`
and `org.ulpgc.tarantino.query.benchmarks.SearchBenchmark` in `services/query`.

Each row of the failure matrix (`SPEC.md` §8) has its script, which takes the replicas of `crawler`, `indexer` and
`query`, from 1 to 3 (2 or 3 for row 14), runs with the short timing of `scripts/recovery/recovery.env` and writes its
run with `checks.json`:

```bash
scripts/recovery/04-crawler-after-dequeue.sh 2
docker compose run --rm bench recovery-compare 04   # fails if the runs of row 04 differ in their books or /search answers
```

## Corpus

`scripts/fill_cache.sh` copies the benchmark corpus, the first 2,800 downloadable books of
`workload/book_ids.txt`, from Gutenberg's official mirrors into `benchmarks/cache/`, and leaves it in
`benchmarks/mirror/` with the `<id>/pg<id>.txt` layout that `TARANTINO_MIRROR` expects. It can be interrupted
and run again; books that are missing or lack the Gutenberg markers are listed in `benchmarks/cache/skipped.txt`.

```bash
scripts/fill_cache.sh                              # with rsync
TARANTINO_CACHE_SOURCE=http scripts/fill_cache.sh  # one HTTP request per book, where port 873 is blocked
```

## Repository

```text
common/                 shared contracts: names, records, JSON, configuration, tokenizer, Mongo, Hazelcast, HTTP
services/
  crawler/              Ingestion
  indexer/              Indexing
  query/                Search
  control/              seeding and reconciliation
bench/                  load generator, recovery checks and benchmark metrics
checks/                 functional API checks (F4) and OpenAPI validation
api/openapi.yaml        HTTP API
nginx/nginx.conf        entry ports 8080 and 8081
docker-compose.yml      the stack; docker-compose.mirror.yml adds the local mirror
Dockerfile              the image of the four services
scripts/fill_cache.sh   benchmark corpus
workload/
  book_ids.txt          corpus candidates, in order
  sample_ids.txt        20 books of the corpus, seeded by default
  queries.txt           search benchmark queries
  stopwords.txt         stopwords removed by the tokenizer
  conformance/          inherited rule cases, in JSON
```

The services follow the Stage 1 hexagonal layout, `model`, `ports`, `adapters` and `commands`, wired by each
`Main` and its factory (`SPEC.md` §3). `SPEC.md` §2.3 has the full layout and the owner of each path.
Benchmark and recovery runs go in `benchmarks/runs/<run_id>/`, which is versioned; `benchmarks/cache/`,
`benchmarks/mirror/` and `datalake/` are not.
