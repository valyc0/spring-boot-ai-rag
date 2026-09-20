package com.example.aidemo;

import java.util.List;

public record AiSearchResponse(
        String answer,
        List<String> sources) {
}