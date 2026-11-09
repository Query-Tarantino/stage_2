# Running and configuration

Every command runs from the root of the repository.

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
| Entry ports | Only nginx publishes ports: 8080 sends `/search`, `/search/filters` and `/books/` to `query`, and 8081 sends `/ingest/`, `/index/` and `/control/` to their service; any other path gives `404`. 8081 has no authentication, so it only listens on `127.0.0.1` |
| Service ports | `TARANTINO_PORT`: 7001 `crawler`, 7002 `indexer`, 7003 `query` and 7004 `control`, inside the Compose network, each with its `/health`: `docker compose exec query curl -s localhost:7003/health` |
| Addresses | `TARANTINO_MONGO_URI` and `TARANTINO_HAZELCAST`, which `docker-compose.yml` points to `mongo` and `hazelcast` |
| Data paths | `TARANTINO_DATALAKE` (the `datalake` volume at `/data/datalake`), `TARANTINO_WORKLOAD` (`./workload` at `/app/workload`) and `TARANTINO_MIRROR` (`benchmarks/mirror` at `/data/mirror` with `docker-compose.mirror.yml`) |
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

Each service is a standalone Maven module that builds an executable jar, `services/<service>/target/<service>-1.0-SNAPSHOT.jar`,
so one can also run on its own outside Compose. Its defaults point to MongoDB and Hazelcast on `localhost`, which
Compose does not publish, so start them first:

```bash
docker run -d --name mongo -p 27017:27017 mongo:7.0
docker run -d --name hazelcast -p 5701:5701 -e HZ_CLUSTERNAME=tarantino hazelcast/hazelcast:5.7.0
TARANTINO_MIRROR=benchmarks/mirror java -jar services/crawler/target/crawler-1.0-SNAPSHOT.jar   # port 7001
docker rm -f mongo hazelcast                                                                    # afterwards
```

Run that way, a service writes its datalake to `datalake/` in the current folder, which git ignores.

## Coordination modes

`TARANTINO_COORDINATION` selects how the services coordinate (`SPEC.md` §11). With it unset the stack runs in
`hazelcast`, as above. Each other mode starts with its Compose override after the usual files:

| Mode | Start |
| --- | --- |
| `hazelcast` | `docker compose -f docker-compose.yml -f docker-compose.mirror.yml up -d --build` |
| `activemq` | `COMPOSE_PROFILES=activemq docker compose -f docker-compose.yml -f docker-compose.mirror.yml -f docker-compose.activemq.yml up -d --build` |
| `activemq-persistent` | `COMPOSE_PROFILES=activemq docker compose -f docker-compose.yml -f docker-compose.mirror.yml -f docker-compose.activemq.yml -f docker-compose.activemq-persistent.yml up -d --build` |
| `polling` | `docker compose -f docker-compose.yml -f docker-compose.mirror.yml -f docker-compose.polling.yml up -d --build` |
| `orchestrator` | `docker compose -f docker-compose.yml -f docker-compose.mirror.yml -f docker-compose.orchestrator.yml up -d --build` |

The benchmark and recovery scripts take the mode from `TARANTINO_VARIANT`, `hazelcast` by default, and add its files
themselves: `TARANTINO_VARIANT=polling scripts/recovery/01-crawler-after-claim.sh 2`.
