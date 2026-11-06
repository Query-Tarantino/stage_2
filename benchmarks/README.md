# Benchmark results

What the official runs of `SPEC.md` §9 show. Every figure comes from the `summary.csv` of the run named next to it,
unless the text names another file of that run: the mean ± the half-width of its 95 % CI over the 5 interleaved
repetitions of each configuration, and p50 or p95 where the tail matters. Each section gives the command that made the
run and where its raw data is, and checks the hypotheses that the script printed before measuring.

All runs used the same machine and corpus, as their `manifest.json` and `corpus.json` record: an Intel Core Ultra 9
275HX with 24 hardware threads (8 P cores and 16 E cores) and 31.4 GiB, Windows 11 with Docker Desktop 29.9.2 given
24 CPUs and 15.3 GiB, and the 2,800 books of the benchmark corpus (755,974 terms) from the local mirror. Each run is
from a clean `dev` commit, the one at the end of its `run_id`.

## The campaign

`20261105-215348-campaign-c1376c7`, from `scripts/bench/all.sh` on `c1376c7`, ran the experiments and the recovery
matrix in the five coordination modes (`SPEC.md` §11) in 9 h 45 min; its `runs.txt` lists each of the 187 steps, its
exit code and its run folders. 186 steps ended with 0. `broker-latency` failed because Git Bash rewrote the run folder
it passed to the container; once the script passed it as a relative path, `20261106-123526-campaign-b4667d8` ran that
step alone with `scripts/bench/all.sh --only broker-latency`, on a commit that only changed scripts in `scripts/bench/`.

The report cites only these runs and the two older runs that the campaign reuses, on the same machine, because the
code they measure did not change, which `git diff` shows:

| Measured with | Reused, or measured instead | Why |
| --- | --- | --- |
| `search-latency` | `20261104-140900-search-latency-8e1c1e8` | Its request path only had refactors since `8e1c1e8`, no dependency changed, and its JMH already met the protocol |
| The indexer's JMH | `jmh/` of `20261103-204454-index-throughput-6925d4e` | The tokenizer, the header parser and the batch writer did not change since `6925d4e`; the rebuild trials ran again |
| `coordination-cost` | The `hazelcast` trials of `coordination-variants` | They run the same procedure with the same N and repetitions |
| Rows 09–12 of the failure matrix | N = 1 only in the four modes other than `hazelcast` | Their recovery time is mostly processing the whole corpus, which `coordination-variants` measures for every N and mode |

Without those cuts the campaign took about 18 hours, more than one night on the one official machine. The other runs
of 2 to 4 November stay in `benchmarks/runs/` and are no longer cited.

One known flaw: the campaign brought the stack down without the `activemq` profile when it moved from mode
`activemq-persistent` to `polling`, so an idle ActiveMQ container stayed up during the recovery runs of `polling` and
`orchestrator`. No service of those modes connects to it; the scripts bring every mode down since `b4667d8`.

## Ingestion: books per second by `crawler` replicas and workers

`20261105-215351-ingest-throughput-c1376c7`, from `./scripts/bench/ingest-throughput.sh`; raw data in `ingest.csv`,
`stats.csv`, `host.csv` and `logs/<trial>/`.

| Replicas | Workers per replica | Whole window (books/s) | Steady 10–90 % (books/s) |
| --- | --- | --- | --- |
| 1 | 4 | 370 ± 51 | 393 ± 51 |
| 1 | 8 | 504 ± 135 | 561 ± 132 |
| 1 | 16 | 623 ± 27 | 671 ± 44 |
| 2 | 8 | 548 ± 73 | 603 ± 84 |
| 3 | 8 | 521 ± 17 | 572 ± 17 |

- Workers help with a shrinking gain: +36 % from 4 to 8 and +24 % from 8 to 16. H2 expected the curve to flatten
  towards 16; it bends but does not flatten, and 8 workers is the noisiest configuration, ±135 books/s.
- Replicas do not help, against H1: 2 and 3 replicas of 8 workers give the same books per second as 1, within their CIs.
- CPU is not what limits ingestion. In the resource-usage run the whole host uses 956–1,281 % of the 2,400 % available
  during ingestion, and adding crawlers raises their total CPU from 378 % to 537 % without raising the books per
  second. The limit is in the parts every crawler shares: the `books-to-download` queue in the single Hazelcast member,
  the one Mongo and the datalake volume. These runs do not tell which of the three.
