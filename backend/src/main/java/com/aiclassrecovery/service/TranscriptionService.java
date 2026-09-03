package com.aiclassrecovery.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.aiclassrecovery.dto.WhisperResponse;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.TranscriptRepository;

@Service
public class TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final TranscriptRepository transcriptRepository;
    private final WebClient webClient;

    public TranscriptionService(TranscriptRepository transcriptRepository,
                                @Value("${transcription.service.url:http://localhost:5000}") String serviceUrl) {
        this.transcriptRepository = transcriptRepository;
        this.webClient = WebClient.builder()
                .baseUrl(serviceUrl)
                .build();
    }

    private static final int WHISPER_MAX_RETRIES = 3;
    private static final long WHISPER_RETRY_BASE_MS = 3000;

    public Transcript transcribe(String classId, String audioPath) {
        log.info("Starting transcription for class {} at {}", classId, audioPath);

        Exception lastException = null;

        for (int attempt = 1; attempt <= WHISPER_MAX_RETRIES; attempt++) {
            try {
                WhisperResponse response = webClient.post()
                        .uri("/transcribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("audio_path", audioPath))
                        .retrieve()
                        .onStatus(
                            status -> status.value() >= 500,
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Whisper HTTP " + clientResponse.statusCode().value() + ": " + body))
                        )
                        .bodyToMono(WhisperResponse.class)
                        .block();

                if (response == null || response.getSegments() == null) {
                    throw new RuntimeException("Empty transcription response from Whisper service");
                }

                var segments = response.getSegments().stream()
                        .map(s -> Transcript.Segment.builder()
                                .start(s.getStart())
                                .end(s.getEnd())
                                .text(s.getText())
                                .build())
                        .toList();

                Transcript transcript = Transcript.builder()
                        .classId(classId)
                        .fullText(response.getFullText())
                        .segments(segments)
                        .duration(segments.isEmpty() ? 0 :
                                segments.get(segments.size() - 1).getEnd())
                        .build();

                transcript = transcriptRepository.save(transcript);
                log.info("Transcription saved for class {}: {} segments", classId, segments.size());
                return transcript;

            } catch (WebClientRequestException e) {
                lastException = e;
                if (attempt == WHISPER_MAX_RETRIES) {
                    log.error("Whisper service unreachable (attempt {}/{})", attempt, WHISPER_MAX_RETRIES, e);
                    throw new RuntimeException(
                        "Transcription service unavailable after " + attempt + " attempt(s). Is it running on port 5000?", e);
                }
                long waitMs = WHISPER_RETRY_BASE_MS * (long) Math.pow(2, attempt - 1);
                log.warn("Whisper service unreachable (attempt {}/{}), retrying in {}ms", attempt, WHISPER_MAX_RETRIES, waitMs);
                try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt == WHISPER_MAX_RETRIES) {
                    log.error("Transcription failed (attempt {}/{}): {}", attempt, WHISPER_MAX_RETRIES, e.getMessage());
                    throw new RuntimeException("Transcription failed after " + attempt + " attempt(s): " + e.getMessage(), e);
                }
                long waitMs = WHISPER_RETRY_BASE_MS * (long) Math.pow(2, attempt - 1);
                log.warn("Transcription failed (attempt {}/{}), retrying in {}ms: {}", attempt, WHISPER_MAX_RETRIES, waitMs, e.getMessage());
                try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new RuntimeException("Transcription failed: " + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    public Transcript getByClassId(String classId) {
        return transcriptRepository.findByClassId(classId)
                .orElseThrow(() -> new RuntimeException("Transcript not found for class: " + classId));
    }

    public void deleteByClassId(String classId) {
        transcriptRepository.findByClassId(classId)
                .ifPresent(transcriptRepository::delete);
    }
}
