package com.aiclassrecovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message must be under 5000 characters")
    private String message;
}
