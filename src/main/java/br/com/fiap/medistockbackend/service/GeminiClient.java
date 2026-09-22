package br.com.fiap.medistockbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;


@Service
public class GeminiClient {

    private static final String URL_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    @Value("${medistock.gemini.api-key:}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String gerarTexto(String prompt) {
        if (!configurado()) {
            return null;
        }

        try {
            String corpoRequisicao = objectMapper.writeValueAsString(
                    new GeminiRequest(new Content[]{
                            new Content(new Part[]{new Part(prompt)})
                    })
            );

            String resposta = restClient.post()
                    .uri(URL_BASE + "?key=" + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(corpoRequisicao)
                    .retrieve()
                    .body(String.class);

            return extrairTexto(resposta);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extrairTexto(String jsonResposta) {
        try {
            JsonNode raiz = objectMapper.readTree(jsonResposta);
            return raiz.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private record GeminiRequest(Content[] contents) {}
    private record Content(Part[] parts) {}
    private record Part(String text) {}
}