- JMH, in the run's `jmh/crawler.csv`: splitting header and body takes 102 µs for book 1080 (40 KB), 1.6 ms for book 84
  (450 KB) and 12.3 ms for book 2600 (3.4 MB). At 504 books/s each of the 8 workers has 16 ms per book, so the text
  processing of a crawler is not the limit either.

## Indexing: books per second by batch size and `indexer` replicas

`20261105-220915-index-throughput-c1376c7`, from `./scripts/bench/index-throughput.sh --without-jmh`; raw data in
`index.csv`, `stats.csv`, `host.csv` and `logs/<trial>/`. Each trial rebuilds the index of the whole corpus.

| Books per batch | Replicas | Books/s |
| --- | --- | --- |
| 1 | 1 | 5.52 ± 0.05 |
| 10 | 1 | 12.5 ± 0.2 |
| 100 | 1 | 28.5 ± 0.9 |
| 250 | 1 | 33.3 ± 1.8 |
| 100 | 2 | 48.4 ± 3.6 |
| 100 | 3 | 59.8 ± 2.2 |

- Bigger batches pay off, then flatten, as H1 expected: ×2.3 from 1 to 10 books, ×2.3 from 10 to 100, but only +17 %
  from 100 to 250. A batch pays a fixed cost (its lease, publishing and marking) that more books share.
- Replicas help less than linearly, as H2 expected: ×1.70 with 2 and ×2.10 with 3.
- Batches of 250 fit in `-Xmx512m`, as H3 expected: no indexer stopped with `OutOfMemoryError`, and in `stats.csv` the
  indexer container peaks at 745–808 MiB per trial with batches of 250 against 587–778 MiB with batches of 100.
- Mongo is where the time goes. The indexer's JMH, reused from `jmh/indexer.csv` of
  `20261103-204454-index-throughput-6925d4e`, writes a batch of 100 books in 2.81 s, which alone caps one indexer at
  36 books/s, close to the 28.5 measured end to end, while counting the terms of the largest book takes 37 ms and
  parsing a header 7 µs. Writing 1,000 books takes 12.5 s, 80 books/s, so the cost per book of a batch keeps falling
  with its size. In the resource-usage run Mongo's CPU during indexing grows with the indexers, from 78 % with one to
  220 % with three, more than the indexers' own 64 % to 176 %.

## Search: latency of `/search` by clients, HTTP threads and `query` replicas

`20261104-140900-search-latency-8e1c1e8`, reused by the campaign, from `./scripts/bench/search-latency.sh`; raw data in
`load/<trial>.csv.gz`, one row per request, 18,496,487 in all. Each trial warms up for 30 s and measures for 120 s,
with the closed-loop clients cycling through `workload/queries.txt`. The p95 is the mean of each repetition's p95.

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

The `hazelcast` trials of `20261106-012054-coordination-variants-c1376c7`, from
`./scripts/bench/coordination-variants.sh`, with 1, 2 and 3 replicas of `crawler` and `indexer` together; raw data in
`coordination.csv`, one row per book and trial, and `logs/<trial>/`, and these figures in `variants.csv`.

| Replicas | Wait in `books-to-download` | Download | Wait in `books-to-index` | Indexing | Time to publication |
| --- | --- | --- | --- | --- | --- |
| 1 | 1,383 ± 327 ms | 12.6 ± 1.6 ms | 46.6 ± 1.9 s | 3.45 ± 0.13 s | 51.4 ± 1.9 s |
| 2 | 670 ± 115 ms | 27.0 ± 3.8 ms | 27.0 ± 2.7 s | 4.19 ± 0.41 s | 31.9 ± 3.1 s |
| 3 | 736 ± 127 ms | 44.4 ± 4.5 ms | 19.9 ± 0.7 s | 4.93 ± 0.21 s | 25.6 ± 0.9 s |

- Coordination costs far more than the work: with one replica a book waits 1.4 s for a crawler to download it in
  13 ms.
- The wait for a crawler halves with 2 replicas and does not fall further with 3, while the download itself gets
  slower with every replica, 13 → 27 → 44 ms.
- Indexing is the bottleneck of the whole pipeline: the wait in `books-to-index` is 91 % of the time to publication
  with one replica. It falls 42 % with 2 replicas and 57 % with 3. Books wait for an indexer, not for their batch to
  fill: with one replica the p95 of that wait is 87 s, far above the 30 s a batch waits at most to fill.

