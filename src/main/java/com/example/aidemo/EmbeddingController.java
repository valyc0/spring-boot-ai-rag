package com.example.aidemo;

import java.util.Arrays;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EmbeddingController {

    private final EmbeddingModel embeddingModel;

    public EmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/embedding")
    public Map<String, Object> embed(@RequestParam(defaultValue = "Ciao mondo") String text) {
        float[] vector = embeddingModel.embed(text);
        return Map.of(
                "text", text,
                "dimensions", vector.length,
                "preview", Arrays.toString(Arrays.copyOfRange(vector, 0, Math.min(10, vector.length))));
    }

    @GetMapping("/cosine")
    public Map<String, Object> cosine(@RequestParam String text1, @RequestParam String text2) {
        float[] v1 = embeddingModel.embed(text1);
        float[] v2 = embeddingModel.embed(text2);
        return Map.of(
                "text1", text1,
                "text2", text2,
                "similarity", cosineSimilarity(v1, v2));
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0.0 || normB == 0.0 ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}