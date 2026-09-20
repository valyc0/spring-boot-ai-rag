#!/usr/bin/env bash
#
# Svuota (svuota) un indice Elasticsearch: cancella TUTTI i documenti
# mantenendo l'indice e il mapping (utile per il demo).
#
# Uso:
#   ./empty-index.sh
#   INDEX=altri-indici ./empty-index.sh
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX="${INDEX:-gemini-demo-index}"

echo "> Svuoto l'indice: $INDEX ($ES_URL) ..." >&2
curl -s "$ES_URL/$INDEX/_delete_by_query" \
  -H 'Content-Type: application/json' \
  -d '{"query": {"match_all": {}}}' | jq .

echo "> Conteggio rimanente:" >&2
curl -s "$ES_URL/$INDEX/_count?pretty" | jq ".count, \"index: $INDEX\""