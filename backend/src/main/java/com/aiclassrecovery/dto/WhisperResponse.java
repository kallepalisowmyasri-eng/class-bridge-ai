package com.aiclassrecovery.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhisperResponse {

    private List<Segment> segments;

    @JsonProperty("full_text")
    private String fullText;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Segment {
        private float start;
        private float end;
        private String text;
    }
}
