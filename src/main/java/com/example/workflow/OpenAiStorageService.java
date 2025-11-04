package com.example.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiStorageService {

    private final ObjectMapper om = new ObjectMapper();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.vector-store-id:}")
    private String vectorStoreId; // можно задать через env OPENAI_VECTOR_STORE_ID

    private WebClient client() {
        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public void storeRunArtifacts(String narrative, String plantUml, List<Issue> addressed) {
        try {
            String vsId = ensureVectorStore();
            String fNarr = uploadFile("narrative.txt", "text/plain", narrative.getBytes(StandardCharsets.UTF_8));
            String fPlant = uploadFile("domain-model.puml", "text/plain", plantUml.getBytes(StandardCharsets.UTF_8));
            String fIssues = uploadFile("addressed-issues.json", "application/json", om.writeValueAsBytes(addressed));
            attachFilesToVectorStore(vsId, List.of(fNarr, fPlant, fIssues));
        } catch (Exception e) {
            // Логируем и не прерываем основной сценарий
            e.printStackTrace();
        }
    }

    private String ensureVectorStore() {
        if (vectorStoreId != null && !vectorStoreId.isBlank()) return vectorStoreId;

        Map<?,?> resp = client().post()
                .uri("/vector_stores")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "iconix-workflows"))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String id = (String) resp.get("id");
        this.vectorStoreId = id;
        return id;
    }

    private String uploadFile(String filename, String contentType, byte[] bytes) {
        Map<?,?> resp = client().post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", new org.springframework.core.io.ByteArrayResource(bytes){
                    @Override public String getFilename(){ return filename; }
                }).with("purpose", "assistants"))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return (String) resp.get("id");
    }

    private void attachFilesToVectorStore(String vectorStoreId, List<String> fileIds) {
        client().post()
            .uri("/vector_stores/{id}/file_batches", vectorStoreId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("file_ids", fileIds))
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    }
}
