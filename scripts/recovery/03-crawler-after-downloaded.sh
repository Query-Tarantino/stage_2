#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

run_recovery 1 recover_from_crash crawler after-downloaded
