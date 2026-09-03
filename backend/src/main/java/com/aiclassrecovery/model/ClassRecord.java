package com.aiclassrecovery.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a processed class recording stored in MongoDB.
 * Collection: "classes"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "classes")
public class ClassRecord {

    @Id
    private String id;

    /** Reference to Subject document */
    @Indexed
    private String subjectId;

    /** e.g. "AI & ML", "Data Structures" */
    @Indexed
    private String subject;

    /** e.g. "Class 08" */
    private String className;

    /** Optional description */
    private String description;

    /** User who uploaded the recording */
    @Indexed
    private String userId;

    /** Original file path of the uploaded recording */
    private String recordingPath;

    /** Original filename as uploaded by the user */
    private String originalFilename;

    /** Path to extracted WAV audio file */
    private String audioPath;

    /** Duration in seconds */
    private int durationSeconds;

    /** Reference to Transcript document */
    private String transcriptId;

    /** Indicates if the recording has a video stream */
    private boolean hasVideo;

    /** List of paths to extracted key slide frames */
    private List<String> slidePaths;

    /** AI-generated textual summary of what was on the slides */
    private String visualSummary;

    /** AI-generated: what was covered in this class */
    private String whatYouMissed;

    /** AI-generated: key topics taught */
    private List<String> topics;

    /** AI-generated: prioritised concepts to study first */
    private List<MustLearnItem> mustLearn;

    /** AI-generated: suggested learning sequence */
    private List<String> catchUpPath;

    /** AI-generated: important timestamps */
    private List<TimestampedItem> importantTimestamps;

    /** AI-generated: concise revision bullets */
    private List<String> revisionPoints;

    /** AI-generated: quiz questions from class content */
    private List<QuizQuestion> quizQuestions;

    /** Processing status: PENDING | UPLOADED | PROCESSING | AUDIO_READY | TRANSCRIBED | ANALYZED | FAILED */
    private String status;

    /** Human-readable reason for the last failure (null when not failed) */
    private String failureReason;

    /** Number of processing attempts made (for retry tracking) */
    private int retryCount;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ── Nested types ────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MustLearnItem {
        /** CRITICAL | HIGH | MEDIUM */
        private String priority;
        private String concept;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimestampedItem {
        /** e.g. "01:12:43" */
        private String timestamp;
        /** EXPLANATION | ASSIGNMENT | ANNOUNCEMENT | EXAMPLE */
        private String type;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizQuestion {
        private String question;
        private List<String> options;
        /** Index of the correct option (0-based) */
        private int correctIndex;
        private String sourceTimestamp;
    }
}
