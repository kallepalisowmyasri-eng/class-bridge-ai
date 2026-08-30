package com.aiclassrecovery.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultRequest {

    @NotNull(message = "Results are required")
    private Map<Integer, Boolean> results;
}
