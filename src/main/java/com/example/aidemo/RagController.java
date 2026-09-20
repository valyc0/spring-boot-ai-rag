package com.example.aidemo;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/load-sample")
    public String loadSample() {
        ragService.loadSampleData();
        return "Sample data loaded into the vector store.";
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam String question,
                                 @RequestParam(defaultValue = "4") int topK) {
        return ragService.search(question, topK);
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return ragService.ask(question);
    }
}