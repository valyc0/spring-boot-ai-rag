package com.example.aidemo.es;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Query AI "custom" sull'indice {@code custom-documents}.
 *
 * <p>Contrariamente alla RAG "vecchia" ({@code VectorStore} Spring AI, che legge SOLO
 * {@code gemini-demo-index} e filtra su {@code metadata.*}), questa classe interroga l'indice creato
 * dall'ingest custom (vedi {@code CustomIngestService} / {@code DocChunk}), dove i campi
 * {@code contentId}/{@code langId}/{@code department} sono a livello top.</p>
 *
 * <p>Flusso: embed della domanda -> kNN search su {@code embedding} (con filtri sui campi top-level) ->
 * contesto ai chunk recuperati -> risposta del {@link ChatClient}.</p>
 */
@Service
public class CustomAiSearchService {

    private static final Logger log = LoggerFactory.getLogger(CustomAiSearchService.class);

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient client;
    private final ChatClient chatClient;

    public CustomAiSearchService(EmbeddingModel embeddingModel, ElasticsearchClient client,
            ChatClient.Builder builder, ChatMemory chatMemory) {
        this.embeddingModel = embeddingModel;
        this.client = client;
        // Stesso prompt di sistema della RAG Spring AI: rispondi SOLO col contesto fornito.
        // MessageChatMemoryAdvisor tiene in memoria (in-memory) le conversazioni per conversationId,
        // così le domande successive possono riferirsi alle risposte precedenti.
        this.chatClient = builder
                .defaultSystem("""
                        You are the internal assistant of an example company.
                        Answer only using the provided context. If the answer is not
                        in the context, say that you do not know.
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * Esegue il retrieval vettoriale sui chunk custom e genera la risposta del LLM.
     *
     * @param query          la domanda dell'utente
     * @param filters        mappa campo-top-level -> valore (es. {@code contentId}, {@code langId},
     *                       {@code department}); vengono applicati come {@code term} query DENTRO la kNN
     * @param topK           numero di chunk più simili da recuperare
     * @param conversationId id della conversazione (memoria in-memory, consente follow-up)
     * @return risposta testuale del modello + hits recuperati (campi top-level e score)
     */
    public CustomAiSearchResponse search(String query, Map<String, String> filters, int topK, String conversationId)
            throws Exception {
        log.debug("Query AI (sincrona) arrivata: query={}, filters={}, topK={}, conversationId={}",
                query, filters, topK, conversationId);
        List<CustomAiSearchResponse.Hit> hits = retrieveHits(query, filters, topK);

        // Nessun chunk rilevante: risposta cortese, senza chiamare il LLM.
        if (hits.isEmpty()) {
            log.debug("Nessun chunk recuperato, risposta senza chiamata al LLM");
            return new CustomAiSearchResponse("Nessun documento trovato nel contesto richiesto.", List.of());
        }

        String answer = chatClient.prompt()
                .user("Domanda: " + query + "\n\nContesto:\n" + buildContext(hits))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.debug("Risposta LLM (sincrona): {}", answer);
        return new CustomAiSearchResponse(answer, hits);
    }

    /**
     * Come {@link #search(String, Map, int, String)} ma la risposta del LLM viene restituita in streaming,
     * token per token. I hit recuperati NON vengono inviati (solo testo).
     *
     * @param query          la domanda dell'utente
     * @param filters        mappa campo-top-level -> valore applicata come {@code term} query dentro la kNN
     * @param topK           numero di chunk più simili da recuperare
     * @param conversationId id della conversazione (memoria in-memory, consente follow-up)
     * @return stream di pezzi di testo della risposta del modello
     */
    public Flux<String> searchStream(String query, Map<String, String> filters, int topK, String conversationId)
            throws Exception {
        log.debug("Query AI (streaming) arrivata: query={}, filters={}, topK={}, conversationId={}",
                query, filters, topK, conversationId);
        List<CustomAiSearchResponse.Hit> hits = retrieveHits(query, filters, topK);

        if (hits.isEmpty()) {
            log.debug("Nessun chunk recuperato, risposta senza chiamata al LLM");
            return Flux.just("Nessun documento trovato nel contesto richiesto.");
        }

        return chatClient.prompt()
                .user("Domanda: " + query + "\n\nContesto:\n" + buildContext(hits))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(token -> log.debug("Risposta LLM (streaming) token: {}", token))
                .doOnComplete(() -> log.debug("Streaming LLM completato"));
    }

    /**
     * Retrieval vettoriale condiviso tra modalità sincrona e streaming: quando la domanda viene embeddata
     * con lo STESSO modello usato in ingest (gemini-embedding-001, 768 dims) i vettori sono comparabili.
     */
    private List<CustomAiSearchResponse.Hit> retrieveHits(String query, Map<String, String> filters, int topK)
            throws Exception {
        // 1) La domanda deve essere embeddata con lo STESSO modello usato in ingest
        //    (gemini-embedding-001, 768 dims) altrimenti i vettori non sono comparabili.
        List<Float> vector = toFloatList(embeddingModel.embed(query));
        log.debug("Query embeddata, dimensione vettore: {}", vector.size());

        // 2) Filtri sui campi top-level: ogni coppia chiave/valore diventa una term query.
        //    Es. {"department":"operations"} -> {"term":{"department":"operations"}}.
        List<Query> filterQueries = new ArrayList<>();
        for (Entry<String, String> entry : filters.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            filterQueries.add(Query.of(q -> q.term(t -> t.field(field).value(value))));
        }

        // 3) kNN: il filter va DENTRO knn (non come query a fianco), con la sintassi ES 8.12+.
        //    numCandidates >= k * 10 è un buon compromesso richiamo/costo.
        KnnSearch knn = new KnnSearch.Builder()
                .field("embedding")
                .queryVector(vector)
                .k(topK)
                .numCandidates(Math.max(100, topK * 10))
                .filter(filterQueries)
                .build();

        SearchRequest request = new SearchRequest.Builder()
                .index(EsVectorSearchConfiguration.INDEX)
                .knn(knn)
                .build();

        // 4) Leggiamo il _source come Map generica (il client ES non conosce l'entity) e mappiamo
        //    i campi top-level sul record Hit. Nota: "embedding" è un dense_vector e NON compare
        //    mai in _source (comportamento di Elasticsearch): i campi top-level sì.
        log.debug("Eseguo kNN search su indice '{}': topK={}, filtri={}", EsVectorSearchConfiguration.INDEX,
                topK, filters);
        List<CustomAiSearchResponse.Hit> hits = new ArrayList<>();
        for (Hit<Map> hit : client.search(request, Map.class).hits().hits()) {
            Map source = hit.source();
            hits.add(new CustomAiSearchResponse.Hit(hit.id(), str(source.get("contentId")),
                    str(source.get("langId")), str(source.get("department")),
                    intVal(source.get("chunkIndex")), intVal(source.get("totalChunks")),
                    str(source.get("content")), hit.score()));
        }
        log.debug("Recuperati {} chunk: {}", hits.size(), hits);
        return hits;
    }

    /**
     * Costruisce il contesto per il LLM: chunk numerati, in ordine di score.
     */
    private static String buildContext(List<CustomAiSearchResponse.Hit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            context.append("[Documento ").append(i + 1).append("]\n")
                    .append(hits.get(i).content()).append("\n\n");
        }
        return context.toString();
    }

    /**
     * Converte un array di {@code float} (output di {@code EmbeddingModel.embed}) in {@code List<Float>},
     * che è il tipo richiesto da {@code KnnSearch.Builder.queryVector}.
     * <p>Attenzione: {@code Arrays.stream(float[])} non esiste (stream(T[]) funziona solo con array
     * di oggetti), per questo la conversione è manuale.
     */
    private static List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static int intVal(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}