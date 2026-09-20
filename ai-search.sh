#!/usr/bin/env bash
#
# Ricerca AI (RAG) sui documenti via API Java: ?q=<domanda> + JSON con
# eventuali filtri sui metadati ({"topK": <n>, "filters": {"<chiave>": "<valore>"}}).
#
# Uso:
#   ./ai-search.sh "rimborso benzina"
#   ./ai-search.sh "rimborso benzina" '{"filters": {"department": "operations"}}'
#   ./ai-search.sh "rimborso benzina" '{"topK": 2, "filters": {"langId": "it-IT"}}'
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
QUERY="${1:?Uso: $0 \"domanda\" ['{\"filters\": {...}}']}"
BODY="${2:-}"
if [ -z "$BODY" ]; then
    BODY='{}'
fi

echo "> POST $APP_URL/api/search/ai?q=$QUERY (body: $BODY)" >&2

ENCODED_Q="$(jq -rn --arg v "$QUERY" '$v | @uri')"

curl -sf -X POST "$APP_URL/api/search/ai?q=$ENCODED_Q" \
  -H "Content-Type: application/json" \
  -d "$BODY" | jq .

echo >&2
echo "> Verifica su Elasticsearch (indice gemini-demo-index) via search-es.sh:" >&2
./search-es.sh "$QUERY" 5 | jq '.hits.hits[] | {_id, contentId: ._source.metadata.contentId, content: ._source.content}' 2>/dev/null || true