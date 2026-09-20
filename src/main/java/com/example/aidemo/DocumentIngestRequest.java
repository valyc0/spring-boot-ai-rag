package com.example.aidemo;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record DocumentIngestRequest(
        @NotBlank String contentId,
        @NotBlank String langId,
        @NotEmpty String text,
        Map<String, String> metadata) {
}