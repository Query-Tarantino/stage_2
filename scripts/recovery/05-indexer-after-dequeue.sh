#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

skip_when_the_claim_is_the_dequeue
run_recovery 1 recover_from_crash indexer after-dequeue
