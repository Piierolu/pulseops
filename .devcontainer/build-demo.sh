#!/usr/bin/env bash
set -euo pipefail

export COMPOSE_PARALLEL_LIMIT=1
compose=(docker compose -f compose.yaml -f .devcontainer/compose.codespaces.yaml)

# Sequential builds keep the demo within a standard Codespaces memory budget.
"${compose[@]}" build control-plane
"${compose[@]}" build agent
"${compose[@]}" build dashboard
