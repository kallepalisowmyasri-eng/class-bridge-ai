package com.aiclassrecovery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aiclassrecovery.model.Note;

@Repository
public interface NoteRepository extends MongoRepository<Note, String> {

    /** All notes for a user in a class, newest first */
    List<Note> findByUserIdAndClassIdOrderByCreatedAtDesc(String userId, String classId);

    /** Single note with ownership check */
    Optional<Note> findByIdAndUserId(String id, String userId);

    /** Cascade delete when a class is deleted */
    void deleteByClassId(String classId);
}
