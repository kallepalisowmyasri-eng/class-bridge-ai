package com.aiclassrecovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassRequest {

    @NotBlank(message = "Subject ID is required")
    private String subjectId;

    @NotBlank(message = "Class name is required")
    @Size(max = 200, message = "Class name must be under 200 characters")
    private String className;

    @Size(max = 2000, message = "Description must be under 2000 characters")
    private String description;
}
