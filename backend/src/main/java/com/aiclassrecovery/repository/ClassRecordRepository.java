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
}
