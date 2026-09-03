package com.aiclassrecovery.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A personal note written by a student for a specific class.
 * Collection: "notes"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notes")
@CompoundIndex(name = "user_class_idx", def = "{'userId': 1, 'classId': 1}")
public class Note {

    @Id
    private String id;

    /** Reference to the ClassRecord this note belongs to */
    @Indexed
    private String classId;

    /** Owner of this note */
    @Indexed
    private String userId;

    /** The note content (plain text) */
    private String content;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
