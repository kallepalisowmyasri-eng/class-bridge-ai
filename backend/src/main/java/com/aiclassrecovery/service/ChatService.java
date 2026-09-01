package com.aiclassrecovery.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.aiclassrecovery.dto.ChatResponse;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.TranscriptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_CONTEXT_CHARS = 25000;

    private final WebClient webClient;
    private final String apiUrl;
    private final String apiKey;
    private final TranscriptRepository transcriptRepository;
    private final ClassRecordRepository classRecordRepository;
    private final ObjectMapper objectMapper;

    public ChatService(
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}") String apiUrl,
            @Value("${gemini.api.key:}") String apiKey,
            TranscriptRepository transcriptRepository,
            ClassRecordRepository classRecordRepository) {
        this.webClient = WebClient.builder().build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.transcriptRepository = transcriptRepository;
        this.classRecordRepository = classRecordRepository;
        this.objectMapper = new ObjectMapper();
    }

    public ChatResponse chat(String userId, String classId, String question) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        Transcript transcript = transcriptRepository.findByClassId(classId)
                .orElseThrow(() -> new RuntimeException("Transcript not available for this class. Wait for processing to complete."));

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini API key not configured. Set GEMINI_API_KEY env var.");
        }

        String fullText = transcript.getFullText();
        if (fullText == null || fullText.isBlank()) {
            throw new RuntimeException("Transcript is empty");
        }

        // Build context: select most relevant segments
        String context = buildContext(fullText, transcript.getSegments(), question);

        // Ask Gemini
        String prompt = buildChatPrompt(context, question);
        String answer = callGemini(prompt);

        // Extract timestamps from answer
        List<String> timestamps = extractTimestamps(answer);

        return ChatResponse.builder()
                .answer(answer)
                .sourceTimestamps(timestamps)
                .className(record.getClassName())
                .build();
    }

    private String buildContext(String fullText, List<Transcript.Segment> segments, String question) {
        // If transcript is short enough, use it all
        if (fullText.length() <= MAX_CONTEXT_CHARS) {
            if (segments != null && !segments.isEmpty()) {
                return segments.stream()
                        .map(s -> String.format("[%s → %s] %s",
                                formatSeconds(s.getStart()),
                                formatSeconds(s.getEnd()),
                                s.getText()))
                        .collect(Collectors.joining("\n"));
            }
            return fullText;
        }

        // For long transcripts: rank segments by keyword relevance
        if (segments == null || segments.isEmpty()) {
            // Fall back to chunked text
            return fullText.substring(0, MAX_CONTEXT_CHARS);
        }

        String[] keywords = extractKeywords(question);

        List<Transcript.Segment> ranked = segments.stream()
                .sorted(Comparator.comparingInt((Transcript.Segment s) -> {
                    String lower = s.getText().toLowerCase();
                    int score = 0;
                    for (String kw : keywords) {
                        if (lower.contains(kw)) score += 10;
                    }
                    // Boost beginning and end of class
                    if (s.getStart() < 120) score += 2;
                    return -score;
                }))
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        for (Transcript.Segment seg : ranked) {
            String entry = String.format("[%s → %s] %s",
                    formatSeconds(seg.getStart()),
                    formatSeconds(seg.getEnd()),
                    seg.getText());
            if (context.length() + entry.length() + 1 > MAX_CONTEXT_CHARS) break;
            context.append(entry).append("\n");
        }

        return context.toString();
    }

    private String[] extractKeywords(String query) {
        String cleaned = query.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String[] stopwords = {"what", "is", "are", "the", "a", "an", "was", "were", "how",
                "why", "when", "where", "who", "which", "this", "that", "these", "those",
                "can", "could", "would", "should", "will", "do", "does", "did", "have",
                "has", "had", "be", "been", "being", "to", "of", "in", "for", "on", "with",
                "at", "by", "from", "as", "into", "about", "tell", "explain", "give", "me",
                "summarize", "describe", "list", "name", "define"};

        return java.util.Arrays.stream(cleaned.split(" "))
                .filter(w -> w.length() > 2)
                .filter(w -> !java.util.List.of(stopwords).contains(w))
                .toArray(String[]::new);
    }

    private String buildChatPrompt(String context, String question) {
        return "You are a helpful academic assistant. Answer the student's question about this class using ONLY the transcript provided below. " +
                "If the transcript doesn't contain enough information to answer, say so honestly.\n\n" +
                "When referencing specific moments, include timestamps in [MM:SS] or [HH:MM:SS] format.\n" +
                "Keep answers concise but complete. Use bullet points for lists.\n\n" +
                "CLASS TRANSCRIPT:\n" + context + "\n\n" +
                "STUDENT QUESTION: " + question + "\n\n" +
                "ANSWER:";
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "maxOutputTokens", 2048
                )
        );

        try {
            String url = apiUrl + "?key=" + apiKey;
            String responseStr = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseStr == null || responseStr.isBlank()) {
                throw new RuntimeException("Gemini API returned empty response body");
            }

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || textNode.isNull()) {
                JsonNode errorNode = root.path("error");
                if (!errorNode.isMissingNode()) {
                    throw new RuntimeException("Gemini API error: " + errorNode.path("message").asText("unknown error"));
                }
                throw new RuntimeException("Gemini API returned empty response");
            }

            return textNode.asText();

        } catch (WebClientRequestException e) {
            log.error("Failed to call Gemini API", e);
            throw new RuntimeException("Gemini API unavailable: " + e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response", e);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private List<String> extractTimestamps(String text) {
        List<String> timestamps = new ArrayList<>();
        Pattern p = Pattern.compile("[\\[\\(]?(\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b)[\\]\\)]?");
        Matcher m = p.matcher(text);
        while (m.find()) {
            timestamps.add(m.group(1));
        }
        return timestamps.stream().distinct().toList();
    }

    private String formatSeconds(float seconds) {
        int totalSeconds = (int) seconds;
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }
}
