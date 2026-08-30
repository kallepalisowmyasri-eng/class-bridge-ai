package com.aiclassrecovery.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponse {

    private String id;
    private String name;
    private String description;
    private int classCount;
    private Instant createdAt;
}
