package com.example.aidemo;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
public class AiSearchService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public AiSearchService(VectorStore vectorStore, ChatClient.Builder builder) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem("""
                        You are the internal assistant of an example company.
                        Answer only using the provided context. If the answer is not
                        in the context, say that you do not know.
                        """)
                .build();
    }

    public AiSearchResponse search(String query, AiSearchRequest request) {
        // 1) Ritrieval standard Spring AI con filtro sui metadati
        Filter.Expression filter = buildFilterExpression(request.filters());
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(request.topK())
                .filterExpression(filter)
                .build());

        List<String> sources = documents.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("contentId", d.getId()))
                .toList();

        if (documents.isEmpty()) {
            return new AiSearchResponse("Nessun documento trovato nel contesto richiesto.", List.of());
        }

        // 2) Risposta generata con ChatClient usando solo il contesto filtrato
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append("[Documento ").append(i + 1).append("]\n")
                    .append(documents.get(i).getText()).append("\n\n");
        }

        String answer = chatClient.prompt()
                .user("Domanda: " + query + "\n\nContesto:\n" + context)
                .call()
                .content();

        return new AiSearchResponse(answer, sources);
    }

    private Filter.Expression buildFilterExpression(Map<String, String> filters) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op op = null;
        for (Entry<String, String> entry : filters.entrySet()) {
            if (op == null) {
                op = builder.eq(entry.getKey(), entry.getValue());
            } else {
                op = builder.and(op, builder.eq(entry.getKey(), entry.getValue()));
            }
        }
        return op == null ? null : op.build();
    }
}