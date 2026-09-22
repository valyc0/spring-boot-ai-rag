package com.example.aidemo.es;

import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/es")
public class CustomIngestController {

    // Unici campi top-level filtrabili via kNN term query (vedi DocChunk / EsVectorSearchConfiguration).
    private static final Set<String> FILTERABLE_FIELDS = Set.of("contentId", "langId", "department");

    private final CustomIngestService service;
    private final DocChunkRepository repository;
    private final CustomAiSearchService aiSearch;

    public CustomIngestController(CustomIngestService service, DocChunkRepository repository,
            CustomAiSearchService aiSearch) {
        this.service = service;
        this.repository = repository;
        this.aiSearch = aiSearch;
    }

    @GetMapping("/query")
    public CustomAiSearchResponse query(@RequestParam String question,
            @RequestParam(required = false, defaultValue = "3") int topK,
            @RequestParam(required = false, defaultValue = "default") String conversationId,
            @RequestParam Map<String, String> allParams) throws Exception {
        // allParams contiene TUTTI i query param (Spring popola così una Map senza "name"):
        // si estraggono solo i campi effettivamente filtrabili, ignorando gli altri.
        Map<String, String> filters = allParams.entrySet().stream()
                .filter(e -> FILTERABLE_FIELDS.contains(e.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return aiSearch.search(question, filters, topK, conversationId);
    }

    /**
     * Come {@code /query} ma la risposta del LLM arriva in streaming (text/event-stream): un evento
     * SSE per ogni pezzo di testo. Usa {@code conversationId} per mantenere la memoria di conversazione.
     */
    @GetMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@RequestParam String question,
            @RequestParam(required = false, defaultValue = "3") int topK,
            @RequestParam(required = false, defaultValue = "default") String conversationId,
            @RequestParam Map<String, String> allParams) throws Exception {
        Map<String, String> filters = allParams.entrySet().stream()
                .filter(e -> FILTERABLE_FIELDS.contains(e.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return aiSearch.searchStream(question, filters, topK, conversationId);
    }

    @PostMapping("/documents")
    public CustomIngestResponse ingest(@Valid @RequestBody CustomIngestRequest request) {
        return service.ingest(request);
    }

    @GetMapping("/documents")
    public List<ChunkView> byContentId(@RequestParam String contentId) {
        return repository.findByContentId(contentId).stream()
                .map(c -> new ChunkView(c.getId(), c.getContentId(), c.getLangId(), c.getDepartment(),
                        c.getChunkIndex(), c.getTotalChunks(), c.getContent()))
                .toList();
    }

    public record ChunkView(String id, String contentId, String langId, String department,
                            int chunkIndex, int totalChunks, String content) {
    }
}