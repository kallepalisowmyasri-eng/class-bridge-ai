package com.aiclassrecovery.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aiclassrecovery.dto.ClassRequest;
import com.aiclassrecovery.dto.ClassResponse;
import com.aiclassrecovery.service.ClassService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @PostMapping
    public ResponseEntity<ClassResponse> create(Authentication auth,
                                                 @Valid @RequestBody ClassRequest request) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<java.util.List<ClassResponse>> list(Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.listByUser(userId));
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<java.util.List<ClassResponse>> listBySubject(Authentication auth,
                                                              @PathVariable String subjectId) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.listBySubject(userId, subjectId));
    }

    @GetMapping("/search")
    public ResponseEntity<java.util.List<ClassResponse>> search(Authentication auth,
                                                                @RequestParam(name = "q", defaultValue = "") String q) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.search(userId, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> getById(Authentication auth,
                                                  @PathVariable String id) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.getById(userId, id));
    }

    @PostMapping("/{id}/upload")
    public ResponseEntity<ClassResponse> uploadRecording(Authentication auth,
                                                          @PathVariable String id,
                                                          @RequestParam("file") MultipartFile file) {
        String userId = auth.getName();
        return ResponseEntity.ok(classService.uploadRecording(userId, id, file));
    }

    @GetMapping("/{id}/recording")
    public ResponseEntity<Resource> downloadRecording(Authentication auth,
                                                       @PathVariable String id) {
        String userId = auth.getName();

        // Single query for ownership check + data retrieval
        ClassResponse cls = classService.getById(userId, id);

        if (cls.getRecordingPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = classService.loadRecording(userId, id);
        String filename = cls.getOriginalFilename() != null ? cls.getOriginalFilename() : "recording";
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<Resource> streamAudio(Authentication auth,
                                                @PathVariable String id) {
        String userId = auth.getName();
        ClassResponse cls = classService.getById(userId, id);

        if (cls.getAudioPath() == null && cls.getRecordingPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = classService.loadAudio(userId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + id + "_audio.wav\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth,
                                        @PathVariable String id) {
        String userId = auth.getName();
        classService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/classes/{id}/retry — restart processing from last checkpoint */
    @PostMapping("/{id}/retry")
    public ResponseEntity<ClassResponse> retry(Authentication auth,
                                               @PathVariable String id) {
        String userId = auth.getName();
        ClassResponse response = classService.retryProcessing(userId, id);
        return ResponseEntity.ok(response);
    }
}
