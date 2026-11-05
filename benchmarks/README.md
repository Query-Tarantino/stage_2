# Benchmark results

What the official runs of `SPEC.md` §9 show. Every figure comes from the `summary.csv` of the run named next to it:
the mean ± the half-width of its 95 % CI over the 5 interleaved repetitions of each configuration, and p50 or p95
where the tail matters. Each section gives the command that made the run and where its raw data is, and checks the
hypotheses that the script printed before measuring.

All runs used the same machine and corpus, as their `manifest.json` and `corpus.json` record: an Intel Core Ultra 9
275HX with 24 hardware threads and 31.4 GiB, Windows 11 with Docker Desktop 29.9.2 given 24 CPUs and 15.3 GiB, and the
2,800 books of the benchmark corpus (755,974 terms) from the local mirror. Each run is from a clean `dev` commit, the
one at the end of its `run_id`.

## Ingestion: books per second by `crawler` replicas and workers

`20261102-204917-ingest-throughput-77ed00a`, from `./scripts/bench/ingest-throughput.sh`; raw data in `ingest.csv`,
`stats.csv` and `logs/<trial>/`.

| Replicas | Workers per replica | Whole window (books/s) | Steady 10–90 % (books/s) |
| --- | --- | --- | --- |
| 1 | 4 | 403 ± 15 | 431 ± 17 |
| 1 | 8 | 554 ± 73 | 600 ± 88 |
| 1 | 16 | 610 ± 59 | 651 ± 69 |
| 2 | 8 | 557 ± 39 | 609 ± 44 |
| 3 | 8 | 526 ± 34 | 581 ± 40 |

- Workers help up to 8 and flatten after: +37 % from 4 to 8, +10 % from 8 to 16, as H2 expected.
- Replicas do not help, against H1: 2 and 3 replicas of 8 workers give the same books per second as 1, within their CIs.
- CPU is not what limits ingestion. In the resource-usage run the whole stack uses 480–750 % of the 2,400 %
  available, and adding crawlers raises their total CPU from 358 % to 563 % without raising the books per second. The
  limit is in the parts every crawler shares: the `books-to-download` queue in the single Hazelcast member, the one
  Mongo and the datalake volume. These runs do not tell which of the three.
- JMH, in the run's `jmh/crawler.csv`: splitting header and body takes 88 µs for book 1080 (40 KB), 1.6 ms for book 84
  (450 KB) and 12.5 ms for book 2600 (3.4 MB). At 554 books/s each of the 8 workers has 14 ms per book, so the text
  processing of a crawler is not the limit either.

## Indexing: books per second by batch size and `indexer` replicas

`20261103-204454-index-throughput-6925d4e`, from `./scripts/bench/index-throughput.sh`; raw data in `index.csv`,
`stats.csv` and `logs/<trial>/`. Each trial rebuilds the index of the whole corpus.

| Books per batch | Replicas | Books/s |
| --- | --- | --- |
| 1 | 1 | 5.46 ± 0.10 |
| 10 | 1 | 12.4 ± 0.4 |
| 100 | 1 | 28.7 ± 1.2 |
| 250 | 1 | 33.0 ± 1.1 |
| 100 | 2 | 47.4 ± 4.2 |
| 100 | 3 | 60.5 ± 3.7 |

- Bigger batches pay off, then flatten, as H1 expected: ×2.3 from 1 to 10 books, ×2.3 from 10 to 100, but only +15 %
  from 100 to 250. A batch pays a fixed cost (its lease, publishing and marking) that more books share.
- Replicas help less than linearly, as H2 expected: ×1.65 with 2 and ×2.11 with 3.
- Mongo is where the time goes. JMH in `jmh/indexer.csv` writes a batch of 100 books in 2.81 s, which alone caps one
  indexer at 36 books/s, close to the 28.7 measured end to end, while counting the terms of the largest book takes
  37 ms and parsing a header 7 µs. Writing 1,000 books takes 12.5 s, 80 books/s, so the cost per book of a batch keeps
  falling with its size. In the resource-usage run Mongo's CPU during indexing grows with the indexers, from 78 % with
  one to 221 % with three, more than the indexers' own 63 % to 178 %.

## Search: latency of `/search` by clients, HTTP threads and `query` replicas

`20261104-140900-search-latency-8e1c1e8`, from `./scripts/bench/search-latency.sh`; raw data in `load/<trial>.csv.gz`,
one row per request, 18,496,487 in all. Each trial warms up for 30 s and measures for 120 s, with the closed-loop
clients cycling through `workload/queries.txt`. The p95 is the mean of each repetition's p95.

