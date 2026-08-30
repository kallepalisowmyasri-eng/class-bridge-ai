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

    public Transcript transcribe(String classId, String audioPath) {
        log.info("Starting transcription for class {} at {}", classId, audioPath);

        WhisperResponse response;
        try {
            response = webClient.post()
                    .uri("/transcribe")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("audio_path", audioPath))
                    .retrieve()
                    .bodyToMono(WhisperResponse.class)
                    .block();
        } catch (WebClientRequestException e) {
            log.error("Failed to connect to transcription service", e);
            throw new RuntimeException("Transcription service unavailable. Is it running on port 5000?");
        }

        if (response == null || response.getSegments() == null) {
            throw new RuntimeException("Empty transcription response");
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
                .fullText(response.getFull_text())
                .segments(segments)
                .duration(segments.isEmpty() ? 0 :
                        segments.get(segments.size() - 1).getEnd())
                .build();

        transcript = transcriptRepository.save(transcript);
        log.info("Transcription saved for class {}: {} segments", classId, segments.size());

        return transcript;
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
