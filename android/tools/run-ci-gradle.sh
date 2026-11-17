#!/usr/bin/env bash
set -euo pipefail

log=$(mktemp)
trap 'rm -f "$log"' EXIT

alternate=false
for delay in 10 30 0; do
  if NOUS_MAVEN_CENTRAL_ALTERNATE="$alternate" bash ./gradlew --no-daemon --console=plain "$@" 2>&1 | tee "$log"; then
    exit 0
  else
    status=$?
  fi

  if (( delay == 0 )) || ! grep -Eq "https://(repo\.maven\.apache\.org|repo1\.maven\.org)/maven2/.*Received status code 429" "$log"; then
    exit "$status"
  fi

  if [[ "$alternate" == false ]]; then
    alternate=true
  else
    alternate=false
  fi
  echo "::warning::Maven Central returned HTTP 429. Retrying with the other official endpoint in ${delay}s."
  sleep "$delay"
done
