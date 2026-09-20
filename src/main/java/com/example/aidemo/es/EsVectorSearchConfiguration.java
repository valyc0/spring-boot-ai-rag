package com.example.aidemo.es;

import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EsVectorSearchConfiguration {

    static final String INDEX = "custom-documents";
    static final int DIMS = 768;

    @Bean
    ApplicationRunner ensureEsVectorIndex(ElasticsearchClient client) {
        return args -> {
            if (!hasDenseVectorMapping(client)) {
                if (client.indices().exists(e -> e.index(INDEX)).value()) {
                    client.indices().delete(d -> d.index(INDEX));
                }
                TypeMapping mapping = new TypeMapping.Builder()
                        .properties("id", Property.of(p -> p.keyword(k -> k)))
                        .properties("contentId", Property.of(p -> p.keyword(k -> k)))
                        .properties("langId", Property.of(p -> p.keyword(k -> k)))
                        .properties("department", Property.of(p -> p.keyword(k -> k)))
                        .properties("chunkIndex", Property.of(p -> p.integer(k -> k)))
                        .properties("totalChunks", Property.of(p -> p.integer(k -> k)))
                        .properties("content", Property.of(p -> p.text(t -> t)))
                        .properties("embedding",
                                Property.of(p -> p.denseVector(d -> d.dims(DIMS).similarity(DenseVectorSimilarity.Cosine))))
                        .build();
                client.indices().create(new CreateIndexRequest.Builder()
                        .index(INDEX)
                        .mappings(mapping)
                        .build());
            }
        };
    }

    private static boolean hasDenseVectorMapping(ElasticsearchClient client) throws Exception {
        if (!client.indices().exists(e -> e.index(INDEX)).value()) {
            return false;
        }
        GetMappingResponse response = client.indices().getMapping(g -> g.index(INDEX));
        Map<String, Property> properties = response.result().get(INDEX).mappings().properties();
        Property embedding = properties.get("embedding");
        return embedding != null && embedding.isDenseVector();
    }
}