## Resources: CPU and memory per service, of the whole stack and of the host

`20261105-235758-resource-usage-c1376c7`, from `./scripts/bench/resource-usage.sh` with 1, 2 and 3 replicas of
`crawler` and `indexer` together; raw data in `stats.csv`, one `docker stats` row per container and refresh,
`host.csv` for the whole host and `logs/<trial>/` for the phase windows. CPU is in percent of one core, as
`docker stats` counts it.

| Replicas | Phase | Crawlers' CPU | Indexers' CPU | Mongo's CPU | Stack CPU | Stack memory | Host CPU |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Ingestion | 378 ± 94 % | – | 50 ± 9 % | 505 ± 105 % | 1,199 ± 122 MiB | 956 ± 85 % |
| 2 | Ingestion | 512 ± 74 % | – | 62 ± 11 % | 680 ± 109 % | 1,398 ± 125 MiB | 1,163 ± 155 % |
| 3 | Ingestion | 537 ± 53 % | – | 65 ± 4 % | 717 ± 32 % | 1,723 ± 73 MiB | 1,281 ± 71 % |
| 1 | Indexing | – | 64 ± 2 % | 78 ± 0.4 % | 147 ± 2 % | 2,264 ± 199 MiB | 208 ± 7 % |
| 2 | Indexing | – | 119 ± 10 % | 150 ± 3 % | 277 ± 7 % | 2,967 ± 148 MiB | 346 ± 12 % |
| 3 | Indexing | – | 176 ± 11 % | 220 ± 3 % | 406 ± 10 % | 3,930 ± 134 MiB | 494 ± 11 % |

- The crawlers' total CPU grows with replicas, as H1 expected, but each crawler uses less, 378 → 256 → 179 %, instead
  of staying flat: the same books per second (see Ingestion) are split among more crawlers, and the 24 hardware
  threads are never near saturation.
- During ingestion the crawlers use 7.6–8.3 times the CPU of Mongo, as H2 expected; during indexing Mongo uses more
  CPU than the indexers and grows with them, as H3 expected.
- H4 does not hold as written: the memory of a crawler or indexer container reaches 497–683 MiB at p99, above its
  `-Xmx512m` in all but one configuration, because `docker stats` counts the whole JVM (heap, metaspace, thread stacks and buffers), not the heap.
  `control` and `query` stay below 270 MiB.
- The host uses 1.7–1.9 times the stack's CPU during ingestion and 1.2–1.4 times during indexing: the rest runs outside
  the containers, in Docker Desktop's VM and Windows. Its used memory averages 19,432–21,213 MiB of
  31.4 GiB, of which the stack is 1.2–3.9 GiB.
- An ingestion window lasts about 5 s, about 10 `docker stats` refreshes per repetition, and the first refresh catches
  the stack still starting, which is why the stack CPU mean during ingestion is below its median (505 % against
  531 % with one replica).

## Publication visibility: listener against periodic refresh

`20261106-002208-publication-visibility-c1376c7`, from `./scripts/bench/publication-visibility.sh`, with 2 `query`
replicas; raw data in `visibility.csv`, one row per batch and replica, `coordination.csv` and `stats.csv`. Visibility
is the time from a batch's `WRITING`→`PUBLISHED` line to the `snapshot` line of each replica.

| `published-batches` listener | Mean (ms) | p50 (ms) | p95 (ms) | p99 (ms) |
| --- | --- | --- | --- | --- |
| On | 43.6 ± 7.3 | 37 | 54 | 145 |
| Off | 2,605 ± 251 | 2,672 | 4,880 | 4,965 |

- With the listener a batch is searchable in about 40 ms, under the 100 ms of H1.
- Without it the delays spread between 0 and the 5 s period, with a median of 2.7 s and a maximum of 4.97 s, as
  H2 expected.
- The listener costs no CPU that the run can see, as H3 expected: in `stats.csv` each `query` replica uses about 3 % of
  a core in both configurations, within their CIs. The time to publication is the same, 54–55 s, because what the
  listener saves is seconds against a pipeline of almost a minute.

## Broker latency: Hazelcast against ActiveMQ, in isolation

`20261106-123534-broker-latency-b4667d8`, from `./scripts/bench/broker-latency.sh`, rerun by the campaign as said
above; raw data in `broker.csv`, one row per trial of 30,000 messages, and `load/`. Latency is the mean of each
repetition's p50.

