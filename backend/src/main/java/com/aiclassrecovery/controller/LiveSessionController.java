package com.aiclassrecovery.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aiclassrecovery.model.LiveSession;
import com.aiclassrecovery.service.LiveSessionService;

/**
 * REST controller for Phase 14 — Live Class Session.
 *
 * POST  /api/live-sessions                    → start a new session
 * POST  /api/live-sessions/{id}/chunk         → upload one audio chunk
 * POST  /api/live-sessions/{id}/end           → end session & trigger pipeline
 * GET   /api/live-sessions/{id}/status        → poll current session state
 * GET   /api/live-sessions                    → list all sessions for user
 */
@RestController
@RequestMapping("/api/live-sessions")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    public LiveSessionController(LiveSessionService liveSessionService) {
        this.liveSessionService = liveSessionService;
    }

    /**
     * Start a new live session.
     * Body: { "subjectId": "...", "sessionName": "optional name" }
     */
    @PostMapping
    public ResponseEntity<LiveSession> startSession(
            Authentication auth,
            @RequestBody Map<String, String> body) {

        String userId = auth.getName();
        String subjectId = body.get("subjectId");
        String sessionName = body.get("sessionName");

        if (subjectId == null || subjectId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        LiveSession session = liveSessionService.startSession(userId, subjectId, sessionName);
        return ResponseEntity.ok(session);
    }

    /**
     * Upload a single audio chunk.
     * Multipart form field: "audio" (audio/webm, audio/wav, etc.)
     */
    @PostMapping(value = "/{id}/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadChunk(
            Authentication auth,
            @PathVariable String id,
            @RequestParam("audio") MultipartFile audioChunk) {

        String userId = auth.getName();

        if (audioChunk == null || audioChunk.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] audioData = audioChunk.getBytes();
            String contentType = audioChunk.getContentType();
            Map<String, Object> result = liveSessionService.processAudioChunk(id, userId, audioData, contentType);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * End the live session.
     * Triggers FFmpeg merge + full ClassBridge pipeline asynchronously.
     * Returns the sessionId; use /status to poll, or listen on WebSocket.
     */
    @PostMapping("/{id}/end")
    public ResponseEntity<Map<String, String>> endSession(
            Authentication auth,
            @PathVariable String id) {

        String userId = auth.getName();
        String sessionId = liveSessionService.endSession(id, userId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "message", "Session ended. Full pipeline is running. Check /status or WebSocket for classId."
        ));
    }

    /**
     * Poll for current session state (topics, transcript, status, classId when done).
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<LiveSession> getStatus(
            Authentication auth,
            @PathVariable String id) {

        String userId = auth.getName();
        return ResponseEntity.ok(liveSessionService.getStatus(id, userId));
    }

    /**
     * List all live sessions for the authenticated user, newest first.
     */
    @GetMapping
    public ResponseEntity<List<LiveSession>> listSessions(Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(liveSessionService.listSessions(userId));
    }
}
