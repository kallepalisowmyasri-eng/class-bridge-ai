package com.aiclassrecovery.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.aiclassrecovery.dto.GeminiAnalysisResult;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.TranscriptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final int MAX_CHUNK_CHARS = 30000;

    private final WebClient webClient;
    private final String apiUrl;
    private final String apiKey;
    private final TranscriptRepository transcriptRepository;
    private final ClassRecordRepository classRecordRepository;
    private final ObjectMapper objectMapper;

    public GeminiService(
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

    public ClassRecord analyze(String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        Transcript transcript = transcriptRepository.findByClassId(classId)
                .orElseThrow(() -> new RuntimeException("Transcript not found"));

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Gemini API key not configured. Set GEMINI_API_KEY env var.");
        }

        StringBuilder sb = new StringBuilder();
        if (transcript.getSegments() != null && !transcript.getSegments().isEmpty()) {
            for (Transcript.Segment seg : transcript.getSegments()) {
                int min = (int) (seg.getStart() / 60);
                int sec = (int) (seg.getStart() % 60);
                sb.append(String.format("[%02d:%02d] %s\n", min, sec, seg.getText()));
            }
        } else {
            sb.append(transcript.getFullText());
        }
        String fullText = sb.toString();

        if (fullText.isBlank()) {
            throw new RuntimeException("Transcript is empty");
        }

        List<String> chunks = splitTranscript(fullText);
        StringBuilder combinedAnalysis = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("Analyzing chunk {}/{} for class {}", i + 1, chunks.size(), classId);

            String prompt = buildPrompt(chunk, chunks.size() > 1 ? (i + 1) : null, chunks.size());
            String response = callGemini(prompt);
            combinedAnalysis.append(response).append("\n\n---\n\n");
        }

        String finalAnalysis;
        if (chunks.size() > 1) {
            String mergePrompt = buildMergePrompt(combinedAnalysis.toString());
            finalAnalysis = callGemini(mergePrompt);
        } else {
            finalAnalysis = combinedAnalysis.toString().trim();
        }

        GeminiAnalysisResult result = parseAnalysis(finalAnalysis);
        applyAnalysis(record, result);

        record.setStatus("ANALYZED");
        classRecordRepository.save(record);

        log.info("Analysis complete for class {}: {} topics, {} quiz questions",
                classId, result.getTopics() != null ? result.getTopics().size() : 0,
                result.getQuizQuestions() != null ? result.getQuizQuestions().size() : 0);

        return record;
    }

    private String buildPrompt(String transcriptText, Integer chunkNumber, int totalChunks) {
        String chunkInfo = chunkNumber != null
                ? "\nThis is chunk " + chunkNumber + " of " + totalChunks + ". Analyze only this portion."
                : "";

        return "You are an expert academic analyst. Analyze this class transcript and return ONLY a valid JSON object with no markdown, no code fences, no explanation." + chunkInfo + "\n\n" +
                "The JSON must have exactly these fields:\n" +
                "{\n" +
                "  \"summary\": \"2-3 sentence overview of what was taught\",\n" +
                "  \"topics\": [\"topic1\", \"topic2\"],\n" +
                "  \"mustLearn\": [{\"priority\": \"CRITICAL|HIGH|MEDIUM\", \"concept\": \"concept name\"}],\n" +
                "  \"catchUpPath\": [\"step 1: ...\", \"step 2: ...\"],\n" +
                "  \"importantTimestamps\": [{\"timestamp\": \"MM:SS or HH:MM:SS\", \"type\": \"EXPLANATION|ASSIGNMENT|ANNOUNCEMENT|EXAMPLE\", \"description\": \"what happened\"}],\n" +
                "  \"revisionPoints\": [\"bullet point 1\", \"bullet point 2\"],\n" +
                "  \"quizQuestions\": [{\"question\": \"...\", \"options\": [\"first option text\", \"second option text\", \"third option text\", \"fourth option text\"], \"correctIndex\": 0, \"sourceTimestamp\": \"MM:SS\"}]\n" +
                "}\n\n" +
                "Rules:\n" +
                "- All quiz questions must be answerable ONLY from the transcript content\n" +
                "- Include at least 5 quiz questions if the transcript is long enough\n" +
                "- Do NOT prefix options with letters (e.g. 'A)', 'B)') or numbers. Provide just the text.\n" +
                "- \"priority\" must be exactly CRITICAL, HIGH, or MEDIUM\n" +
                "- \"type\" must be exactly EXPLANATION, ASSIGNMENT, ANNOUNCEMENT, or EXAMPLE\n" +
                "- DO NOT invent timestamps. If a timestamp is not available, leave the timestamp field empty or omit the item.\n" +
                "- All extracted items MUST be grounded in the text.\n" +
                "- Return ONLY the JSON, no other text\n\n" +
                "TRANSCRIPT:\n" + transcriptText;
    }

    private String buildMergePrompt(String combinedChunks) {
        return "You received multiple analysis chunks from the same class transcript. Merge them into ONE clean JSON object.\n\n" +
                "Rules:\n" +
                "- Combine and deduplicate topics\n" +
                "- Merge revision points\n" +
                "- Keep at most 8 quiz questions (pick the best ones)\n" +
                "- Merge mustLearn items, keeping highest priority\n" +
                "- Merge catchUpPath sequentially\n" +
                "- Merge importantTimestamps chronologically\n" +
                "- DO NOT invent timestamps. If a timestamp is not available, leave the timestamp field empty or omit the item.\n" +
                "- Provide a unified summary\n" +
                "- Do NOT prefix quiz options with letters (e.g. 'A)', 'B)') or numbers. Provide just the text.\n" +
                "- Return ONLY valid JSON, no markdown, no code fences\n\n" +
                "JSON schema:\n" +
                "{\n" +
                "  \"summary\": \"...\",\n" +
                "  \"topics\": [...],\n" +
                "  \"mustLearn\": [{\"priority\": \"CRITICAL|HIGH|MEDIUM\", \"concept\": \"...\"}],\n" +
                "  \"catchUpPath\": [...],\n" +
                "  \"importantTimestamps\": [{\"timestamp\": \"...\", \"type\": \"...\", \"description\": \"...\"}],\n" +
                "  \"revisionPoints\": [...],\n" +
                "  \"quizQuestions\": [{\"question\": \"...\", \"options\": [...], \"correctIndex\": 0, \"sourceTimestamp\": \"...\"}]\n" +
                "}\n\n" +
                "ANALYSIS CHUNKS:\n" + combinedChunks;
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 8192
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
                // Check for error response
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

    private GeminiAnalysisResult parseAnalysis(String json) {
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
            }
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1);
            }
            return objectMapper.readValue(cleaned, GeminiAnalysisResult.class);
        } catch (Exception e) {
            log.error("Failed to parse analysis JSON: {}", json, e);
            throw new RuntimeException("Failed to parse AI analysis result: " + e.getMessage());
        }
    }

    private void applyAnalysis(ClassRecord record, GeminiAnalysisResult result) {
        if (result.getSummary() != null) record.setWhatYouMissed(result.getSummary());
        if (result.getTopics() != null) record.setTopics(result.getTopics());

        if (result.getMustLearn() != null) {
            record.setMustLearn(result.getMustLearn().stream()
                    .map(m -> ClassRecord.MustLearnItem.builder()
                            .priority(m.getPriority())
                            .concept(m.getConcept())
                            .build())
                    .toList());
        }

        if (result.getCatchUpPath() != null) record.setCatchUpPath(result.getCatchUpPath());

        if (result.getImportantTimestamps() != null) {
            record.setImportantTimestamps(result.getImportantTimestamps().stream()
                    .map(t -> ClassRecord.TimestampedItem.builder()
                            .timestamp(t.getTimestamp())
                            .type(t.getType())
                            .description(t.getDescription())
                            .build())
                    .toList());
        }

        if (result.getRevisionPoints() != null) record.setRevisionPoints(result.getRevisionPoints());

        if (result.getQuizQuestions() != null) {
            record.setQuizQuestions(result.getQuizQuestions().stream()
                    .map(q -> ClassRecord.QuizQuestion.builder()
                            .question(q.getQuestion())
                            .options(q.getOptions())
                            .correctIndex(q.getCorrectIndex())
                            .sourceTimestamp(q.getSourceTimestamp())
                            .build())
                    .toList());
        }
    }

    private List<String> splitTranscript(String text) {
        if (text.length() <= MAX_CHUNK_CHARS) {
            return List.of(text);
        }

        var chunks = new java.util.ArrayList<String>();
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() + 2 > MAX_CHUNK_CHARS) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                if (para.length() > MAX_CHUNK_CHARS) {
                    String[] sentences = para.split("(?<=[.!?])\\s+");
                    for (String sentence : sentences) {
                        if (current.length() + sentence.length() + 1 > MAX_CHUNK_CHARS) {
                            if (!current.isEmpty()) {
                                chunks.add(current.toString());
                                current = new StringBuilder();
                            }
                        }
                        current.append(sentence).append(" ");
                    }
                } else {
                    current.append(para).append("\n\n");
                }
            } else {
                current.append(para).append("\n\n");
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
