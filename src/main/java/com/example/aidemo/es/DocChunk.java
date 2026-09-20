package com.example.aidemo.es;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.KnnSimilarity;

@Document(indexName = "custom-documents", createIndex = false)
public class DocChunk {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String contentId;

    @Field(type = FieldType.Keyword)
    private String langId;

    @Field(type = FieldType.Keyword)
    private String department;

    @Field(type = FieldType.Integer)
    private int chunkIndex;

    @Field(type = FieldType.Integer)
    private int totalChunks;

    @Field(type = FieldType.Text)
    private String content;

    // Deve rispecchiare il mapping reale creato da EsVectorSearchConfiguration (dense_vector/cosine).
    @Field(type = FieldType.Dense_Vector, dims = EsVectorSearchConfiguration.DIMS, knnSimilarity = KnnSimilarity.COSINE)
    private List<Double> embedding;

    public DocChunk() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getLangId() {
        return langId;
    }

    public void setLangId(String langId) {
        this.langId = langId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }
}