| Broker | Consumers | p50 latency (µs) | Throughput (messages/s) |
| --- | --- | --- | --- |
| Hazelcast | 1 | 102 ± 3 | 8,872 ± 583 |
| Hazelcast | 3 | 104 ± 2 | 8,513 ± 860 |
| Hazelcast | 8 | 105 ± 4 | 8,866 ± 385 |
| ActiveMQ, non-persistent | 1 | 139 ± 3 | 9,350 ± 45 |
| ActiveMQ, non-persistent | 3 | 137 ± 2 | 18,338 ± 2,440 |
| ActiveMQ, non-persistent | 8 | 138 ± 5 | 25,904 ± 3,830 |
| ActiveMQ, persistent | 1 | 180 ± 4 | 1,475 ± 35 |
| ActiveMQ, persistent | 3 | 184 ± 2 | 1,457 ± 11 |
| ActiveMQ, persistent | 8 | 185 ± 5 | 1,454 ± 18 |

- Every p50 is between 0.1 and 0.2 ms, as H1 expected.
- Persistence costs 1.3 times the non-persistent latency, not the 3 times of H2, but it caps the throughput at about
  1,460 messages/s whatever the consumers, because each send waits for its disk write.
- Non-persistent ActiveMQ scales with consumers, ×2.8 from 1 to 8, while Hazelcast stays at about 8,700 messages/s.
- A message is never what a book waits for, as H3 expected: the slowest p50, 185 µs, is about 7,700 times smaller than
  the 1,431 ms median wait in `books-to-download` with one replica in `coordination-variants`.

## Coordination modes: the same pipeline in each mode

`20261106-012054-coordination-variants-c1376c7`, from `./scripts/bench/coordination-variants.sh`: the whole corpus in
each of the five modes with 1, 2 and 3 replicas of `crawler` and `indexer` together, 5 interleaved repetitions each;
raw data in `coordination.csv`, `visibility.csv` and `stats.csv`, these figures in `variants.csv`, and in `pairs.csv`
the difference of every pair of modes with its 95 % CI and whether it differs. Coordination CPU is the broker's, or
`control`'s in `polling` and `orchestrator`.

| Mode | Books/s, N = 1 / 2 / 3 | Time to publication (s) | Visibility p50 (ms) | Coordination CPU | Mongo's CPU |
| --- | --- | --- | --- | --- | --- |
| `hazelcast` | 28.5 / 46.7 / 58.6 | 51.4 / 31.9 / 25.6 | 38 / 41 / 46 | 3.6 / 6.0 / 8.3 % | 80 / 152 / 224 % |
| `activemq` | 28.4 / 45.5 / 57.5 | 52.2 / 33.5 / 26.8 | 39 / 43 / 48 | 5.4 / 9.0 / 12.8 % | 80 / 153 / 219 % |
| `activemq-persistent` | 28.1 / 43.3 / 55.0 | 50.3 / 32.4 / 24.2 | 38 / 45 / 47 | 7.8 / 12.4 / 16.6 % | 79 / 152 / 219 % |
| `polling` | 27.5 / 47.9 / 55.5 | 54.5 / 32.1 / 28.2 | 2,321 / 2,472 / 2,656 | 3.6 / 5.8 / 7.0 % | 89 / 169 / 247 % |
| `orchestrator` | 27.7 / 44.7 / 34.6 | 55.9 / 36.2 / 31.9 | 176 / 208 / 173 | 10.8 / 15.9 / 12.7 % | 80 / 147 / 140 % |

- H0 holds for indexing: `indexing_ms` differs in no pair of modes without `polling`. It does not hold for
  downloads: `download_ms` differs in 22 of the 30 pairs, because in `activemq-persistent` and `orchestrator` the
  downloads take 8–15 ms at p50 with 2 and 3 replicas, against 21–45 ms in the other modes. In those two modes books
  reach the crawlers more slowly than the crawlers take them, through persistent sends or one `POST` at a time, so
  fewer downloads overlap: in `activemq-persistent` a book waits under 4 ms in `books-to-download` on average. Downloads
  take milliseconds against a time to publication of tens of seconds, so the comparison of the pipeline still holds.
- `activemq`, H1: throughput and time to publication within the CI of `hazelcast` at every N, as expected.
- `activemq-persistent`, H2: throughput within the CI of `activemq` at every N, with 1.3–1.4 times its broker CPU, as
  expected.
