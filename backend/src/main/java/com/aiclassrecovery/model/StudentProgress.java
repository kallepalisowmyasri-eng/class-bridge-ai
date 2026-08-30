package com.aiclassrecovery.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks a student's learning progress per concept per class.
 * Collection: "student_progress"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "student_progress")
@CompoundIndex(name = "user_class_idx", def = "{'userId': 1, 'classId': 1}", unique = true)
public class StudentProgress {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String classId;

    private String subjectId;

    /** concept -> status: UNDERSTANDING, NEEDS_REVIEW, MASTERED */
    private Map<String, ConceptStatus> conceptStatuses;

    /** Quiz attempt results: questionIndex -> correct (boolean) */
    private Map<Integer, Boolean> quizResults;

    /** Overall progress percentage for this class (0-100) */
    private int progressPercent;

    /** Total quiz questions attempted */
    private int quizAttempted;

    /** Quiz score percentage (0-100) */
    private int quizScorePercent;

    /** Timestamp of last activity */
    private Instant lastActivityAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConceptStatus {
        /** UNDERSTANDING | NEEDS_REVIEW | MASTERED */
        private String status;
        private Instant markedAt;
    }
}
