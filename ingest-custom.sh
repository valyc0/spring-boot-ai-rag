#!/usr/bin/env bash
#
# Inserisce un documento tramite l'API custom (/api/es/documents),
# che chunkizza ed embedda il testo su Elasticsearch (maxTokens=200, overlap=32).
# Il testo è abbastanza lungo da generare almeno 3 chunk.
#
# Uso:
#   ./ingest-custom.sh
#   ./ingest-custom.sh my-content-id
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
CONTENT_ID="${CONTENT_ID:-${1:-manual-operations-2026}}"
LANG_ID="${LANG_ID:-it-IT}"
DEPARTMENT="${DEPARTMENT:-operations}"

TEXT="La manutenzione ordinaria degli impianti produttivi deve essere pianificata e registrata
sul sistema gestionale. Ogni intervento viene identificato da un numero progressivo e deve
riportare la macchina, l'operatore, la data di esecuzione e l'esito della verifica. Prima di
avviare qualsiasi attività, l'operatore è tenuto a consultare la scheda tecnica dell'impianto
e a controllare che la macchina sia in sicurezza, scollegata dall'alimentazione e bloccata
con lucchetto personale. L'uso dei dispositivi di protezione individuale è obbligatorio: guanti
antitaglio, occhiali di protezione e calzature antinfortunistiche. Dopo la conclusione della
manutenzione, vanno compilati i moduli di controllo qualità e va segnalato qualsiasi scostamento
rispetto agli standard previsti. Eventuali ricambi non a magazzino vanno richiesti con apposita
nota di acquisto allegando la foto del componente da sostituire. La gestione dei pezzi di ricambio
segue la logica del magazzino a scorta minima: quando il livello scende sotto la soglia, il sistema
genera automaticamente una richiesta di riordino. Gli ordini di acquisto creati in automatico
devono essere validati dal responsabile di reparto entro ventiquattro ore. Il venditore deve
confermare la data di consegna stimata e il prezzo deve corrispondere a quello contrattuale
nella lista fornitori. Eventuali variazioni di prezzo sono accettate solo se documentate e
approvate dalla direzione acquisti. Nel caso di fermi macchina prolungati si attiva una procedura
speciale di massima urgenza che coinvolge il team di manutenzione centrale, disponibile anche
fuori dall'orario regolare. Le segnalazioni vengono inoltrate tramite il portale interno e
ricevono un numero di ticket con priorità alta. Il team centrale deve controllare i log del
macchinario e valutare se il problema rientra nei casi già noti oppure se richiede una diagnosi
approfondita. La risoluzione del guasto viene tracciata nel registro degli interventi con foto,
causa individuata e azione correttiva eseguita. I dati raccolti confluiscono nel cruscotto di
manutenzione che mostra gli indicatori di affidabilità delle macchine: tempo medio tra i guasti,
tempo medio di riparazione e numero di interventi per reparto. Il cruscotto viene aggiornato a
fine giornata ed è consultabile da direzione e produzione. L'analisi dell'affidabilità permette
di programmare revisioni preventive prima che l'usura provochi fermi non pianificati. Le revisioni
preventive seguono un calendario basato sulle ore di funzionamento dichiarate dal produttore.
Ogni revisione comprende controllo dei lubrificanti, verifica dei filtri, serraggio delle
fissazioni e misurazione delle vibrazioni. I valori rilevati vengono confrontati con la soglia
di allarme; se superati, si pianifica un intervento correttivo entro il successivo turno. Il
personale di manutenzione riceve una formazione annuale sulle procedure di sicurezza e sulle
nuove tecnologie installate nel reparto. La formazione è documentata tramite registro presenze e
test finale a risposta chiusa. Al termine di ogni anno il responsabile della manutenzione redige
un rapporto di sintesi con gli interventi effettuati, i costi sostenuti per ricambi e ore uomo,
le criticità ricorrenti e le azioni di miglioramento proposte per l'anno successivo. Il rapporto
viene discusso nella riunione di bilancio insieme con pianificazione investimenti e fabbisogno
di personale. La documentazione tecnica degli impianti è archiviata in formato digitale nella
libreria condivisa e ogni aggiornamento viene versionato e firmato elettronicamente. L'accesso
alla documentazione è profilato per ruolo: lettura per gli operatori, modifica per i tecnici,
approvazione per il responsabile. Tutte le attività sopra descritte devono rispettare la
normativa vigente in materia di sicurezza sul lavoro e le disposizioni interne del sistema
qualità certificato. Eventuali dubbi su procedure o autorizzazioni vanno chiariti con il
responsabile della manutenzione prima di avviare le operazioni."

echo "> POST $APP_URL/api/es/documents (contentId=$CONTENT_ID, langId=$LANG_ID, department=$DEPARTMENT) ..." >&2

RESPONSE=$(curl -sf -X POST "$APP_URL/api/es/documents" \
  -H "Content-Type: application/json" \
  -d "$(jq -n \
    --arg contentId "$CONTENT_ID" \
    --arg langId "$LANG_ID" \
    --arg department "$DEPARTMENT" \
    --arg text "$TEXT" \
    '{ contentId: $contentId, langId: $langId, department: $department, text: $text }')")

echo "$RESPONSE" | jq .
CHUNKS=$(echo "$RESPONSE" | jq -r '.chunks')
echo "> Chunk creati: $CHUNKS" >&2

if [ "$CHUNKS" -lt 3 ]; then
  echo "Avviso: attesi almeno 3 chunk" >&2
fi

echo "> Ids: $(echo "$RESPONSE" | jq -c '.chunkIds')" >&2