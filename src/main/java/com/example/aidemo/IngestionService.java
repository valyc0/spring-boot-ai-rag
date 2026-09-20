package com.example.aidemo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private final TokenTextSplitter textSplitter;
    private final VectorStore vectorStore;

    public IngestionService(VectorStore vectorStore) {
        // Standard Spring AI ETL: DocumentReader -> DocumentTransformer -> DocumentWriter
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(200)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .build();
        this.vectorStore = vectorStore;
    }

    public IngestResponse ingest(DocumentIngestRequest request) {
        // 1) READ: standard DocumentReader (text -> Document)
        TextReader textReader = new TextReader(
                new ByteArrayResource(request.text().getBytes(StandardCharsets.UTF_8)));
        textReader.getCustomMetadata().put("contentId", request.contentId());
        textReader.getCustomMetadata().put("langId", request.langId());
        if (request.metadata() != null) {
            textReader.getCustomMetadata().putAll(request.metadata());
        }
        List<Document> documents = textReader.read();

        // 2) TRANSFORM: standard DocumentTransformer (chunking)
        List<Document> chunks = textSplitter.apply(documents);

        // deterministic ids (contentId-N) -> idempotent upsert on Elasticsearch
        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = request.contentId() + "-" + i;
            chunkIds.add(chunkId);
            chunks.set(i, chunks.get(i).mutate().id(chunkId).build());
            chunks.get(i).getMetadata().put("chunkIndex", i);
            chunks.get(i).getMetadata().put("totalChunks", chunks.size());
        }

        // 3) LOAD: standard DocumentWriter (embedding + write on Elasticsearch)
        vectorStore.accept(chunks);

        return new IngestResponse(request.contentId(), request.langId(), chunks.size(), chunkIds);
    }
}