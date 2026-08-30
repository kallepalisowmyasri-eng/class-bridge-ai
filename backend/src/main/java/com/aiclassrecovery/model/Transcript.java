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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transcripts")
public class Transcript {

    @Id
    private String id;

    /** Reference to ClassRecord */
    @Indexed(unique = true)
    private String classId;

    /** Full transcript text */
    private String fullText;

    /** Timestamped segments */
    private List<Segment> segments;

    /** Detected language */
    private String language;

    /** Audio duration in seconds (from whisper) */
    private float duration;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        /** Start time in seconds */
        private float start;

        /** End time in seconds */
        private float end;

        /** Spoken text */
        private String text;
    }
}