| Clients | HTTP threads | Replicas | Mean latency (ms) | p95 (ms) | Requests/s |
| --- | --- | --- | --- | --- | --- |
| 1 | 32 | 1 | 1.54 ± 0.02 | 2.73 | 646 ± 8 |
| 8 | 32 | 1 | 2.20 ± 0.06 | 3.87 | 3,618 ± 99 |
| 32 | 32 | 1 | 7.29 ± 0.04 | 14.5 | 4,374 ± 26 |
| 64 | 32 | 1 | 14.5 ± 0.2 | 27.2 | 4,381 ± 50 |
| 32 | 8 | 1 | 12.9 ± 1.3 | 19.3 | 2,493 ± 254 |
| 32 | 128 | 1 | 7.56 ± 0.06 | 15.1 | 4,217 ± 36 |
| 32 | 32 | 2 | 5.66 ± 0.02 | 10.1 | 5,625 ± 24 |
| 32 | 32 | 3 | 5.82 ± 0.04 | 10.5 | 5,471 ± 39 |

- One replica saturates at about 4,400 requests/s from 32 clients, as H1 expected. From 32 to 64 clients the
  requests per second stay the same and the latency doubles, which is Little's law for a saturated server:
  64 clients / 4,381 requests/s = 14.6 ms.
- 8 HTTP threads are too few for 32 clients: they queue, and latency is 77 % higher than with 32 threads. 128
  threads add nothing and lose 4 % of the requests per second, as H2 expected.
- A second replica gives +29 % requests/s and −22 % latency; a third gives nothing more. H3 holds for 2 replicas only,
  so with 2 replicas the limit is no longer the `query` JVM but what the replicas share: nginx, Mongo and the load
  generator itself.
- The ranking is not the cost. JMH in `jmh/query.csv` ranks a three-word query over 1,000 books in 60 µs, about 1 %
  of the 5–7 ms each request takes end to end; the rest is reading postings and metadata from Mongo, HTTP and JSON.
- Answers stay correct as configurations change: every request was answered `200` except 170 `503`, 168 of them with
  64 clients, 0.006 % of that configuration's 2,628,660 requests. CI also runs the functional checks against one and
  against three `query` replicas.

## Coordination cost: time a book spends in each queue

`20261102-212416-coordination-cost-77ed00a`, from `./scripts/bench/coordination-cost.sh` with 1, 2 and 3 replicas of
`crawler` and `indexer` together; raw data in `coordination.csv`, one row per book and trial, and `logs/<trial>/`.

| Replicas | Wait in `books-to-download` | Download | Wait in `books-to-index` | Indexing | Time to publication |
| --- | --- | --- | --- | --- | --- |
| 1 | 1,436 ± 380 ms | 13.1 ± 1.9 ms | 46.7 ± 1.2 s | 3.49 ± 0.08 s | 51.7 ± 1.0 s |
| 2 | 794 ± 68 ms | 27.4 ± 1.1 ms | 26.0 ± 1.4 s | 4.12 ± 0.21 s | 31.0 ± 1.6 s |
| 3 | 836 ± 89 ms | 45.1 ± 2.7 ms | 19.7 ± 1.1 s | 5.08 ± 0.29 s | 25.6 ± 1.4 s |

- Coordination costs far more than the work: with one replica a book waits 1.5 s for a crawler to download it in
  13 ms, as H1 expected.
- The wait for a crawler halves with 2 replicas and does not fall further with 3, while the download itself gets
  slower with every replica, 13 → 27 → 45 ms, as H2 expected.
- Indexing is the bottleneck of the whole pipeline: the wait in `books-to-index` is 90 % of the time to publication
  with one replica. It falls 44 % with 2 replicas and 58 % with 3, while the wait for a crawler falls 45 % and then
  42 %, so it falls as much or more, against H3. Books wait for an indexer, not for their batch to fill: with one
  replica the p95 of that wait is 88 s, far above the 30 s a batch waits at most to fill.

## Resources: CPU and memory per service and of the whole stack

`20261102-215134-resource-usage-77ed00a`, from `./scripts/bench/resource-usage.sh` with 1, 2 and 3 replicas of
`crawler` and `indexer` together; raw data in `stats.csv`, one `docker stats` row per container and refresh, and
`logs/<trial>/` for the phase windows. CPU is in percent of one core, as `docker stats` counts it.

