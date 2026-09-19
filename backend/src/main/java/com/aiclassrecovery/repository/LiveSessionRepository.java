package com.aiclassrecovery.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.aiclassrecovery.model.LiveSession;

@Repository
public interface LiveSessionRepository extends MongoRepository<LiveSession, String> {

    /** All sessions for a user, newest first (sorted in service layer) */
    List<LiveSession> findByUserId(String userId);

    /** Find all active sessions for a user */
    List<LiveSession> findByUserIdAndStatus(String userId, String status);
}
