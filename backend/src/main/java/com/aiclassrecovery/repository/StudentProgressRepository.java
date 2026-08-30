package com.aiclassrecovery.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aiclassrecovery.model.StudentProgress;

import java.util.Optional;

@Repository
public interface StudentProgressRepository extends MongoRepository<StudentProgress, String> {

    Optional<StudentProgress> findByUserIdAndClassId(String userId, String classId);

    boolean existsByUserIdAndClassId(String userId, String classId);
}
