package com.example.aidemo;

import java.net.URI;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDemoApplication.class, args);
    }

    @Bean
    RestClient elasticRestClient(@Value("${spring.elasticsearch.uris:http://localhost:9200}") String uri) {
        return RestClient.builder(HttpHost.create(uri)).build();
    }

    @Bean
    ApplicationRunner loadSampleData(RagService ragService) {
        return args -> ragService.loadSampleData();
    }
}