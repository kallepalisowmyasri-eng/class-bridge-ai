package com.aiclassrecovery.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aiclassrecovery.model.ClassRecord;

import java.util.List;

@Repository
public interface ClassRecordRepository extends MongoRepository<ClassRecord, String> {
    
    /** Find all records uploaded by a specific user */
    List<ClassRecord> findByUserId(String userId);
    
    /** Find all records for a specific subject */
    List<ClassRecord> findBySubject(String subject);

    /** Find all records for a specific subject by subject ID */
    List<ClassRecord> findBySubjectId(String subjectId);

    /** Count records for a subject (single COUNT query — no full document load) */
    long countBySubjectId(String subjectId);

    /** Find all records for a specific user and subject */
    List<ClassRecord> findByUserIdAndSubjectId(String userId, String subjectId);

    /** Search classes by name or subject for a user */
    List<ClassRecord> findByUserIdAndClassNameContainingIgnoreCase(String userId, String className);

    List<ClassRecord> findByUserIdAndSubjectContainingIgnoreCase(String userId, String subject);

    /** Search classes by description text for a user */
    List<ClassRecord> findByUserIdAndDescriptionContainingIgnoreCase(String userId, String description);
}
