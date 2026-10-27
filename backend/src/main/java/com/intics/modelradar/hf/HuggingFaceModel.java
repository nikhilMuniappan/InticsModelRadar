package com.intics.modelradar.hf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One model as returned by the Hugging Face Hub API
 * ({@code GET https://huggingface.co/api/models}). A DTO that mirrors the raw
 * response — only the fields we consume are declared; {@code ignoreUnknown} lets
 * the API add/remove fields without breaking deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuggingFaceModel {

    /** Model id, e.g. {@code Qwen/Qwen2.5-VL-7B-Instruct}. */
    public String id;

    /** Publishing author/org. Sometimes absent — then derived from the id prefix. */
    public String author;

    @JsonProperty("pipeline_tag")
    public String pipelineTag;

    public Instant createdAt;

    public Instant lastModified;

    public long downloads;

    public long likes;

    public List<String> tags = new ArrayList<>();

    /** Present only when the request asks for {@code cardData=true}. */
    public CardData cardData;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CardData {
        /** May be a plain string or a list depending on the model card. */
        public Object license;
    }
}
