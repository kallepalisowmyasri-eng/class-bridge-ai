package com.aiclassrecovery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.aiclassrecovery.model.Subject;

public interface SubjectRepository extends MongoRepository<Subject, String> {

    List<Subject> findByUserId(String userId);

    Optional<Subject> findByIdAndUserId(String id, String userId);

    boolean existsByNameAndUserId(String name, String userId);
}
