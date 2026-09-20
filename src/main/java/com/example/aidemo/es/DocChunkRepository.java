package com.example.aidemo.es;

import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface DocChunkRepository extends ElasticsearchRepository<DocChunk, String> {

    List<DocChunk> findByContentId(String contentId);

    List<DocChunk> findByContentIdAndLangId(String contentId, String langId);
}