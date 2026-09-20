#!/usr/bin/env bash
#
# SEMPLICE COPIA/COLLA PER FARE UNA DOMANDA (CON O SENZA FILTRI)
#

# 1) Domanda senza filtri
curl -s -X POST "http://localhost:8080/api/search/ai" \
  --url-query "q=come vengono rimborsate le spese di benzina in trasferta" \
  -H "Content-Type: application/json" \
  -d "{}"

# 2) Domanda con filtri sui metadati
curl -s -X POST "http://localhost:8080/api/search/ai" \
  --url-query "q=come vengono rimborsate le spese di benzina in trasferta" \
  -H "Content-Type: application/json" \
  -d '{"filters":{"department":"operations"}}'

# 3) Domanda con filtri e topK
curl -s -X POST "http://localhost:8080/api/search/ai" \
  --url-query "q=come vengono rimborsate le spese di benzina in trasferta" \
  -H "Content-Type: application/json" \
  -d '{"topK": 2, "filters":{"department":"operations","langId":"it-IT"}}'