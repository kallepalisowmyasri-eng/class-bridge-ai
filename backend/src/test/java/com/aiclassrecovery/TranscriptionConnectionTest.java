package com.aiclassrecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.aiclassrecovery.dto.WhisperResponse;

public class TranscriptionConnectionTest {

    private final String baseUrl = "http://localhost:5000";
    private final WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    @Test
    void testWhisperHealthEndpoint() {
        @SuppressWarnings("unchecked")
        Map<String, Object> health = webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        assertNotNull(health, "Health response should not be null");
        assertEquals("UP", health.get("status"));
        assertEquals(Boolean.TRUE, health.get("model_loaded"));
    }

    @Test
    void testWhisperTranscribeWithRealAudio() {
        File audioFile = new File("../speech.wav");
        assertTrue(audioFile.exists(), "speech.wav must exist in project root");

        WhisperResponse response = webClient.post()
                .uri("/transcribe")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("audio_path", audioFile.getAbsolutePath()))
                .retrieve()
                .bodyToMono(WhisperResponse.class)
                .block();

        assertNotNull(response, "Transcription response should not be null");
        assertNotNull(response.getSegments(), "Segments should not be null");
        assertFalse(response.getSegments().isEmpty(), "Segments should not be empty");
        assertNotNull(response.getFullText(), "Full text should not be null");
        assertTrue(response.getFullText().contains("Classbridge AI"), "Full text should contain transcribed content");
    }
}
