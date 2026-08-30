package com.aiclassrecovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubjectRequest {

    @NotBlank(message = "Subject name is required")
    @Size(max = 100, message = "Subject name must be under 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;
}
