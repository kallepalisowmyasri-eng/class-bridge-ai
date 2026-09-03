package com.aiclassrecovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoteRequest {

    @NotBlank(message = "Note content cannot be blank")
    @Size(max = 5000, message = "Note content cannot exceed 5000 characters")
    private String content;
}
