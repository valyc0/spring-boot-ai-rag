package com.example.aidemo.es;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class CustomIngestService {

    private static final int MAX_TOKENS = 200;
    private static final int OVERLAP_TOKENS = 32;

    private final EmbeddingModel embeddingModel;
    private final DocChunkRepository repository;

    public CustomIngestService(EmbeddingModel embeddingModel, DocChunkRepository repository) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
    }

    public CustomIngestResponse ingest(CustomIngestRequest request) {
        List<String> chunks = SimpleTextChunker.chunk(request.text(), MAX_TOKENS, OVERLAP_TOKENS);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String id = request.contentId() + "-" + i;
            DocChunk chunk = new DocChunk();
            chunk.setId(id);
            chunk.setContentId(request.contentId());
            chunk.setLangId(request.langId());
            chunk.setDepartment(request.department());
            chunk.setChunkIndex(i);
            chunk.setTotalChunks(chunks.size());
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(toList(embeddingModel.embed(chunks.get(i))));
            repository.save(chunk);
            ids.add(id);
        }
        return new CustomIngestResponse(request.contentId(), chunks.size(), ids);
    }

    private static List<Double> toList(float[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add((double) value);
        }
        return result;
    }
}