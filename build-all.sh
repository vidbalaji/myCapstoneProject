#!/usr/bin/env bash
# Builds (and by default tests) every service. Usage: ./build-all.sh [-DskipTests]
set -euo pipefail
cd "$(dirname "$0")"
mvn -B "$@" package
