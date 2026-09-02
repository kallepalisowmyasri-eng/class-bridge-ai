package com.aiclassrecovery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.aiclassrecovery.model.Transcript;

public interface TranscriptRepository extends MongoRepository<Transcript, String> {

    Optional<Transcript> findByClassId(String classId);

    List<Transcript> findByClassIdIn(List<String> classIds);

    boolean existsByClassId(String classId);
}
