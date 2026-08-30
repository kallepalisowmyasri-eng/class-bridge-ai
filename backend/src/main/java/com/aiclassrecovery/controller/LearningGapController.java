package com.aiclassrecovery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.LearningGapResponse;
import com.aiclassrecovery.service.LearningGapService;

@RestController
@RequestMapping("/api/classes/{id}")
public class LearningGapController {

    private final LearningGapService learningGapService;

    public LearningGapController(LearningGapService learningGapService) {
        this.learningGapService = learningGapService;
    }

    @GetMapping("/learning-gaps")
    public ResponseEntity<LearningGapResponse> getLearningGaps(Authentication auth,
                                                               @PathVariable String id) {
        String userId = auth.getName();
        return ResponseEntity.ok(learningGapService.analyzeGaps(userId, id));
    }
}
