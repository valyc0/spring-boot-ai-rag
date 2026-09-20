#!/usr/bin/env bash
# Ferma l'app Spring Boot (sia eseguita come jar che via mvn spring-boot:run).
set -uo pipefail

# Il pattern con [x] evita l'auto-match con la shell/script corrente.
patterns=(
    'gemini-rag-dem[o]'
    'com.example.aidemo.AiDemoApplicatio[n]'
)

pids=""
for p in "${patterns[@]}"; do
    pids+="$(pgrep -f "$p" || true) "
done
pids=$(echo "$pids" | tr ' ' '\n' | sort -u | grep -E '[0-9]+' || true)

if [ -z "$pids" ]; then
    echo "App non in esecuzione."
    exit 0
fi

echo "Fermo i processi: $pids ..."
kill $pids 2>/dev/null
sleep 2

for p in "${patterns[@]}"; do
    pkill -f "$p" 2>/dev/null || true
done

echo "App fermata."