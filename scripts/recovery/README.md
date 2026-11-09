# Recovery

Every command runs from the root of the repository.

Each row of the failure matrix (`SPEC.md` §8) is `scripts/recovery/<nn>-<row>.sh <N>`, for example
`./scripts/recovery/04-crawler-after-dequeue.sh 2`, and `java -jar bench/target/bench-1.0-SNAPSHOT.jar recovery-compare 04`
checks that the runs of a row give the same books and `/search` answers.

The scripts take the coordination mode from `TARANTINO_VARIANT`, `hazelcast` by default:
`TARANTINO_VARIANT=polling scripts/recovery/01-crawler-after-claim.sh 2`. The 165 recovery runs of the campaign end in
`-c1376c7`, and `20261106-073908-recovery-summary-c1376c7` lists them;
[benchmarks/README.md](../../benchmarks/README.md) reads them.