- `polling`, H1 to H3: throughput within the CI of `hazelcast` at every N; Mongo's CPU 10–12 % higher at every N,
  from the sorted claims and the idle polls; and visibility at p50 between 2.3 and 2.7 s, half the 5 s period, as
  expected.
- `orchestrator`, H1: throughput within the CI of `hazelcast` with 1 and 2 replicas, and 41 % lower with 3, which H1
  allowed because one thread sends one `POST` at a time; Mongo's CPU falls with it.
- `orchestrator`, H2 does not hold: the time to publication is 4.3–6.3 s above `hazelcast`, not two ticks. Books wait
  in Mongo until `control` dispatches them, inside its window: 4.2–5.6 s in `books-to-download` against 0.7–1.4 s
  in `hazelcast`.
- `orchestrator`, H3: visibility under 300 ms, 173–208 ms at p50, as expected.
- Coordination is cheap in every mode: the broker or `control` uses at most 17 % of one core, against Mongo's 80–247 %.

## Recovery: time back to correct answers after each failure

The 165 runs `2026110[56]-…-recovery-<nn>[-<mode>]-r<N>-c1376c7`, one per row of the failure matrix (`SPEC.md` §8),
mode and replica count where the row applies (`SPEC.md` §11.5): 41 in `hazelcast`, 33 in `activemq` and in
`activemq-persistent`, 26 in `polling` and 32 in `orchestrator`, from `./scripts/recovery/<nn>-<row>.sh <N>` with
`TARANTINO_VARIANT` set to the mode. `20261106-073908-recovery-summary-c1376c7` charts them all and lists them in
`sources.txt`. Each run has `checks.json` with the answers once recovered, and `recovery-compare` checks that the runs
of a row give the same books and `/search` answers whatever N and mode: it passed for all 14 rows. Recovery runs use
the short leases of `scripts/recovery/recovery.env` (6 s) and run once per row, mode and N, so they have no CI.

| Rows | Failure | Corpus | `hazelcast`, N = 1 / 2 / 3 (s) | Other modes (s) |
| --- | --- | --- | --- | --- |
| 01–04 | `crawler` crash points | sample | 8.6–18.8 | 7.2–22.8 |
| 05–08 | `indexer` crash points | sample | 6.3–17.7 | 6.6–16.9 |
| 09 | `crawler` paused longer than its lease | mirror | 373 / 276 / 225 | 363–373, N = 1 |
| 10 | `indexer` paused longer than its lease | mirror | 382 / 278 / 241 | 368–441, N = 1 |
| 11 | `control` killed while seeding | mirror | 367 / 255 / 253 | 365–373, N = 1 |
| 12 | Broker restarted | mirror | 374 / 261 / 235 | 372 and 384 in `activemq` and `activemq-persistent`, N = 1 |
| 13 | Mongo stopped for 10 s | sample | 16.6 / 15.9 / 14.8 | 14.7–22.6 |
| 14 | A `query` replica killed | sample | – / 0.9 / 0.7 | 0.2–1.2 |

- Every row recovers in every mode where it applies, with the same answers.
- The crash points of `crawler` and `indexer` recover within 23 s with the 20 sample books, whatever N and mode.
- Rows 09–12 use the whole mirror, so their time includes ingesting and indexing the 2,800 books, and it falls with N
  as indexing gets faster. With N = 1 every mode takes 363–384 s except `polling` in row 10, 441 s.
- Killing one of two or three `query` replicas costs at most 1.2 s of `/search` answers, because nginx sends the
  requests to the others.

## Recovery delay: how lost work comes back in each mode

`20261106-073911-recovery-delay-c1376c7`, from `./scripts/bench/recovery-delay.sh` over the 29 recovery runs of rows
04, 05 and 12, where a crash or a broker restart loses work after its message was taken; raw data in
`recovery-delay.csv`, one row per book. For each book, `requeue_delay` goes from the failure to its requeue or
dispatch again, and `reclaim_delay` to its next claim. Rows 04, 05 and 12 do not apply in `polling`.

| Mode | Rows 04 and 05: requeue p50 (s) | Rows 04 and 05: reclaim p50 (s) | Row 12: requeue p50 (s) | Row 12: reclaim p50 (s) |
| --- | --- | --- | --- | --- |
| `hazelcast` | 11.8 and 11.7 | 11.5 and 5.6 | 82.4 | 12.3 |
| `activemq` | 11.8 and 11.7 | 10.8 and 8.5 | 11.7 | 13.5 |
| `activemq-persistent` | No requeue | 3.8 and 5.0 | No requeue | 6.1 |
| `orchestrator` | 9.7 and 9.7 | 5.0 and 9.8 | – | – |

