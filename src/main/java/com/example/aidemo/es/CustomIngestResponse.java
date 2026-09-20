package com.example.aidemo.es;

import java.util.List;

public record CustomIngestResponse(String contentId, int chunks, List<String> chunkIds) {
}