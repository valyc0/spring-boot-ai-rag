package com.example.aidemo.es;

import java.util.List;

public record CustomAiSearchResponse(String answer, List<Hit> hits) {

    public record Hit(String id, String contentId, String langId, String department, int chunkIndex,
            int totalChunks, String content, Double score) {
    }
}