- H1 holds in rows 04 and 05, where `control` requeues 10.8–11.8 s after the failure in `hazelcast` and `activemq`,
  and in row 12 of `activemq`. It does not hold in row 12 of `hazelcast` with 2 and 3 replicas, whose median requeue
  is 97 and 82 s, against 12 s with one; these runs do not tell why.
- H2 holds in rows 04 and 05, where a requeued book is claimed again within 0.6 s at p50, and not in row 12, where it
  takes 1.8 s in `activemq` and 11.6 s in `hazelcast`.
- In `activemq-persistent` nothing is requeued: the broker redelivers what a dead consumer did not acknowledge, and
  the book is claimed again in 3.8–6.1 s at p50, as H3 expected, though the slowest books of rows 05 and 12 take 11 and
  19 s, above its 10 s.
- H4 does not hold by a little: the orchestrator dispatches again 9.50–10.05 s after the failure, not 10–10.4 s, because
  its 10 s count from the book's last dispatch, which comes before the crash.

## Where the bottlenecks are

1. Indexing limits the pipeline: one indexer publishes 28.5 books/s while one crawler downloads 504, so books wait
   20–47 s in `books-to-index`, and writing postings to the one Mongo is what an indexer spends its time on.
2. Ingestion stops growing at 500–670 books/s with 8 or more workers, whatever the replicas, with at most 53 % of the
   host's CPU in use: the shared queue, Mongo and datalake volume limit it.
3. Search saturates one `query` replica at about 4,400 requests/s; a second replica adds 29 % and a third nothing.
4. Coordination is not a bottleneck in any mode: a message takes 0.1–0.2 ms and the broker or `control` under 17 % of
   a core. The mode changes how soon a book is searchable, about 40 ms with a listener, 170–210 ms with the
   orchestrator and 2.3–2.7 s with polling, and the orchestrator alone loses throughput, 41 % with 3 replicas.

## What belongs to the single machine, and what Stage 3 changes

These runs measure one host: every replica shares its CPUs, its disk and the one MongoDB and broker, so a speedup here
says how the design uses one machine, not how it scales across several. What the runs show depends on these
single-host assumptions of `SPEC.md` §1:

| Measured limit | Single-host cause | What changes in a cluster |
| --- | --- | --- |
| Indexing: 28.5 books/s per indexer, ×2.1 with 3, while Mongo's CPU grows faster than the indexers' | Every indexer writes postings to one MongoDB server | Postings sharded by `term` across a sharded MongoDB, so indexers on several nodes write to different shards; the batch, its lease and its publication stay single-document writes |
| Ingestion: 500–670 books/s whatever the replicas, with the CPU far from full | One `books-to-download` queue on one Hazelcast member, one MongoDB and one datalake volume | A Hazelcast cluster with one queue per partition of `book_id`, and a datalake on storage every node mounts, or one per node with the book's node in `book_states` |
| Search: one `query` replica saturates at about 4,400 requests/s, and a third replica adds nothing to two | Replicas share nginx, the one MongoDB and the host's CPUs with the load generator | `query` replicas on several nodes behind a load balancer, reading a MongoDB replica set; the snapshot already lets each replica answer on its own |
| Recovery: a lost message waits `TARANTINO_REQUEUE_SECONDS` for `control` | `control` is one process, which a single host makes acceptable | `control` elected among several nodes, or the persistent ActiveMQ mode, whose broker redelivers without `control` in 3.8–6.1 s at p50 |
| Mode `orchestrator`: 41 % less throughput with 3 replicas | One `control` thread dispatches every book, one `POST` at a time | Several dispatchers, each with its own partition of the books, or a choreography mode |

The conditional writes of `SPEC.md` §5.1 already work across machines: claims, leases and expirations compare with
MongoDB's clock (`$$NOW`), never a host's, and no process keeps a lock another one needs. What a cluster replaces is
everything named after the host: Compose service names and nginx's resolver `127.0.0.11`, the local `datalake` volume,
the single Hazelcast member, ActiveMQ broker and MongoDB server, and, in mode `orchestrator`, the JVM's 30-second DNS
cache with which `control` finds the `query` replicas.
