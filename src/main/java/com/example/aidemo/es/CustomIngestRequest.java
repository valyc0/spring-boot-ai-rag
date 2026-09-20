package com.example.aidemo.es;

import jakarta.validation.constraints.NotBlank;

public record CustomIngestRequest(
        @NotBlank String contentId,
        @NotBlank String langId,
        String department,
        @NotBlank String text) {
}