| Replicas | Phase | Crawlers' CPU | Indexers' CPU | Mongo's CPU | Stack CPU | Stack memory |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Ingestion | 358 ± 42 % | – | 47 ± 12 % | 480 ± 91 % | 1,190 ± 95 MiB |
| 2 | Ingestion | 505 ± 35 % | – | 63 ± 5 % | 689 ± 20 % | 1,484 ± 103 MiB |
| 3 | Ingestion | 563 ± 48 % | – | 64 ± 6 % | 749 ± 41 % | 1,766 ± 106 MiB |
| 1 | Indexing | – | 63 ± 1 % | 78 ± 1 % | 146 ± 1 % | 2,324 ± 132 MiB |
| 2 | Indexing | – | 116 ± 8 % | 151 ± 4 % | 275 ± 5 % | 3,019 ± 191 MiB |
| 3 | Indexing | – | 178 ± 3 % | 221 ± 2 % | 409 ± 4 % | 3,822 ± 221 MiB |

- The crawlers' total CPU grows with replicas, as H1 expected, but each crawler uses less, 358 → 252 → 188 %, instead
  of staying flat: the same books per second (see Ingestion) are split among more crawlers, and the 24 hardware
  threads are never near saturation.
- During ingestion the crawlers use 7.6–8.8 times the CPU of Mongo, as H2 expected; during indexing Mongo uses more
  CPU than the indexers and grows with them, as H3 expected.
- H4 does not hold as written: the memory of a crawler or indexer container reaches 634–653 MiB at p99, above its
  `-Xmx512m`, because `docker stats` counts the whole JVM (heap, metaspace, thread stacks and buffers), not the heap.
  `control` and `query` stay below 260 MiB.
- An ingestion window lasts about 5 s, about 10 `docker stats` refreshes per repetition, and the first refresh catches
  the stack still starting, which is why the stack CPU mean during ingestion is below its median (480 % against
  550 % with one replica). The indexing windows last 97, 64 and 48 s with 1, 2 and 3 replicas.
- This run has no CPU or memory of the whole host. `bench stats` records `host.csv` from SPEC 1.9.2 on, so the next
  resource-usage run adds `host_cpu_total` and `host_memory_used`.

## Recovery: time back to correct answers after each failure

The 41 runs `20261103-…-recovery-<nn>-r<N>-…`, one per row of the failure matrix (`SPEC.md` §8) and replica count,
from `./scripts/recovery/<nn>-<row>.sh <N>`; `20261103-184101-recovery-summary-34cac8a` charts them all and lists
them in `sources.txt`. Each run has `checks.json` with the answers once recovered, and `recovery-compare` checks that
the runs of a row give the same books and `/search` answers whatever N. Recovery runs use the short leases of
`scripts/recovery/recovery.env` (6 s) and run once per row and N, so they have no CI.

| Rows | Failure | Corpus | Seconds with N = 1 / 2 / 3 |
| --- | --- | --- | --- |
| 01–04 | `crawler` crash points | sample | 8.4–19.7 |
| 05–08 | `indexer` crash points | sample | 6.0–13.5 |
| 09 | `crawler` paused longer than its lease | mirror | 372 / 273 / 239 |
| 10 | `indexer` paused longer than its lease | mirror | 429 / 313 / 244 |
| 11 | `control` killed while seeding | mirror | 410 / 276 / 236 |
| 12 | Hazelcast restarted | mirror | 381 / 304 / 244 |
| 13 | Mongo stopped for 10 s | sample | 17.6 / 17.7 / 16.1 |
| 14 | A `query` replica killed | sample | – / 0.46 / 0.90 |

- The crash points of `crawler` and `indexer` recover within 20 s with the 20 sample books, whatever N.
- Rows 09–12 use the whole mirror, so their time includes ingesting and indexing the 2,800 books, and it falls with N
  as indexing gets faster.
- Killing one of two or three `query` replicas costs under a second of `/search` answers, because nginx sends the
  requests to the others.

## Where the bottlenecks are

1. Indexing limits the pipeline: one indexer publishes 29 books/s while one crawler downloads 554, so books wait
   20–47 s in `books-to-index`, and writing postings to the one Mongo is what an indexer spends its time on.
2. Ingestion stops growing at about 600 books/s whatever the replicas, with the CPU far from full: the shared queue,
   Mongo and datalake volume limit it.
3. Search saturates one `query` replica at about 4,400 requests/s; a second replica adds 29 % and a third nothing.
