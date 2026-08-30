package com.aiclassrecovery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.ClassResponse;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.service.ClassService;
import com.aiclassrecovery.service.GeminiService;

@RestController
@RequestMapping("/api/classes")
public class AnalyzeController {

    private final GeminiService geminiService;
    private final ClassService classService;

    public AnalyzeController(GeminiService geminiService, ClassService classService) {
        this.geminiService = geminiService;
        this.classService = classService;
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ClassResponse> analyze(Authentication auth,
                                                  @PathVariable String id) {
        String userId = auth.getName();

        // Verify ownership
        classService.getById(userId, id);

        ClassRecord record = geminiService.analyze(id);
        return ResponseEntity.ok(classService.toResponse(record));
    }
}
