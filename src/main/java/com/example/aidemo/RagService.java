package com.example.aidemo;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder builder) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem("""
                        You are the internal assistant of an example company.
                        Answer only using the provided context. If the answer is not
                        in the context, say that you do not know.
                        """)
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder()
                                .topK(4)
                                .similarityThreshold(0.3)
                                .build())
                        .build())
                .build();
    }

    public void loadSampleData() {
        List<Document> docs = List.of(
                new Document("sample-ferie",
                        "Le ferie maturano a 30 giorni lavorativi all'anno. Le richieste si inoltrano "
                                + "al manager con almeno 15 giorni di preavviso tramite il portale HR.",
                        Map.of("source", "ferie.md")),
                new Document("sample-smart-working",
                        "Lo smart working (remoto) e' permesso fino a 3 giorni alla settimana previo accordo "
                                + "con il proprio team. Il giorno di lavoro da casa richiede la disponibilita' "
                                + "a partecipare alle riunioni sincronizzate.",
                        Map.of("source", "smart-working.md")),
                new Document("sample-sanita",
                        "La polizza sanitaria integrativa copre visite specialistiche, analisi e odontoiatria "
                                + "fino a 1500 euro all'anno per ogni dipendente e i suoi familiari conviventi.",
                        Map.of("source", "sanita.md")),
                new Document("sample-rimborsi",
                        "Il rimborso spese per trasferte: vitto e' rimborsato fino a 30 euro al giorno, "
                                + "albergo fino a 110 euro per notte. Le richieste vanno caricate entro 30 "
                                + "giorni dalla fine della trasferta.",
                        Map.of("source", "rimborsi.md")),
                new Document("sample-formazione",
                        "Ogni dipendente ha diritto a 25 ore annuali di formazione. Sono disponibili corsi "
                                + "scontati sui framework Java/Spring e certificazioni cloud.",
                        Map.of("source", "formazione.md")));

        vectorStore.add(docs);
    }

    public List<Document> search(String question, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .build());
    }

    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}