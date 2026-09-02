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
            ClassRecordRepository classRecordRepository,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.transcriptRepository = transcriptRepository;
        this.classRecordRepository = classRecordRepository;
        this.objectMapper = objectMapper;
    }

    public ChatResponse chat(String userId, String classId, String question) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        List<ClassRecord> allClasses = new ArrayList<>();
        if (record.getSubjectId() != null) {
            allClasses = classRecordRepository.findByUserIdAndSubjectId(userId, record.getSubjectId())
                    .stream()
                    .filter(c -> "ANALYZED".equals(c.getStatus()) || "DONE".equals(c.getStatus()))
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return a.getCreatedAt().compareTo(b.getCreatedAt());
                    })
                    .toList();
        } else {
            allClasses.add(record);
        }

        // Filter out classes that come strictly after the current class (don't use future classes for context)
        List<ClassRecord> relevantClasses = new ArrayList<>();
        for (ClassRecord c : allClasses) {
            relevantClasses.add(c);
            if (c.getId().equals(classId)) {
                break;
            }
        }
        
        // If current class isn't found in the list (e.g., status is PROCESSING), just use current
        if (relevantClasses.stream().noneMatch(c -> c.getId().equals(classId))) {
            relevantClasses.clear();
            relevantClasses.add(record);
        }

        List<String> classIds = relevantClasses.stream().map(ClassRecord::getId).toList();
        List<Transcript> transcripts = transcriptRepository.findByClassIdIn(classIds);
        
        if (transcripts.isEmpty()) {
            throw new RuntimeException("Transcript not available for this class. Wait for processing to complete.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini API key not configured. Set GEMINI_API_KEY env var.");
        }

        // Combine segments from all transcripts
        List<ClassSegment> allSegments = new ArrayList<>();
        StringBuilder combinedFullText = new StringBuilder();

        for (ClassRecord c : relevantClasses) {
            Transcript t = transcripts.stream().filter(tr -> tr.getClassId().equals(c.getId())).findFirst().orElse(null);
            if (t != null) {
                combinedFullText.append(t.getFullText()).append("\n");
                if (t.getSegments() != null) {
                    for (Transcript.Segment seg : t.getSegments()) {
                        allSegments.add(new ClassSegment(c.getClassName(), seg));
                    }
                }
            }
        }

        String fullText = combinedFullText.toString();
        if (fullText.isBlank()) {
            throw new RuntimeException("Transcripts are empty");
        }

        // Build context: select most relevant segments across all classes
        String context = buildContext(fullText, allSegments, question);

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

    private static class ClassSegment {
        String className;
        Transcript.Segment segment;

        public ClassSegment(String className, Transcript.Segment segment) {
            this.className = className;
            this.segment = segment;
        }
    }

    private String buildContext(String fullText, List<ClassSegment> segments, String question) {
        // If total combined text is short enough, use it all
        if (fullText.length() <= MAX_CONTEXT_CHARS) {
            if (segments != null && !segments.isEmpty()) {
                return segments.stream()
                        .map(s -> String.format("[%s | %s → %s] %s",
                                s.className != null ? s.className : "Current Class",
                                formatSeconds(s.segment.getStart()),
                                formatSeconds(s.segment.getEnd()),
                                s.segment.getText()))
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

        List<ClassSegment> ranked = segments.stream()
                .sorted(Comparator.comparingInt((ClassSegment s) -> {
                    String lower = s.segment.getText().toLowerCase();
                    int score = 0;
                    for (String kw : keywords) {
                        if (lower.contains(kw)) score += 10;
                    }
                    // Boost beginning and end of class
                    if (s.segment.getStart() < 120) score += 2;
                    return -score;
                }))
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        for (ClassSegment seg : ranked) {
            String entry = String.format("[%s | %s → %s] %s",
                    seg.className != null ? seg.className : "Current Class",
                    formatSeconds(seg.segment.getStart()),
                    formatSeconds(seg.segment.getEnd()),
                    seg.segment.getText());
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
        return "You are a helpful academic assistant. Answer the student's question using ONLY the provided class transcripts below. " +
                "The transcripts may contain content from multiple classes in the same subject.\n\n" +
                "Rules:\n" +
                "1. If the information exists in neither current nor previous classes, reply EXACTLY with: 'This information was not found in the available class content.'\n" +
                "2. When referencing specific moments, clearly cite the class name and timestamps in [Class Name | MM:SS] format.\n" +
                "3. Keep answers concise but complete. Use bullet points for lists if necessary.\n" +
                "4. Do NOT invent information or answer from external knowledge.\n\n" +
                "CLASS TRANSCRIPTS:\n" + context + "\n\n" +
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
