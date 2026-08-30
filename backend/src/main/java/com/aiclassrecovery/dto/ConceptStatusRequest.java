package com.aiclassrecovery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConceptStatusRequest {

    @NotBlank(message = "Concept is required")
    private String concept;

    @NotBlank(message = "Status is required")
    private String status; // UNDERSTANDING, NEEDS_REVIEW, MASTERED
}
