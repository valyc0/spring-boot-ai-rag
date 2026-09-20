package com.example.aidemo;

import java.util.List;

public record IngestResponse(
        String contentId,
        String langId,
        int chunks,
        List<String> chunkIds) {
}