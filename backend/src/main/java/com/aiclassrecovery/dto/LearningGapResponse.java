package com.aiclassrecovery.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningGapResponse {

    private String subjectName;
    private String className;
    private int totalClassesAnalyzed;
    private List<String> topicsCoveredInSubject;
    private List<String> topicsInCurrentClass;
    private List<String> missingTopics;
    private List<String> prerequisiteTopics;
    private List<GapRecommendation> recommendations;
    private String overallAssessment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GapRecommendation {
        private String topic;
        private String reason;
        private String priority; // CRITICAL, HIGH, MEDIUM
        private String suggestedAction; // REVIEW, STUDY, PRACTICE
    }
}
