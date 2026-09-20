#!/usr/bin/env bash
#
# Cancella completamente l'indice Elasticsearch 'custom-documents' (quello su cui
# scrive l'ingest custom /api/es/documents). Al successivo avvio del servizio,
# EsVectorSearchConfiguration lo ricrea con il mapping corretto.
#
# Uso:
#   ./delete-index.sh
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX="${INDEX:-custom-documents}"

echo "> Cancello l'indice: $INDEX ($ES_URL) ..." >&2
curl -s -X DELETE "$ES_URL/$INDEX" | jq .

EXISTS=$(curl -s -o /dev/null -w '%{http_code}' "$ES_URL/$INDEX")
if [ "$EXISTS" = "404" ]; then
  echo "OK: indice '$INDEX' cancellato (o inesistente)" >&2
else
  echo "Avviso: l'indice '$INDEX' risulta ancora presente (HTTP $EXISTS)" >&2
fi