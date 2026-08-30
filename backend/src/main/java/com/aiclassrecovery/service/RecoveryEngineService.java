package com.aiclassrecovery.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.TranscriptRepository;

@Service
public class RecoveryEngineService {

    private final ClassRecordRepository classRecordRepository;
    private final TranscriptRepository transcriptRepository;

    public RecoveryEngineService(ClassRecordRepository classRecordRepository,
                                  TranscriptRepository transcriptRepository) {
        this.classRecordRepository = classRecordRepository;
        this.transcriptRepository = transcriptRepository;
    }

    public ClassRecord getClassRecord(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("Access denied");
        }
        return record;
    }

    public Map<String, Object> getWhatYouMissed(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classId", record.getId());
        result.put("className", record.getClassName() != null ? record.getClassName() : "");
        result.put("subject", record.getSubject() != null ? record.getSubject() : "");
        result.put("summary", record.getWhatYouMissed() != null ? record.getWhatYouMissed() : "");
        result.put("topics", record.getTopics() != null ? record.getTopics() : List.of());
        result.put("durationSeconds", record.getDurationSeconds());
        return result;
    }

    public List<ClassRecord.MustLearnItem> getMustLearn(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        return record.getMustLearn() != null ? record.getMustLearn() : List.of();
    }

    public List<String> getCatchUpPath(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        return record.getCatchUpPath() != null ? record.getCatchUpPath() : List.of();
    }

    public Map<String, Object> getPrerequisites(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topics", record.getTopics() != null ? record.getTopics() : List.of());
        result.put("catchUpPath", record.getCatchUpPath() != null ? record.getCatchUpPath() : List.of());
        result.put("mustLearn", record.getMustLearn() != null ? record.getMustLearn() : List.of());
        return result;
    }

    public Map<String, Object> getExplanations(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revisionPoints", record.getRevisionPoints() != null ? record.getRevisionPoints() : List.of());
        result.put("topics", record.getTopics() != null ? record.getTopics() : List.of());
        result.put("mustLearn", record.getMustLearn() != null ? record.getMustLearn() : List.of());
        return result;
    }

    public List<Map<String, String>> getAnnouncements(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        if (record.getImportantTimestamps() == null) return List.of();

        return record.getImportantTimestamps().stream()
                .filter(t -> "ANNOUNCEMENT".equals(t.getType()))
                .map(t -> Map.of(
                        "timestamp", t.getTimestamp() != null ? t.getTimestamp() : "",
                        "description", t.getDescription() != null ? t.getDescription() : ""))
                .toList();
    }

    public List<Map<String, String>> getAssignments(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        if (record.getImportantTimestamps() == null) return List.of();

        return record.getImportantTimestamps().stream()
                .filter(t -> "ASSIGNMENT".equals(t.getType()))
                .map(t -> Map.of(
                        "timestamp", t.getTimestamp() != null ? t.getTimestamp() : "",
                        "description", t.getDescription() != null ? t.getDescription() : ""))
                .toList();
    }

    public List<Map<String, Object>> getResources(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        Transcript transcript = transcriptRepository.findByClassId(classId).orElse(null);

        return List.of(
                Map.of("type", "transcript", "available", transcript != null),
                Map.of("type", "recording", "available", record.getRecordingPath() != null),
                Map.of("type", "topics", "count", record.getTopics() != null ? record.getTopics().size() : 0)
        );
    }

    public List<ClassRecord.TimestampedItem> getTimestamps(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        return record.getImportantTimestamps() != null ? record.getImportantTimestamps() : List.of();
    }

    public List<String> getRevision(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        return record.getRevisionPoints() != null ? record.getRevisionPoints() : List.of();
    }

    public List<ClassRecord.QuizQuestion> getQuiz(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        return record.getQuizQuestions() != null ? record.getQuizQuestions() : List.of();
    }

    public Map<String, Object> getWhatToWatch(String userId, String classId) {
        ClassRecord record = getClassRecord(userId, classId);
        List<ClassRecord.MustLearnItem> mustLearn = record.getMustLearn() != null ? record.getMustLearn() : List.of();
        List<ClassRecord.TimestampedItem> timestamps = record.getImportantTimestamps() != null ? record.getImportantTimestamps() : List.of();

        var mustWatch = mustLearn.stream()
                .filter(m -> "CRITICAL".equals(m.getPriority()))
                .map(m -> Map.of(
                        "concept", m.getConcept() != null ? m.getConcept() : "",
                        "priority", "MUST_WATCH",
                        "reason", "Critical concept — essential for understanding the class"))
                .toList();

        var recommended = mustLearn.stream()
                .filter(m -> "HIGH".equals(m.getPriority()))
                .map(m -> Map.of(
                        "concept", m.getConcept() != null ? m.getConcept() : "",
                        "priority", "RECOMMENDED",
                        "reason", "High-priority concept — strongly recommended"))
                .toList();

        var optional = mustLearn.stream()
                .filter(m -> "MEDIUM".equals(m.getPriority()))
                .map(m -> Map.of(
                        "concept", m.getConcept() != null ? m.getConcept() : "",
                        "priority", "OPTIONAL",
                        "reason", "Medium priority — can be skipped if short on time"))
                .toList();

        var explanationTimestamps = timestamps.stream()
                .filter(t -> "EXPLANATION".equals(t.getType()))
                .map(t -> Map.of(
                        "timestamp", t.getTimestamp() != null ? t.getTimestamp() : "",
                        "description", t.getDescription() != null ? t.getDescription() : ""))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mustWatch", mustWatch);
        result.put("recommended", recommended);
        result.put("optional", optional);
        result.put("explanationTimestamps", explanationTimestamps);
        return result;
    }
}
