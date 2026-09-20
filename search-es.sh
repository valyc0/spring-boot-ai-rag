#!/usr/bin/env bash
#
# Query su un indice Elasticsearch: restituisce l'INTERO JSON di risposta,
# formattato, escludendo il campo embedding.
#
# Uso:
#   ./search-es.sh [query] [size]
#
# Esempi:
#   ./search-es.sh                    # tutti i documenti dell'indice
#   ./search-es.sh ferie 20           # due termini con max 20 risultati
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX="${INDEX:-gemini-demo-index}"
QUERY="${1:-}"
SIZE="${2:-100}"

PAYLOAD=$(jq -n \
  --arg q "$QUERY" \
  --argjson size "$SIZE" \
  '{
     size: $size,
     _source: ["id", "contentId", "langId", "metadata", "content"],
     query: (if $q == "" then { match_all: {} } else { match: { content: $q } } end)
   }')

curl -s "$ES_URL/$INDEX/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD" | jq .

echo "(indice: $INDEX, query: \"$QUERY\", size: $SIZE)" >&2