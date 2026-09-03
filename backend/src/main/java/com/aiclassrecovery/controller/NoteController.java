package com.aiclassrecovery.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.dto.NoteRequest;
import com.aiclassrecovery.dto.NoteResponse;
import com.aiclassrecovery.service.NoteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/classes/{id}/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /** GET /api/classes/{id}/notes — list all notes for this class */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> getNotes(Authentication auth,
                                                        @PathVariable("id") String classId) {
        return ResponseEntity.ok(noteService.getNotes(auth.getName(), classId));
    }

    /** POST /api/classes/{id}/notes — create a new note */
    @PostMapping
    public ResponseEntity<NoteResponse> addNote(Authentication auth,
                                                 @PathVariable("id") String classId,
                                                 @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.addNote(auth.getName(), classId, request));
    }

    /** PUT /api/classes/{id}/notes/{noteId} — update an existing note */
    @PutMapping("/{noteId}")
    public ResponseEntity<NoteResponse> updateNote(Authentication auth,
                                                    @PathVariable("id") String classId,
                                                    @PathVariable("noteId") String noteId,
                                                    @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.updateNote(auth.getName(), classId, noteId, request));
    }

    /** DELETE /api/classes/{id}/notes/{noteId} — delete a note */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(Authentication auth,
                                            @PathVariable("id") String classId,
                                            @PathVariable("noteId") String noteId) {
        noteService.deleteNote(auth.getName(), classId, noteId);
        return ResponseEntity.noContent().build();
    }
}
