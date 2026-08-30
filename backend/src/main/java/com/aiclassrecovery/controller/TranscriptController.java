package com.aiclassrecovery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.service.ClassService;
import com.aiclassrecovery.service.TranscriptionService;

@RestController
@RequestMapping("/api/classes")
public class TranscriptController {

    private final TranscriptionService transcriptionService;
    private final ClassService classService;

    public TranscriptController(TranscriptionService transcriptionService, ClassService classService) {
        this.transcriptionService = transcriptionService;
        this.classService = classService;
    }

    @GetMapping("/{id}/transcript")
    public ResponseEntity<Transcript> getTranscript(Authentication auth,
                                                     @PathVariable String id) {
        // Ownership check
        classService.getById(auth.getName(), id);

        Transcript transcript = transcriptionService.getByClassId(id);
        return ResponseEntity.ok(transcript);
    }
}
