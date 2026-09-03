package com.aiclassrecovery.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aiclassrecovery.dto.ClassRequest;
import com.aiclassrecovery.dto.ClassResponse;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Subject;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.NoteRepository;
import com.aiclassrecovery.repository.StudentProgressRepository;
import com.aiclassrecovery.repository.SubjectRepository;

@Service
public class ClassService {

    private static final Logger log = LoggerFactory.getLogger(ClassService.class);

    private final ClassRecordRepository classRecordRepository;
    private final SubjectRepository subjectRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final NoteRepository noteRepository;
    private final FileService fileService;
    private final MediaProcessingService mediaProcessingService;
    private final TranscriptionService transcriptionService;
    private final GeminiService geminiService;
    private final ApplicationContext applicationContext;

    public ClassService(ClassRecordRepository classRecordRepository,
                        SubjectRepository subjectRepository,
                        StudentProgressRepository studentProgressRepository,
                        NoteRepository noteRepository,
                        FileService fileService,
                        MediaProcessingService mediaProcessingService,
                        TranscriptionService transcriptionService,
                        GeminiService geminiService,
                        ApplicationContext applicationContext) {
        this.classRecordRepository = classRecordRepository;
        this.subjectRepository = subjectRepository;
        this.studentProgressRepository = studentProgressRepository;
        this.noteRepository = noteRepository;
        this.fileService = fileService;
        this.mediaProcessingService = mediaProcessingService;
        this.transcriptionService = transcriptionService;
        this.geminiService = geminiService;
        this.applicationContext = applicationContext;
    }

