#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose -f compose.yaml -f .devcontainer/compose.codespaces.yaml)
project_id="00000000-0000-0000-0000-000000000002"
api="http://localhost:8082/api/projects/${project_id}/monitors"

"${compose[@]}" up -d

for attempt in {1..60}; do
  if curl --fail --silent http://localhost:8082/actuator/health >/dev/null; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    "${compose[@]}" ps
    exit 1
  fi
  sleep 5
done

inventory="$(curl --fail --silent "${api}?includeArchived=true")"

create_monitor() {
  local name="$1"
  local payload="$2"
  if ! grep --fixed-strings --quiet "\"name\":\"${name}\"" <<<"$inventory"; then
    curl --fail --silent --show-error \
      --request POST \
      --header "Content-Type: application/json" \
      --data "$payload" \
      "$api" >/dev/null
  fi
}

create_monitor "Demo HTTP" '{"name":"Demo HTTP","type":"HTTP","targetUrl":"http://demo-target","frequencySeconds":15,"timeoutMs":5000,"expectedStatus":200}'
create_monitor "Demo TCP" '{"name":"Demo TCP","type":"TCP","host":"demo-target","port":80,"frequencySeconds":15,"timeoutMs":5000}'
create_monitor "Example DNS" '{"name":"Example DNS","type":"DNS","host":"example.com","dnsRecordType":"A","frequencySeconds":30,"timeoutMs":5000}'
create_monitor "Example TLS" '{"name":"Example TLS","type":"TLS","host":"example.com","port":443,"tlsExpiryWarningDays":30,"frequencySeconds":30,"timeoutMs":8000}'

printf '\nPulseOps demo is ready. Open the PORTS tab, then select port 3000.\n'
