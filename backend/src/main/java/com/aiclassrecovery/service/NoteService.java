package com.aiclassrecovery.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.aiclassrecovery.dto.NoteRequest;
import com.aiclassrecovery.dto.NoteResponse;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Note;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.NoteRepository;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final ClassRecordRepository classRecordRepository;

    public NoteService(NoteRepository noteRepository,
                       ClassRecordRepository classRecordRepository) {
        this.noteRepository = noteRepository;
        this.classRecordRepository = classRecordRepository;
    }

    /** Get all notes for a user in a class, newest first */
    public List<NoteResponse> getNotes(String userId, String classId) {
        verifyOwnership(userId, classId);
        return noteRepository.findByUserIdAndClassIdOrderByCreatedAtDesc(userId, classId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Add a new note */
    public NoteResponse addNote(String userId, String classId, NoteRequest request) {
        verifyOwnership(userId, classId);

        Note note = Note.builder()
                .classId(classId)
                .userId(userId)
                .content(request.getContent().trim())
                .build();

        return toResponse(noteRepository.save(note));
    }

    /** Edit an existing note — only the owner can edit */
    public NoteResponse updateNote(String userId, String classId, String noteId, NoteRequest request) {
        verifyOwnership(userId, classId);

        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Note not found or access denied"));

        note.setContent(request.getContent().trim());
        return toResponse(noteRepository.save(note));
    }

    /** Delete a note — only the owner can delete */
    public void deleteNote(String userId, String classId, String noteId) {
        verifyOwnership(userId, classId);

        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new RuntimeException("Note not found or access denied"));

        noteRepository.delete(note);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void verifyOwnership(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private NoteResponse toResponse(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .classId(note.getClassId())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
