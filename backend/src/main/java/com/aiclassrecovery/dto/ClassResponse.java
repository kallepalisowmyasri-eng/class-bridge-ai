package com.aiclassrecovery.dto;

import java.time.Instant;
import java.util.List;

import com.aiclassrecovery.model.ClassRecord;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassResponse {

    private String id;
    private String subjectId;
    private String subject;
    private String className;
    private String description;

    @JsonIgnore
    private String userId;

    @JsonIgnore
    private String recordingPath;

    private String originalFilename;

    @JsonIgnore
    private String audioPath;

    private int durationSeconds;
    private String transcriptId;
    private String status;

    private String whatYouMissed;
    private List<String> topics;
    private List<ClassRecord.MustLearnItem> mustLearn;
    private List<String> catchUpPath;
    private List<ClassRecord.TimestampedItem> importantTimestamps;
    private List<String> revisionPoints;
    private List<ClassRecord.QuizQuestion> quizQuestions;

    private Instant createdAt;
    private Instant updatedAt;
}
