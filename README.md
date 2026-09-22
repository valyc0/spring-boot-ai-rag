# Gemini RAG Demo (Spring AI)

Piccolissimo esempio di **embedding** e **retrieval (RAG)** con Spring Boot AI e **Google Gemini**
(Gemini Developer API / AI Studio, piano gratuito).

- Stack: **Spring Boot 3.4.13**, **Spring AI 1.1.8**, Java 21, Maven
- Chat: `gemini-3.6-flash` via `spring-ai-starter-model-google-genai`
- Embedding: `gemini-embedding-001` (768 dims) via `spring-ai-starter-model-google-genai-embedding`
- Vector store: **Elasticsearch** (`spring-ai-starter-vector-store-elasticsearch`) su `http://localhost:9200`
- Nota: `elasticsearch-java` è pinnato a `8.18.8` nel pom (Boot 3.4 ne gestisce 8.15.5, che manca di
  `DenseVectorSimilarity` usato dallo store Spring AI 1.1.8)

## Due percorsi in questa demo (confronto)

La demo contiene **due modi diversi** di fare RAG sullo stesso Elasticsearch, per confrontarli:

| | **Percorso "Spring AI"** | **Percorso "custom"** |
|---|---|---|
| Indice | `gemini-demo-index` | `custom-documents` |
| Ingest | `VectorStore` Spring AI (`/api/documents`) | Repository Spring Data (`/api/es/documents`) |
| Formato doc | `content` + `metadata` annidata + `embedding` | campi a livello top (`contentId`, `langId`, `department`) |
| Filtri | su `metadata.*` (filter expression) | su campo top-level (term/knn filter) |
| Query AI | `VectorStore.similaritySearch` + `ChatClient` | kNN ES + `ChatClient` |
| Quando usarlo | prototipo veloce, dati nostri nella forma di Spring AI | quando serve controllare il formato dei documenti (es. campo top-level) |

**Perché serve il percorso custom?** La vecchia query `/api/search/ai` (e `/api/rag/*`) legge **solo**
da `gemini-demo-index`, nello store Spring AI: i campi stanno dentro `metadata` annidata e l'`embedding` nel
mapping hardcoded dello store. Non può interrogare `custom-documents`, dove i tuoi campi sono a livello top:
se gli fai puntare lì perderebbe i filtri e i campi. Quindi per il tuo progetto reale (ingest custom +
campi piatti + query ai) serve la **risposta `custom`**, e la vecchia RAG resta solo come demo "Spring AI way".

## Requisiti

- Java 21
- Maven 3.8+
- Una API key Gemini gratuita: https://aistudio.google.com/apikey
- Un'istanza **Elasticsearch** su `localhost:9200`
  (es. il container docker `elasticsearch`, `xpack.security.enabled=false`, `discovery.type=single-node`)

## Passi (dall'inizio)

### 1. Configurare Elasticsearch

```bash
# avvia il container (se il nome è "elasticsearch")
docker start elasticsearch
```

### 2. Inserire la chiave API

`application.properties` legge la chiave da `${GEMINI_API_KEY}`.
Il file `.env` (gitignored) viene caricato automaticamente all'avvio
(`spring.config.import=optional:file:./.env[.properties]`):

```bash
cp .env.example .env
# poi modifica .env e inserisci la chiave da https://aistudio.google.com/apikey
```

`.env`:

```properties
GEMINI_API_KEY=<la_tua_chiave>
```

Alternativa: esporta la variabile d'ambiente (ha la precedenza su `.env`):

```bash
export GEMINI_API_KEY=<la_tua_chiave>
```

### 3. Creare l'indice Elasticsearch (con la pipeline per i campi top-level)

```bash
./create-index.sh
```

Lo script ricrea l'indice `gemini-demo-index` e:
- applica il mapping con `embedding` `dense_vector` 768 dims (coseno)
- crea la ingest pipeline `copy-meta`
- la attiva come `default_pipeline` dell'indice

### La pipeline `copy-meta`: perché esiste e come funziona

**Il problema.** Spring AI, quando scrive un documento su Elasticsearch, serializza sempre tutto ciò che
non è `id`/`content`/`embedding` dentro un unico campo annidato `metadata`:

```json
{
  "id": "policy-viaggi-2026-1",
  "content": "...",
  "metadata": { "contentId": "policy-viaggi-2026", "langId": "it-IT", "department": "operations" },
  "embedding": [...]
}
```

Non esiste alcuna configurazione per fargli mettere `langId`/`contentId` a livello top: il formato è
hardcoded dallo store (sia nella versione 2.0.1 sia nella 1.1.8). Se ti servono "fuori" da `metadata`,
devi agire nell'**ingest** di Elasticsearch.

