package com.aiclassrecovery.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {

    private String classId;
    private Map<String, String> conceptStatuses; // concept -> status
    private int progressPercent;
    private int quizAttempted;
    private int quizScorePercent;
    private Map<Integer, Boolean> quizResults;
}
