package com.aiclassrecovery.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiAnalysisResult {

    private String summary;
    private List<String> topics;
    private List<MustLearnConcept> mustLearn;
    private List<String> catchUpPath;
    private List<TimestampedItem> importantTimestamps;
    private List<String> revisionPoints;
    private List<QuizQuestion> quizQuestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MustLearnConcept {
        private String priority;
        private String concept;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimestampedItem {
        private String timestamp;
        private String type;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizQuestion {
        private String question;
        private List<String> options;
        private int correctIndex;
        private String sourceTimestamp;
    }
}
