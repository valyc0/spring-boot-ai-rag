package com.example.aidemo;

import java.util.Map;

public record AiSearchRequest(
        Integer topK,
        Map<String, String> filters) {

    public AiSearchRequest {
        if (topK == null) {
            topK = 4;
        }
        if (filters == null) {
            filters = Map.of();
        }
    }
}