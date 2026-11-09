# Functional checks

Every command runs from the root of the repository.

Run the functional API checks (F4) against the running stack and validate every response against `api/openapi.yaml`:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar
```

In another coordination mode, give the checks the mode's Compose files, which they use to reach the service ports from
inside the containers, and the mode itself; in mode `orchestrator` they also check the internal routes of `SPEC.md`
§11.4 from inside the `control` container:

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.mirror.yml:docker-compose.orchestrator.yml TARANTINO_COORDINATION=orchestrator \
  java -jar checks/target/checks-1.0-SNAPSHOT.jar
```

Regenerate `checks/expected.json` when the corpus or schema changes:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar --generate-expected
```
