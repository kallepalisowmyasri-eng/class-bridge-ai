package com.aiclassrecovery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.ConceptStatusRequest;
import com.aiclassrecovery.dto.ProgressResponse;
import com.aiclassrecovery.dto.QuizResultRequest;
import com.aiclassrecovery.service.ProgressService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/classes/{id}/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping
    public ResponseEntity<ProgressResponse> getProgress(Authentication auth,
                                                         @PathVariable("id") String classId) {
        return ResponseEntity.ok(progressService.getProgress(auth.getName(), classId));
    }

    @PostMapping("/concept")
    public ResponseEntity<ProgressResponse> updateConcept(Authentication auth,
                                                           @PathVariable("id") String classId,
                                                           @Valid @RequestBody ConceptStatusRequest request) {
        return ResponseEntity.ok(progressService.updateConceptStatus(auth.getName(), classId, request));
    }

    @PostMapping("/quiz")
    public ResponseEntity<ProgressResponse> submitQuiz(Authentication auth,
                                                        @PathVariable("id") String classId,
                                                        @Valid @RequestBody QuizResultRequest request) {
        return ResponseEntity.ok(progressService.submitQuizResults(auth.getName(), classId, request));
    }
}
