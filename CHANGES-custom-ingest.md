# Modifiche al flusso custom ingest/query (`com.example.aidemo.es`)

Analisi del flusso `CustomIngestController` → `CustomIngestService` / `CustomAiSearchService`
e correzione di due errori concettuali trovati.

## 1. `DocChunk.embedding`: mapping annotato in modo scorretto

**File:** [`DocChunk.java`](src/main/java/com/example/aidemo/es/DocChunk.java)

**Prima:**
```java
@Field(type = FieldType.Double)
private List<Double> embedding;
```

**Dopo:**
```java
// Deve rispecchiare il mapping reale creato da EsVectorSearchConfiguration (dense_vector/cosine).
@Field(type = FieldType.Dense_Vector, dims = EsVectorSearchConfiguration.DIMS, knnSimilarity = KnnSimilarity.COSINE)
private List<Double> embedding;
```

**Perché:** l'indice `custom-documents` viene creato "a mano" da `EsVectorSearchConfiguration` con
il campo `embedding` come `dense_vector` (768 dim, similarity `cosine`). L'entity Spring Data lo
dichiarava però come `FieldType.Double` (un array di numeri qualsiasi). Funzionava solo perché
`@Document(createIndex = false)` impedisce a Spring Data di generare autonomamente il mapping
dall'annotazione. Il rischio: se in futuro qualcuno abilita `createIndex = true` (o usa
`elasticsearchOperations` per creare l'indice dall'entity), verrebbe generato un mapping sbagliato
(niente `dense_vector`), rompendo silenziosamente la ricerca kNN. Ora l'annotazione riflette la
realtà e riusa la costante `EsVectorSearchConfiguration.DIMS`, cosi le due definizioni non possono
più divergere.

## 2. `CustomIngestController.query`: filtri estratti con una deny-list fragile

**File:** [`CustomIngestController.java`](src/main/java/com/example/aidemo/es/CustomIngestController.java)

**Prima:**
```java
@RequestParam(required = false) Map<String, String> filters
...
Map<String, String> clean = filters == null ? Map.of()
        : filters.entrySet().stream()
                .filter(e -> !"question".equals(e.getKey()) && !"topK".equals(e.getKey()))
                .collect(...);
```

**Dopo:**
```java
private static final Set<String> FILTERABLE_FIELDS = Set.of("contentId", "langId", "department");
...
@RequestParam Map<String, String> allParams
...
Map<String, String> filters = allParams.entrySet().stream()
        .filter(e -> FILTERABLE_FIELDS.contains(e.getKey()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
```

**Perché:** Spring, quando incontra un `@RequestParam Map<String, String>` senza `name`, lo popola
con **tutti** i query param della richiesta (compresi `question` e `topK`), non solo con quelli
"extra". Il codice originale doveva quindi escluderli esplicitamente per nome (deny-list): fragile,
perché qualsiasi nuovo `@RequestParam` aggiunto in futuro a questo endpoint sarebbe finito
automaticamente tra i filtri passati alla `term` query su Elasticsearch, a meno di ricordarsi di
escluderlo anche lì. Inoltre un client poteva passare un parametro qualsiasi (es. `embedding=xyz`)
e vederlo trasformato in un filtro `term` su un campo non pensato per essere filtrabile.

La nuova versione usa una **allow-list** esplicita (`contentId`, `langId`, `department`, gli unici
campi top-level filtrabili secondo `DocChunk`/`EsVectorSearchConfiguration`): solo questi tre
diventano filtri, tutto il resto viene ignorato senza bisogno di manutenzione futura.

## Verifica effettuata

1. `./delete-index.sh` → cancellato l'indice `custom-documents`.
2. Riavvio app (`./start.sh`) → `EsVectorSearchConfiguration` ricrea l'indice; mapping verificato via
   `GET /custom-documents/_mapping`: `embedding` è `dense_vector`, `dims: 768`, `similarity: cosine`.
3. `./ingest-custom.sh manual-operations-test` → 4 chunk creati correttamente.
4. `GET /api/es/query?question=...&topK=3` → risposta LLM corretta, hit di score più alto pertinente.
5. `GET /api/es/query?...&department=operations` → trova il documento; `department=finance` → nessun
   hit (filtro allow-list funzionante).
6. `GET /api/es/query?...&embedding=xyz` → parametro sconosciuto ignorato, nessun errore, nessun
   filtro spurio applicato.
7. `GET /api/es/documents?contentId=manual-operations-test` → rilettura dei 4 chunk corretta.
8. App fermata con `./stop.sh` a fine test.

Nessuna modifica al comportamento esterno delle API (stessi endpoint, stessi parametri documentati
in [`README.md`](README.md)): gli script esistenti (`ai-search.sh`, `ingest-custom.sh`, ecc.) non sono
impattati.

## Problemi noti non ancora risolti (fuori scope di questa modifica)

Emersi durante l'analisi ma non affrontati in questo intervento:

- **`EsVectorSearchConfiguration`** cancella e ricrea l'intero indice `custom-documents` ad ogni
  avvio se il mapping non risulta un `dense_vector` valido: rischio di perdita dati silenziosa.
- **`CustomIngestService.ingest`** non cancella i chunk residui dello stesso `contentId` prima di
  reinserirli: un documento ri-ingerito con meno chunk lascia chunk orfani nell'indice.
- Duplicazione architetturale tra la pipeline RAG "standard" Spring AI (`RagService`/
  `AiSearchService`/`IngestionService`, indice `gemini-demo-index`) e quella "custom"
  (`CustomIngestService`/`CustomAiSearchService`, indice `custom-documents`).
