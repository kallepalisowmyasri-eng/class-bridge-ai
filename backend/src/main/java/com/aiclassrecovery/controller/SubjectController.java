package com.aiclassrecovery.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.SubjectRequest;
import com.aiclassrecovery.dto.SubjectResponse;
import com.aiclassrecovery.service.SubjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(Authentication auth,
                                                   @Valid @RequestBody SubjectRequest request) {
        String userId = auth.getName();
        return ResponseEntity.ok(subjectService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> list(Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(subjectService.listByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getById(Authentication auth,
                                                    @PathVariable String id) {
        String userId = auth.getName();
        return ResponseEntity.ok(subjectService.getById(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth,
                                        @PathVariable String id) {
        String userId = auth.getName();
        subjectService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
