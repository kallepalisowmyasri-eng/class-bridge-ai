package com.aiclassrecovery.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.aiclassrecovery.dto.LearningGapResponse;
import com.aiclassrecovery.dto.LearningGapResponse.GapRecommendation;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LearningGapService {

    private static final Logger log = LoggerFactory.getLogger(LearningGapService.class);

    private final WebClient webClient;
    private final String apiUrl;
    private final String apiKey;
    private final ClassRecordRepository classRecordRepository;
    private final ObjectMapper objectMapper;

    public LearningGapService(
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}") String apiUrl,
            @Value("${gemini.api.key:}") String apiKey,
            ClassRecordRepository classRecordRepository,
            ObjectMapper objectMapper) {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.classRecordRepository = classRecordRepository;
        this.objectMapper = objectMapper;
    }

    public LearningGapResponse analyzeGaps(String userId, String classId) {
        ClassRecord currentClass = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(currentClass.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        if (currentClass.getSubjectId() == null) {
            throw new RuntimeException("Class has no associated subject");
        }

        // Get all analyzed classes for this subject
        List<ClassRecord> allClasses = classRecordRepository.findByUserIdAndSubjectId(userId, currentClass.getSubjectId())
                .stream()
                .filter(c -> "ANALYZED".equals(c.getStatus()) || "DONE".equals(c.getStatus()))
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
                .toList();

        if (allClasses.size() < 2) {
            return LearningGapResponse.builder()
                    .subjectName(currentClass.getSubject())
                    .className(currentClass.getClassName())
                    .totalClassesAnalyzed(allClasses.size())
                    .topicsCoveredInSubject(currentClass.getTopics() != null ? currentClass.getTopics() : List.of())
                    .topicsInCurrentClass(currentClass.getTopics() != null ? currentClass.getTopics() : List.of())
                    .missingTopics(List.of())
                    .prerequisiteTopics(List.of())
                    .recommendations(List.of())
                    .overallAssessment("Only " + allClasses.size() + " class(es) analyzed in this subject. Upload more classes to enable gap analysis.")
                    .build();
        }

        // Collect all topics from earlier classes (before current)
        List<String> earlierTopics = new ArrayList<>();
        List<String> currentTopics = currentClass.getTopics() != null ? currentClass.getTopics() : List.of();
        boolean foundCurrent = false;

        for (ClassRecord cls : allClasses) {
            if (cls.getId().equals(classId)) {
                foundCurrent = true;
                continue;
            }
            if (!foundCurrent && cls.getTopics() != null) {
                earlierTopics.addAll(cls.getTopics());
            }
        }

        // If current class is not in the sorted list (shouldn't happen), use all except current
        if (!foundCurrent) {
            for (ClassRecord cls : allClasses) {
                if (!cls.getId().equals(classId) && cls.getTopics() != null) {
                    earlierTopics.addAll(cls.getTopics());
                }
            }
        }

        // Deduplicate
        Set<String> allSubjectTopics = new LinkedHashSet<>(earlierTopics);
        allSubjectTopics.addAll(currentTopics);

        // Find missing: topics from earlier classes NOT in current class
        Set<String> missing = new LinkedHashSet<>(earlierTopics);
        missing.removeAll(currentTopics);

        // Use AI to generate gap analysis and recommendations
        if (apiKey != null && !apiKey.isBlank() && !missing.isEmpty()) {
            try {
                return generateAiGapAnalysis(currentClass, allClasses, earlierTopics, currentTopics,
                        new ArrayList<>(missing), new ArrayList<>(allSubjectTopics));
            } catch (Exception e) {
                log.warn("AI gap analysis failed, falling back to basic analysis: {}", e.getMessage());
            }
        }

        // Fallback: basic gap analysis without AI
        return buildBasicGapAnalysis(currentClass, allClasses, earlierTopics, currentTopics,
                new ArrayList<>(missing), new ArrayList<>(allSubjectTopics));
    }

    private LearningGapResponse generateAiGapAnalysis(ClassRecord currentClass, List<ClassRecord> allClasses,
                                                       List<String> earlierTopics, List<String> currentTopics,
                                                       List<String> missingTopics, List<String> allSubjectTopics) {
        StringBuilder classInfo = new StringBuilder();
        for (ClassRecord cls : allClasses) {
            String marker = cls.getId().equals(currentClass.getId()) ? " (CURRENT CLASS)" : "";
            classInfo.append(String.format("Class: %s%s\nTopics: %s\nMust Learn: %s\n\n",
                    cls.getClassName(), marker,
                    cls.getTopics() != null ? String.join(", ", cls.getTopics()) : "none",
                    cls.getMustLearn() != null ? cls.getMustLearn().stream()
                            .map(m -> m.getConcept() + " [" + m.getPriority() + "]")
                            .collect(Collectors.joining(", ")) : "none"));
        }

        String prompt = "You are an academic learning analyst. Analyze the learning gaps for a student based on class data.\n\n" +
                "SUBJECT: " + currentClass.getSubject() + "\n" +
                "CURRENT CLASS: " + currentClass.getClassName() + "\n\n" +
                "ALL CLASSES IN THIS SUBJECT:\n" + classInfo + "\n" +
                "TOPICS FROM EARLIER CLASSES: " + String.join(", ", earlierTopics) + "\n" +
                "TOPICS IN CURRENT CLASS: " + String.join(", ", currentTopics) + "\n" +
                "MISSING TOPICS (in earlier but not current): " + String.join(", ", missingTopics) + "\n\n" +
                "Return ONLY a valid JSON object:\n" +
                "{\n" +
                "  \"missingTopics\": [\"topic1\", \"topic2\"],\n" +
                "  \"prerequisiteTopics\": [\"topics the student should review before current class\"],\n" +
                "  \"recommendations\": [{\"topic\": \"...\", \"reason\": \"why it's important\", \"priority\": \"CRITICAL|HIGH|MEDIUM\", \"suggestedAction\": \"REVIEW|STUDY|PRACTICE\"}],\n" +
                "  \"overallAssessment\": \"2-3 sentence summary of learning gaps and what to focus on\"\n" +
                "}\n\n" +
                "Rules:\n" +
                "- priority: CRITICAL = must know before current class, HIGH = strongly recommended, MEDIUM = nice to have\n" +
                "- suggestedAction: REVIEW = quick recap, STUDY = deeper learning, PRACTICE = do exercises\n" +
                "- Focus on concepts that directly impact understanding the current class\n" +
                "- Return ONLY the JSON, no markdown, no code fences";

        String response = callGemini(prompt);
        return parseAiResponse(response, currentClass, allClasses.size(), earlierTopics, currentTopics, missingTopics, allSubjectTopics);
    }

    private LearningGapResponse parseAiResponse(String json, ClassRecord currentClass, int totalClasses,
                                                 List<String> earlierTopics, List<String> currentTopics,
                                                 List<String> missingTopics, List<String> allSubjectTopics) {
        try {
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
            }

            JsonNode root = objectMapper.readTree(cleaned);

            List<String> parsedMissing = new ArrayList<>();
            root.path("missingTopics").forEach(n -> parsedMissing.add(n.asText()));

            List<String> parsedPrereqs = new ArrayList<>();
            root.path("prerequisiteTopics").forEach(n -> parsedPrereqs.add(n.asText()));

            List<GapRecommendation> recommendations = new ArrayList<>();
            root.path("recommendations").forEach(n -> {
                recommendations.add(GapRecommendation.builder()
                        .topic(n.path("topic").asText())
                        .reason(n.path("reason").asText())
                        .priority(n.path("priority").asText())
                        .suggestedAction(n.path("suggestedAction").asText())
                        .build());
            });

            return LearningGapResponse.builder()
                    .subjectName(currentClass.getSubject())
                    .className(currentClass.getClassName())
                    .totalClassesAnalyzed(totalClasses)
                    .topicsCoveredInSubject(allSubjectTopics)
                    .topicsInCurrentClass(currentTopics)
                    .missingTopics(parsedMissing.isEmpty() ? missingTopics : parsedMissing)
                    .prerequisiteTopics(parsedPrereqs)
                    .recommendations(recommendations)
                    .overallAssessment(root.path("overallAssessment").asText("Analysis complete."))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI gap analysis", e);
            return buildBasicGapAnalysis(currentClass, null, earlierTopics, currentTopics, missingTopics, allSubjectTopics);
        }
    }

    private LearningGapResponse buildBasicGapAnalysis(ClassRecord currentClass, List<ClassRecord> allClasses,
                                                       List<String> earlierTopics, List<String> currentTopics,
                                                       List<String> missingTopics, List<String> allSubjectTopics) {
        List<GapRecommendation> recommendations = missingTopics.stream()
                .map(topic -> GapRecommendation.builder()
                        .topic(topic)
                        .reason("Covered in earlier classes but not in " + currentClass.getClassName())
                        .priority("HIGH")
                        .suggestedAction("REVIEW")
                        .build())
                .toList();

        String assessment = missingTopics.isEmpty()
                ? "No learning gaps detected. The current class covers all previously introduced topics."
                : missingTopics.size() + " topic(s) from earlier classes are missing from " + currentClass.getClassName()
                + ". Review these to strengthen your foundation.";

        return LearningGapResponse.builder()
                .subjectName(currentClass.getSubject())
                .className(currentClass.getClassName())
                .totalClassesAnalyzed(allClasses != null ? allClasses.size() : 0)
                .topicsCoveredInSubject(allSubjectTopics)
                .topicsInCurrentClass(currentTopics)
                .missingTopics(missingTopics)
                .prerequisiteTopics(List.of())
                .recommendations(recommendations)
                .overallAssessment(assessment)
                .build();
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.3,
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
}
