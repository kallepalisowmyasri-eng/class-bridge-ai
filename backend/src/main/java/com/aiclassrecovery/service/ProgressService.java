package com.aiclassrecovery.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.aiclassrecovery.dto.ConceptStatusRequest;
import com.aiclassrecovery.dto.ProgressResponse;
import com.aiclassrecovery.dto.QuizResultRequest;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.StudentProgress;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.StudentProgressRepository;

@Service
public class ProgressService {

    private final StudentProgressRepository progressRepository;
    private final ClassRecordRepository classRecordRepository;

    public ProgressService(StudentProgressRepository progressRepository,
                           ClassRecordRepository classRecordRepository) {
        this.progressRepository = progressRepository;
        this.classRecordRepository = classRecordRepository;
    }

    public ProgressResponse getProgress(String userId, String classId) {
        verifyOwnership(userId, classId);

        StudentProgress progress = progressRepository.findByUserIdAndClassId(userId, classId)
                .orElse(StudentProgress.builder()
                        .userId(userId)
                        .classId(classId)
                        .conceptStatuses(new HashMap<>())
                        .quizResults(new HashMap<>())
                        .build());

        return toResponse(progress);
    }

    public ProgressResponse updateConceptStatus(String userId, String classId, ConceptStatusRequest request) {
        verifyOwnership(userId, classId);

        String status = request.getStatus();
        if (!"UNDERSTANDING".equals(status) && !"NEEDS_REVIEW".equals(status) && !"MASTERED".equals(status)) {
            throw new RuntimeException("Invalid status. Must be UNDERSTANDING, NEEDS_REVIEW, or MASTERED");
        }

        StudentProgress progress = progressRepository.findByUserIdAndClassId(userId, classId)
                .orElse(StudentProgress.builder()
                        .userId(userId)
                        .classId(classId)
                        .subjectId(getSubjectId(userId, classId))
                        .conceptStatuses(new HashMap<>())
                        .quizResults(new HashMap<>())
                        .build());

        Map<String, StudentProgress.ConceptStatus> statuses = progress.getConceptStatuses();
        if (statuses == null) statuses = new HashMap<>();

        statuses.put(request.getConcept(), StudentProgress.ConceptStatus.builder()
                .status(status)
                .markedAt(Instant.now())
                .build());

        progress.setConceptStatuses(statuses);
        progress.setLastActivityAt(Instant.now());
        recalculateProgress(progress);

        progress = progressRepository.save(progress);
        return toResponse(progress);
    }

    public ProgressResponse submitQuizResults(String userId, String classId, QuizResultRequest request) {
        verifyOwnership(userId, classId);

        StudentProgress progress = progressRepository.findByUserIdAndClassId(userId, classId)
                .orElse(StudentProgress.builder()
                        .userId(userId)
                        .classId(classId)
                        .subjectId(getSubjectId(userId, classId))
                        .conceptStatuses(new HashMap<>())
                        .quizResults(new HashMap<>())
                        .build());

        Map<Integer, Boolean> results = progress.getQuizResults();
        if (results == null) results = new HashMap<>();
        results.putAll(request.getResults());
        progress.setQuizResults(results);

        // Calculate quiz stats
        int attempted = results.size();
        int correct = (int) results.values().stream().filter(Boolean::booleanValue).count();
        progress.setQuizAttempted(attempted);
        progress.setQuizScorePercent(attempted > 0 ? (correct * 100 / attempted) : 0);

        progress.setLastActivityAt(Instant.now());
        recalculateProgress(progress);

        progress = progressRepository.save(progress);
        return toResponse(progress);
    }

    private void recalculateProgress(StudentProgress progress) {
        Map<String, StudentProgress.ConceptStatus> statuses = progress.getConceptStatuses();
        if (statuses == null || statuses.isEmpty()) {
            // If no concept tracking, use quiz score
            progress.setProgressPercent(progress.getQuizScorePercent());
            return;
        }

        long mastered = statuses.values().stream()
                .filter(s -> "MASTERED".equals(s.getStatus()))
                .count();
        long understanding = statuses.values().stream()
                .filter(s -> "UNDERSTANDING".equals(s.getStatus()))
                .count();
        long total = statuses.size();

        // Weighted: mastered=100%, understanding=50%, needs_review=0%
        double weighted = (mastered * 100.0 + understanding * 50.0) / total;
        progress.setProgressPercent((int) Math.round(weighted));
    }

    private void verifyOwnership(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private String getSubjectId(String userId, String classId) {
        return classRecordRepository.findById(classId)
                .map(ClassRecord::getSubjectId)
                .orElse(null);
    }

    private ProgressResponse toResponse(StudentProgress progress) {
        Map<String, String> statuses = new LinkedHashMap<>();
        if (progress.getConceptStatuses() != null) {
            progress.getConceptStatuses().forEach((k, v) -> statuses.put(k, v.getStatus()));
        }

        return ProgressResponse.builder()
                .classId(progress.getClassId())
                .conceptStatuses(statuses)
                .progressPercent(progress.getProgressPercent())
                .quizAttempted(progress.getQuizAttempted())
                .quizScorePercent(progress.getQuizScorePercent())
                .quizResults(progress.getQuizResults())
                .build();
    }
}