    public ClassResponse create(String userId, ClassRequest request) {
        Subject subject = subjectRepository.findByIdAndUserId(request.getSubjectId(), userId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        ClassRecord record = ClassRecord.builder()
                .subjectId(subject.getId())
                .subject(subject.getName())
                .className(request.getClassName())
                .description(request.getDescription())
                .userId(userId)
                .status("PENDING")
                .build();

        record = classRecordRepository.save(record);
        return toResponse(record);
    }

    public List<ClassResponse> listByUser(String userId) {
        return classRecordRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ClassResponse> listBySubject(String userId, String subjectId) {
        subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        // Use the efficient query instead of fetching all and filtering
        return classRecordRepository.findByUserIdAndSubjectId(userId, subjectId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ClassResponse> search(String userId, String query) {
        if (query == null || query.isBlank()) {
            return listByUser(userId);
        }
        String q = query.trim();

        // Search by class name, subject, and description — deduplicate by ID
        var byName        = classRecordRepository.findByUserIdAndClassNameContainingIgnoreCase(userId, q);
        var bySubject     = classRecordRepository.findByUserIdAndSubjectContainingIgnoreCase(userId, q);
        var byDescription = classRecordRepository.findByUserIdAndDescriptionContainingIgnoreCase(userId, q);

        java.util.LinkedHashMap<String, ClassRecord> merged = new java.util.LinkedHashMap<>();
        byName.forEach(r        -> merged.put(r.getId(), r));
        bySubject.forEach(r     -> merged.put(r.getId(), r));
        byDescription.forEach(r -> merged.put(r.getId(), r));

        return merged.values().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(this::toResponse)
                .toList();
    }

    public ClassResponse getById(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        return toResponse(record);
    }

    public ClassResponse uploadRecording(String userId, String classId, MultipartFile file) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        // Delete previous files if they exist
        fileService.deleteFile(record.getRecordingPath());
        mediaProcessingService.deleteAudio(record.getAudioPath());
        transcriptionService.deleteByClassId(classId);

        String filePath = fileService.saveRecording(classId, file);
        record.setRecordingPath(filePath);
        record.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "recording");
        record.setTranscriptId(null);
        record.setStatus("UPLOADED");

        record = classRecordRepository.save(record);

        // Trigger async processing via proxy to avoid self-invocation issue
        String recordingPath = record.getRecordingPath();
        String id = record.getId();
        applicationContext.getBean(ClassService.class).processRecordingAsync(id, recordingPath);

        return toResponse(record);
    }

    @Async
    public void processRecordingAsync(String classId, String recordingPath) {
        try {
            ClassRecord record = classRecordRepository.findById(classId).orElse(null);
            if (record == null) return;

            record.setStatus("PROCESSING");
            classRecordRepository.save(record);

            // Step 1: Get duration
            int duration = mediaProcessingService.getDuration(recordingPath);
            record.setDurationSeconds(duration);
            classRecordRepository.save(record);

            // Step 2: Extract audio and frames (visuals are best-effort and non-fatal)
            try {
                boolean hasVideo = mediaProcessingService.hasVideoStream(recordingPath);
                record.setHasVideo(hasVideo);
                if (hasVideo) {
                    log.info("Video stream detected for class {}. Extracting keyframes...", classId);
                    try {
                        List<String> frames = mediaProcessingService.extractFrames(recordingPath, classId);
                        record.setSlidePaths(frames);
                    } catch (Exception e) {
                        log.warn("Frame extraction failed for class {}, proceeding with audio only: {}", classId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to probe video stream for class {}: {}", classId, e.getMessage());
            }

            String audioPath = mediaProcessingService.extractAudio(recordingPath, classId);
            record.setAudioPath(audioPath);
            record.setStatus("AUDIO_READY");
            classRecordRepository.save(record);
            log.info("Media extraction complete for class {}", classId);

            // Step 3: Transcribe
            Transcript transcript = transcriptionService.transcribe(classId, audioPath);
            record.setTranscriptId(transcript.getId());
            record.setStatus("TRANSCRIBED");
            classRecordRepository.save(record);
            log.info("Transcription complete for class {}", classId);

            // Step 4: Analyze with Gemini
            try {
                geminiService.analyze(classId);
                log.info("Analysis complete for class {}", classId);
            } catch (Exception e) {
                log.warn("Gemini analysis failed for class {} (transcription still available): {}",
                        classId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Processing failed for class {}", classId, e);
            ClassRecord failed = classRecordRepository.findById(classId).orElse(null);
            if (failed != null) {
                failed.setStatus("FAILED");
                // Store the root cause message (strip class name prefix for readability)
                String reason = e.getMessage();
                if (reason == null && e.getCause() != null) reason = e.getCause().getMessage();
                if (reason == null) reason = e.getClass().getSimpleName();
                // Truncate if very long
                if (reason.length() > 500) reason = reason.substring(0, 497) + "...";
                failed.setFailureReason(reason);
                classRecordRepository.save(failed);
            }
        }
    }

    /**
     * Retry processing from the last completed checkpoint.
     * - If status is TRANSCRIBED → only re-run Gemini analysis
     * - If status is AUDIO_READY → re-run transcription + Gemini
     * - Otherwise → restart the entire pipeline
     */
    public ClassResponse retryProcessing(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        String currentStatus = record.getStatus();
        // Only retry from FAILED, or non-terminal stuck states
        if ("ANALYZED".equals(currentStatus) || "DONE".equals(currentStatus)) {
            throw new RuntimeException("Class is already analyzed. No retry needed.");
        }

        // Clear failure info and increment retry counter
        record.setFailureReason(null);
        record.setRetryCount(record.getRetryCount() + 1);
        record.setStatus("PROCESSING");
        classRecordRepository.save(record);

        // Launch async retry from last checkpoint
        if ("TRANSCRIBED".equals(currentStatus)) {
            log.info("Retrying Gemini analysis only for class {} (was TRANSCRIBED)", classId);
            applicationContext.getBean(ClassService.class).retryGeminiAsync(classId);
        } else if ("AUDIO_READY".equals(currentStatus)) {
            log.info("Retrying transcription+Gemini for class {} (was AUDIO_READY)", classId);
            applicationContext.getBean(ClassService.class).retryFromTranscriptionAsync(classId);
        } else {
            log.info("Full pipeline retry for class {} (was {})", classId, currentStatus);
            applicationContext.getBean(ClassService.class).processRecordingAsync(classId, record.getRecordingPath());
        }

        return toResponse(classRecordRepository.findById(classId).orElse(record));
    }

    @Async
    public void retryGeminiAsync(String classId) {
        try {
            geminiService.analyze(classId);
            log.info("Retry Gemini analysis complete for class {}", classId);
        } catch (Exception e) {
            log.error("Retry Gemini analysis failed for class {}", classId, e);
            ClassRecord rec = classRecordRepository.findById(classId).orElse(null);
            if (rec != null) {
                rec.setStatus("FAILED");
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                rec.setFailureReason(reason.length() > 500 ? reason.substring(0, 497) + "..." : reason);
                classRecordRepository.save(rec);
            }
        }
    }

    @Async
    public void retryFromTranscriptionAsync(String classId) {
        ClassRecord record = classRecordRepository.findById(classId).orElse(null);
        if (record == null) return;
        try {
            Transcript transcript = transcriptionService.transcribe(classId, record.getAudioPath());
            record.setTranscriptId(transcript.getId());
            record.setStatus("TRANSCRIBED");
            classRecordRepository.save(record);
            log.info("Retry transcription complete for class {}", classId);

            geminiService.analyze(classId);
            log.info("Retry Gemini analysis complete for class {}", classId);
        } catch (Exception e) {
            log.error("Retry from transcription failed for class {}", classId, e);
            ClassRecord failed = classRecordRepository.findById(classId).orElse(record);
            failed.setStatus("FAILED");
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            failed.setFailureReason(reason.length() > 500 ? reason.substring(0, 497) + "..." : reason);
            classRecordRepository.save(failed);
        }
    }

    public Resource loadRecording(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        if (record.getRecordingPath() == null) {
            throw new RuntimeException("No recording found for this class");
        }

        return fileService.loadRecording(record.getRecordingPath());
    }

    public Resource loadAudio(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        if (record.getAudioPath() != null) {
            return fileService.loadRecording(record.getAudioPath());
        }
        if (record.getRecordingPath() != null) {
            return fileService.loadRecording(record.getRecordingPath());
        }
        throw new RuntimeException("No audio or recording found for this class");
    }

    public Resource loadSlide(String userId, String classId, String slideName) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        if (slideName == null || slideName.contains("..") || slideName.contains("/") || slideName.contains("\\")) {
            throw new IllegalArgumentException("Invalid slide name");
        }

        if (record.getSlidePaths() != null) {
            for (String path : record.getSlidePaths()) {
                if (path.endsWith(slideName) || path.endsWith("/" + slideName) || path.endsWith("\\" + slideName)) {
                    return fileService.loadRecording(path);
                }
            }
        }
        throw new RuntimeException("Slide not found");
    }

    public String getOriginalFilename(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        return record.getOriginalFilename();
    }

    public void delete(String userId, String classId) {
        ClassRecord record = classRecordRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (!userId.equals(record.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }

        fileService.deleteFile(record.getRecordingPath());
        mediaProcessingService.deleteAudio(record.getAudioPath());
        mediaProcessingService.deleteFrames(classId);
        transcriptionService.deleteByClassId(classId);
        studentProgressRepository.deleteByClassId(classId);
        noteRepository.deleteByClassId(classId);
        classRecordRepository.delete(record);
    }

    public ClassResponse toResponse(ClassRecord record) {
        return ClassResponse.builder()
                .id(record.getId())
                .subjectId(record.getSubjectId())
                .subject(record.getSubject())
                .className(record.getClassName())
                .description(record.getDescription())
                .userId(record.getUserId())
                .recordingPath(record.getRecordingPath())
                .originalFilename(record.getOriginalFilename())
                .audioPath(record.getAudioPath())
                .durationSeconds(record.getDurationSeconds())
                .transcriptId(record.getTranscriptId())
                .hasVideo(record.isHasVideo())
                .slidePaths(record.getSlidePaths())
                .visualSummary(record.getVisualSummary())
                .status(record.getStatus())
                .failureReason(record.getFailureReason())
                .retryCount(record.getRetryCount())
                .whatYouMissed(record.getWhatYouMissed())
                .topics(record.getTopics())
                .mustLearn(record.getMustLearn())
                .catchUpPath(record.getCatchUpPath())
                .importantTimestamps(record.getImportantTimestamps())
                .revisionPoints(record.getRevisionPoints())
                .quizQuestions(record.getQuizQuestions())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
