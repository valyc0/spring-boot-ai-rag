package com.example.aidemo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/ai")
public class AiSearchController {

    private final AiSearchService aiSearchService;

    public AiSearchController(AiSearchService aiSearchService) {
        this.aiSearchService = aiSearchService;
    }

    @PostMapping
    public AiSearchResponse search(@RequestParam("q") String query,
                                   @RequestBody(required = false) AiSearchRequest request) {
        return aiSearchService.search(query, request == null ? new AiSearchRequest(null, null) : request);
    }
}