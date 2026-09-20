#!/usr/bin/env bash
#
# Ricrea l'indice gemini-demo-index con mapping personalizzato:
# langId e contentId copiati anche a livello top del _source ("copy_to").
#
# Nota: deve esserci spring.ai.vectorstore.elasticsearch.initialize-schema=false
# in application.properties, altrimenti l'app sovrascrive il mapping all'avvio.
#
# Uso:
#   ./create-index.sh
#   INDEX=altri-indici ./create-index.sh
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX="${INDEX:-gemini-demo-index}"
PIPELINE="${PIPELINE:-copy-meta}"

[ -f src/main/java/com/example/aidemo/AiDemoApplication.java ] || {
    echo "Esegui dalla root del progetto (o cd da /home/valerio/lavoro/appo/elastic-ai)" >&2
    exit 1
}

MAPPING="$(jq -n '
  {
    properties: {
      id: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
      content: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
      embedding: {
        type: "dense_vector", dims: 768, index: true, similarity: "cosine",
        index_options: { type: "bbq_hnsw", m: 16, ef_construction: 100,
          rescore_vector: { oversample: 3.0 } }
      },
      langId: { type: "keyword" },
      contentId: { type: "keyword" },
      metadata: {
        properties: {
          charset: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
          chunkIndex: { type: "long" },
          chunk_index: { type: "long" },
          contentId: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } },
                       copy_to: "contentId" },
          department: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
          langId: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } },
                    copy_to: "langId" },
          owner: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
          parent_document_id: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
          source: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } },
          totalChunks: { type: "long" },
          total_chunks: { type: "long" },
          year: { type: "text", fields: { keyword: { type: "keyword", ignore_above: 256 } } }
        }
      }
    }
  }')"

echo "> Elimino l'indice esistente: $INDEX ..." >&2
curl -s -X DELETE "$ES_URL/$INDEX?ignore_unavailable=true" > /dev/null

echo "> Creo l'indice: $INDEX ..." >&2
curl -s -X PUT "$ES_URL/$INDEX" \
  -H "Content-Type: application/json" \
  -d "$(jq -nc --argjson m "$MAPPING" '{mappings: $m}')" > /dev/null

echo "> Creo/o aggiorno la pipeline: $PIPELINE (sposta langId/conteId a top-level nel _source) ..." >&2
curl -s -X PUT "$ES_URL/_ingest/pipeline/$PIPELINE" \
  -H "Content-Type: application/json" \
  -d '{
    "processors": [
      { "set": { "field": "langId", "value": "{{metadata.langId}}", "if": "ctx.metadata != null && ctx.metadata.langId != null" } },
      { "set": { "field": "contentId", "value": "{{metadata.contentId}}", "if": "ctx.metadata != null && ctx.metadata.contentId != null" } }
    ]
  }' > /dev/null

echo "> Attivo la default_pipeline sull'indice ..." >&2
curl -s -X PUT "$ES_URL/$INDEX/_settings" \
  -H "Content-Type: application/json" \
  -d "$(jq -nc --arg p "$PIPELINE" '{index: {default_pipeline: $p}}')" > /dev/null

echo "> Mapping + default_pipeline applicati:" >&2
curl -s "$ES_URL/$INDEX/_settings" | jq -c '.[].settings.index | {default_pipeline}'
curl -s "$ES_URL/$INDEX/_mapping?pretty" | jq '.[] .mappings .properties | {langId, contentId, metadata_langId: .metadata.properties.langId}'