package com.aiclassrecovery.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aiclassrecovery.dto.SubjectRequest;
import com.aiclassrecovery.dto.SubjectResponse;
import com.aiclassrecovery.model.Subject;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.SubjectRepository;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ClassRecordRepository classRecordRepository;
    private final FileService fileService;
    private final MediaProcessingService mediaProcessingService;

    public SubjectService(SubjectRepository subjectRepository,
                          ClassRecordRepository classRecordRepository,
                          FileService fileService,
                          MediaProcessingService mediaProcessingService) {
        this.subjectRepository = subjectRepository;
        this.classRecordRepository = classRecordRepository;
        this.fileService = fileService;
        this.mediaProcessingService = mediaProcessingService;
    }

    public SubjectResponse create(String userId, SubjectRequest request) {
        if (subjectRepository.existsByNameAndUserId(request.getName(), userId)) {
            throw new RuntimeException("Subject already exists");
        }

        Subject subject = Subject.builder()
                .name(request.getName())
                .description(request.getDescription())
                .userId(userId)
                .build();

        subject = subjectRepository.save(subject);
        return toResponse(subject, 0);
    }

    public List<SubjectResponse> listByUser(String userId) {
        List<Subject> subjects = subjectRepository.findByUserId(userId);
        return subjects.stream()
                .map(s -> {
                    int count = classRecordRepository.findBySubjectId(s.getId()).size();
                    return toResponse(s, count);
                })
                .toList();
    }

    public SubjectResponse getById(String userId, String subjectId) {
        Subject subject = subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));
        int count = classRecordRepository.findBySubjectId(subjectId).size();
        return toResponse(subject, count);
    }

    public void delete(String userId, String subjectId) {
        Subject subject = subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        var classes = classRecordRepository.findBySubjectId(subjectId);
        for (var cls : classes) {
            if (userId.equals(cls.getUserId())) {
                fileService.deleteFile(cls.getRecordingPath());
                mediaProcessingService.deleteAudio(cls.getAudioPath());
                classRecordRepository.delete(cls);
            }
        }

        subjectRepository.delete(subject);
    }

    private SubjectResponse toResponse(Subject subject, int classCount) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .description(subject.getDescription())
                .classCount(classCount)
                .createdAt(subject.getCreatedAt())
                .build();
    }
}
