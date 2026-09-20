package com.example.aidemo.es;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SimpleTextChunker {

    private SimpleTextChunker() {
    }

    public static List<String> chunk(String text, int maxTokens, int overlapTokens) {
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int n = words.length;
        while (start < n) {
            int end = Math.min(start + maxTokens, n);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            if (end == n) {
                break;
            }
            start = Math.min(start + maxTokens - overlapTokens, n - 1);
        }
        return chunks;
    }
}