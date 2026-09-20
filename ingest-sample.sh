#!/usr/bin/env bash
#
# Inserisce un documento di esempio tramite l'API Java (/api/documents),
# che chunkizza ed embedda il testo scrivendolo su Elasticsearch.
#
# Uso:
#   ./ingest-sample.sh                   # documento di esempio
#   CONTENT_ID=altra-source ./ingest-sample.sh
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
CONTENT_ID="${CONTENT_ID:-policy-viaggi-2026}"
LANG_ID="${LANG_ID:-it-IT}"

echo "> POST $APP_URL/api/documents (contentId=$CONTENT_ID, langId=$LANG_ID) ..." >&2

curl -sf -X POST "$APP_URL/api/documents" \
  -H "Content-Type: application/json" \
  -d "$(jq -n \
    --arg contentId "$CONTENT_ID" \
    --arg langId "$LANG_ID" \
    '{
      contentId: $contentId,
      langId: $langId,
      metadata: { department: "operations", owner: "amministrazione", year: "2026" },
      text: "Policy aziendale sui viaggi di lavoro. La prenotazione di voli e hotel va effettuata
              tramite il portale viaggi almeno due settimane prima della partenza. Per i voli di
              durata inferiore a tre ore è prevista la classe economy; per tratte intercontinentali
              è consentita la business class. Gli alberghi devono rientrare nel budget di 140 euro
              a notte, salvo autorizzazione esplicita del direttore. Le spese di benzina e parcheggio
              sono rimborsate sulla base delle ricevute: per il chilometraggio è applicata la tariffa
              ACI del mese in corso. In caso di annullamento della trasferta, verificare le condizioni
              di cancellazione prima di prenotare e fondere eventuali penali nella nota spese. Tutti i
              giustificativi vanno allegati al sistema entro trenta giorni dal rientro."
    }')" | jq .