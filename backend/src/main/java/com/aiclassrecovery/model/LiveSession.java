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
 * Represents an active or ended live recording session.
 * Audio is captured chunk-by-chunk from the browser mic and transcribed in near-real-time.
 * When the session ends, it is converted into a full ClassRecord via the existing pipeline.
 *
 * Collection: "live_sessions"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "live_sessions")
public class LiveSession {

    @Id
    private String id;

    /** Owner of this session */
    @Indexed
    private String userId;

    /** Subject this session belongs to */
    @Indexed
    private String subjectId;

    /** Subject display name */
    private String subject;

    /** Name of the session, e.g. "Class 09 — Live" */
    private String sessionName;

    /**
     * Lifecycle status:
     * LIVE     — session is active, chunks arriving
     * ENDED    — session was stopped; ClassRecord pipeline may be running
     * FAILED   — something went wrong during the session
     */
    private String status;

    /** Human-readable failure reason (null when not FAILED) */
    private String failureReason;

    /** Directory where temp audio chunks are stored while session is LIVE */
    private String tempAudioDir;

    /** Path to the merged full audio file produced when session ends */
    private String mergedAudioPath;

    /**
     * If the session was successfully converted to a ClassRecord this field
     * holds the resulting class ID.
     */
    private String resultClassId;

    /** Running transcript: each element is one chunk's text */
    @Builder.Default
    private List<String> liveTranscript = new java.util.ArrayList<>();

    /** AI-detected topics so far (updated per chunk) */
    @Builder.Default
    private List<String> liveTopics = new java.util.ArrayList<>();

    /** AI-detected key points so far (updated per chunk) */
    @Builder.Default
    private List<String> liveKeyPoints = new java.util.ArrayList<>();

    /** How many audio chunks have been received */
    private int chunkCount;

    /** Total approximate duration in seconds so far (chunk_count * chunk_interval) */
    private int approximateDurationSeconds;

    @CreatedDate
    private Instant startedAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Set when the session is ended by the user */
    private Instant endedAt;
}