**La soluzione: una ingest pipeline.** Una *ingest pipeline* è una catena di trasformazioni che ES
applica a ogni documento **prima di indicizzarlo**. Nell'indice la si aggancia come
`default_pipeline`, così ogni scrittura (da qualunque client, anche l'app Spring AI) passa da lì:

```json
{
  "processors": [
    { "set": { "field": "langId",    "value": "{{metadata.langId}}",
               "if": "ctx.metadata != null && ctx.metadata.langId != null" } },
    { "set": { "field": "contentId", "value": "{{metadata.contentId}}",
               "if": "ctx.metadata != null && ctx.metadata.contentId != null" } }
  ]
}
```

Il processor `set` copia `metadata.contentId` → `contentId` (top-level) e idem per `langId`. Il tutto avviene
nel `_source`: **prima** del `_doc` lo si vede solo dentro `metadata`, **dopo** l'indicizzazione compare
anche a livello top. La condizione `if` evita che vengano creati campi con stringa vuota quando il documento
non ha quei metadati (es. i 5 doc di esempio, che non li definiscono).

Esempio, prima → dopo l'indicizzazione:

```json
// documento inviato a ES
{ "metadata": { "contentId": "policy-viaggi-2026", "langId": "it-IT", ... }, ... }
// _source indicizzato
{ "metadata": { ... }, "contentId": "policy-viaggi-2026", "langId": "it-IT", ... }
```

**Perché non `copy_to` nel mapping?** `copy_to` esiste, ma copia il valore solo nell'**indice invertito**
per la ricerca testuale: nel `_source` (quello che vedi con una query e che rileggi) il campo copiato
**non appare**. `copy_to` = utile per cercare, inutile per "vedere" il dato al top.

**Nota importante di design.** Dato che Spring AI scrive comunque `contentId`/`langId` dentro `metadata`,
il `_source` ora li contiene due volte (annidati + top-level). La **fonte di verità è `metadata`**: se un
documento esiste già indicizzato e migliori un punto in pipeline, i valori top-level del documento NON si
aggiornano da soli (la pipeline agisce solo sulle scritture nuove) → per ripulire/ricreare serve re-index
(scrittura con lo stesso `_id`, che è un upsert).

### 4. Compilare e avviare l'app

```bash
mvn -o -B package -DskipTests
java -jar target/gemini-rag-demo-0.0.1-SNAPSHOT.jar
```

Al boot l'app carica 5 documenti di esempio (id fissi → idempotente). L'indice esiste già: lo store
Spring AI non lo ricrea.

### 5. Popolare con altri documenti (chunking + embed + scrittura su ES)

```bash
./ingest-sample.sh
```

Equivalente, dato un documento `contentId`, `langId`, `metadata` e `text`, l'app lo chunkizza
(`TokenTextSplitter`, chunk da 200 token), lo embedda e lo scrive in ES. I chunk hanno id deterministici
(`<contentId>-N`) → ri-caricare lo stesso `contentId` è un **upsert** (nessun duplicato).

Endpoint diretto:

```bash
curl -X POST "http://localhost:8080/api/documents" \
  -H "Content-Type: application/json" \
  -d '{
    "contentId": "hr-manual-2026",
    "langId": "it-IT",
    "metadata": { "department": "risorse-umane" },
    "text": "Testo lungo che verrà chunkizzato..."
  }'
```

### 5bis. Ingest custom con Spring Data repository (senza Spring AI VectorStore)

Dimostrazione alternativa che NON usa lo store Spring AI: ingest con un normale repository
Spring Data Elasticsearch (`ElasticsearchRepository`), entity `DocChunk` con **campi a livello
top** (`contentId`, `langId`, `department`, ...) e embedding calcolato con l'`EmbeddingModel` di
Spring AI. I chunk hanno id deterministici `<contentId>-N` (upsert). L'indice `custom-documents`
viene creato all'avvio con `embedding` come `dense_vector` 768 dims (coseno).

**Codice (pacchetto `com.example.aidemo.es`):**

| Classe | Ruolo |
|---|---|
| `DocChunk` | Entity `@Document(indexName="custom-documents", createIndex=false)` |
| `DocChunkRepository` | `ElasticsearchRepository<DocChunk, String>` (metodi `findByContentId`, `findByContentIdAndLangId`) |
| `SimpleTextChunker` | Chunking custom: split su whitespace, 200 token max, 32 di overlap |
| `CustomIngestService` | Chunk + embed (`EmbeddingModel`) + salvataggio via repository |
| `CustomIngestController` | Endpoint `/api/es/documents` (POST ingest, GET rilettura) e `/api/es/query` (query AI) |
| `CustomAiSearchService` | kNN su `embedding` (client `ElasticsearchClient`) + filtri top-level + risposta `ChatClient` |
| `EsVectorSearchConfiguration` | All'avvio crea/ripara l'indice con mapping `dense_vector` per `embedding` |

**Perché `createIndex=false`.** Spring Data, di default, auto-crea l'indice a partire dall'entity:
col campo `embedding` annotato `@Field(type=Double)` creerebbe un mapping `embedding` come `double`,
che NON è un `dense_vector` → la ricerca kNN fallirebbe con
`[knn] queries are only supported on [dense_vector] fields`. Con `createIndex=false` l'entity non crea
più nulla; il mapping corretto lo garantisce `EsVectorSearchConfiguration` (che inoltre **ripara** un
indice esistente col mapping sbagliato: lo cancella e lo ricrea).
Nota: nel runner usare `Property.isDenseVector()` per il check, non `Property.denseVector()`:
il getter del variant `denseVector()` lancia `IllegalStateException` se il tipo reale è `double`.

```bash
# ingest custom (text → chunking 200 tok / overlap 32 → embed → ES)
curl -s -X POST "http://localhost:8080/api/es/documents" \
  -H "Content-Type: application/json" \
  -d '{
    "contentId": "manual-it-2026",
    "langId": "it-IT",
    "department": "operations",
    "text": "Testo lungo che verrà chunkizzato..."
  }'

# rilettura dei chunk di un contenuto (senza vettore)
curl -s "http://localhost:9200/custom-documents/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"contentId":"manual-it-2026"}}}'
```

**Query AI custom** (kNN sul vettore + risposta `ChatClient`, con filtri sui campi top-level):

```bash
curl -s "http://localhost:8080/api/es/query?question=quanto%20viene%20rimborsata%20la%20benzina%20in%20trasferta&topK=2"
# filtri sui campi top-level (contentId/langId/department)
curl -s "http://localhost:8080/api/es/query?question=...&topK=2&department=operations&langId=it-IT"
# filtri "fissi": &contentId=..., &langId=..., &department=...
# variante con curl --data-urlencode (evita di percent-encodare a mano)
curl -s -G "http://localhost:8080/api/es/query" \
  --data-urlencode "question=quali sono i dispositivi di protezione" \
  --data-urlencode "topK=2" \
  --data-urlencode "department=operations" \
  --data-urlencode "langId=it-IT"
```

La risposta è `{"answer": "...", "hits": [{id, contentId, langId, department, chunkIndex, totalChunks, content, score}]}`.

**Query AI custom in streaming** (stessa kNN, ma la risposta del LLM arriva token per token via SSE,
`text/event-stream`):
```bash
curl -N "http://localhost:8080/api/es/query/stream?question=quanto%20viene%20rimborsata%20la%20benzina%20in%20trasferta&topK=2"
# variante con --data-urlencode (evita di percent-encodare a mano)
curl -N -G "http://localhost:8080/api/es/query/stream" \
  --data-urlencode "question=quali sono i dispositivi di protezione" \
  --data-urlencode "topK=2" \
  --data-urlencode "department=operations" \
  --data-urlencode "langId=it-IT"
```
Ogni pezzo di testo arriva come evento SSE (`data:...`). Passando lo stesso `conversationId` (default
`"default"`) il bot ricorda le risposte precedenti e può rispondere a domande di follow-up sul contenuto.

Qui NON serve la pipeline `copy-meta`: i campi nascono già a livello top nell'entity.

**Come funziona la query AI custom** (`CustomAiSearchService`):
1. la `question` viene embeddata con lo stesso `EmbeddingModel` dell'ingest;
2. si esegue una **kNN search** su `custom-documents` con `ElasticsearchClient`
   (`knn.filter` con query `term` per i filtri top-level, `filter` va dentro `knn`);
3. si scrivono nel `_source` i campi top-level recuperati;
4. il contesto viene passato al `ChatClient` che genera `answer`.

**Attenzione su `dense_vector` e `_source`.** Il vettore indicizzato da `repository.save()` NON appare
nel `_source` del documento restituito da ES: è un comportamento di Elasticsearch, i campi
`dense_vector` non vengono serializzati in `_source` (si recuperano con `fields` o con una kNN search).
È normale vedere `embedding` assente in `_source`: la verifica che il vettore c'è è la ricerca kNN:

```bash
# kNN coi filtri su campi top-level (contentId) — nota: filter DENTRO knn
curl -s "http://localhost:9200/custom-documents/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "knn": {
      "field": "embedding",
      "query_vector": [...768 float...],
      "k": 5,
      "num_candidates": 20,
      "filter": [{ "term": { "contentId": "manual-it-2026" } }]
    }
  }'
```

### 6. Ricerca AI (RAG: retrieval filtrato + risposta del LLM)

```bash
# senza filtri
./ai-search.sh "come vengono rimborsate le spese di benzina in trasferta"

# con filtri sui metadati
./ai-search.sh "come vengono rimborsate le spese di benzina in trasferta" \
  '{"filters":{"department":"operations"}}'

# con filtri e topK
./ai-search.sh "come vengono rimborsate le spese di benzina in trasferta" \
  '{"topK":2,"filters":{"department":"operations","langId":"it-IT"}}'
```

Esempi curl senza variabili (con `--url-query` per non percent-encodare a mano):

```bash
./curl-example.sh
```

L'endpoint è `POST /api/search/ai?q=<domanda>` con body JSON opzionale
(`{"topK": n, "filters": {"chiave": "valore"}}`). Il retrieval usa `VectorStore.similaritySearch`
con `FilterExpressionBuilder` (AND sui metadati); la risposta è generata da `ChatClient` usando solo il
contesto recuperato. La risposta contiene `answer` e `sources` (contentId dei chunk usati).

> **Importante:** questa query legge SOLO `gemini-demo-index` (i doc ingeriti con `/api/documents`).
> I dati ingeriti con l'ingest custom (`/api/es/documents`, indice `custom-documents`) NON compaiono qui.
> Per interrogarli usa la query custom `/api/es/query` (vedi 5bis).

### 7. Verificare/cercare su Elasticsearch

```bash
# query semplice sull'indice (vero JSON, senza i vettori)
./search-es.sh "rimborso benzina" 5
```

### 8. Svuotare l'indice (mantiene indice, mapping e pipeline)

```bash
./empty-index.sh
```

## Endpoint (riepilogo)

| Endpoint | Metodo | Descrizione |
|---|---|---|
| `/api/documents` | POST | Ingest Spring AI: text + contentId/langId/metadata → chunk + embed → ES |
| `/api/es/documents` | POST | Ingest custom Spring Data: text + contentId/langId/department → chunk + embed → ES |
| `/api/es/documents?contentId=...` | GET | Rilettura chunk custom (senza embedding) |
| `/api/es/query?question=...&topK=&contentId=&langId=&department=` | GET | Query AI custom: kNN + risposta LLM con filtri top-level |
| `/api/es/query/stream?question=...&topK=&conversationId=&contentId=&langId=&department=` | GET | Come sopra ma risposta in streaming SSE, con memoria di conversazione |
| `/api/search/ai?q=...` | POST | Ricerca AI filtrata (RAG) con body JSON opzionale |
| `/api/embedding?text=...` | GET | Embedding di un testo (dimensioni/preview) |
| `/api/cosine?text1=...&text2=...` | GET | Similarità coseno tra due testi |
| `/api/rag/search?question=...` | GET | Solo retrieval vettoriale |
| `/api/rag/ask?question=...` | GET | RAG completo (retrieval + risposta LLM) |
| `/api/rag/load-sample` | POST | Ricarica i 5 documenti di esempio |

## Note

- Il piano gratuito Gemini ha rate limit severi (`429`, es. 20 `generateContent`/min su `gemini-3.6-flash`).
  Per usi reali serve una chiave a pagamento. Il modello vecchio `gemini-2.5-flash` dà `404` per i nuovi utenti.
- I vettori e i documenti vivono in Elasticsearch e **persistono tra i riavvii** dell'app.
- I campi a livello top (`langId`, `contentId`) nel `_source` si ottengono con la ingest pipeline
  `copy-meta` (`default_pipeline` dell'indice): vedi sezione dedicata al passo 3. Per l'ingest custom
  (passo 5bis) i campi top-level nascono direttamente nell'entity, senza pipeline.
- I campi `dense_vector` (es. `embedding`) non compaiono mai nel `_source`: sono comunque indicizzati
  e interrogabili con kNN (vedi passo 5bis). Non è un bug, è il comportamento di Elasticsearch.
- `gemini-demo-index` e `custom-documents` sono **due indici separati** e indipendenti: la query
  `/api/search/ai` cerca solo nel primo, `/api/es/query` solo nel secondo.
- Se svuoti l'indice con `empty-index.sh` i settings e la pipeline si conservano (resta tutto uguale);
  `create-index.sh` li ricrea da zero.
- La chiave API non va committata nel repository (vedi punto 2).
- Per fermare/avviare l'app senza passare per Maven: `./stop.sh` / `./start.sh`.