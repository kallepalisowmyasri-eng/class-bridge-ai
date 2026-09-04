package com.aiclassrecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.Transcript;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.TranscriptRepository;
import com.aiclassrecovery.service.FileService;
import com.aiclassrecovery.service.MediaProcessingService;
import com.aiclassrecovery.service.TranscriptionService;

@SpringBootTest
public class RecordingPipelineVerificationTest {

    @Autowired
    private MediaProcessingService mediaProcessingService;

    @Autowired
    private TranscriptionService transcriptionService;

    @Autowired
    private FileService fileService;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private ClassRecordRepository classRecordRepository;

    private String testClassId;
    private String extractedAudioPath;
    private String savedRecordingPath;

    @BeforeEach
    void setUp() {
        testClassId = "test_class_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        if (testClassId != null) {
            transcriptionService.deleteByClassId(testClassId);
            classRecordRepository.deleteById(testClassId);
        }
        if (extractedAudioPath != null) {
            mediaProcessingService.deleteAudio(extractedAudioPath);
        }
        if (savedRecordingPath != null) {
            fileService.deleteFile(savedRecordingPath);
        }
    }

    @Test
    void test1_FFmpegAndFFprobeAccessible() throws Exception {
        // 1. Verify FFmpeg version check from Java ProcessBuilder
        ProcessBuilder pbFfmpeg = new ProcessBuilder("ffmpeg", "-version");
        pbFfmpeg.redirectErrorStream(true);
        Process pFfmpeg = pbFfmpeg.start();
        String ffmpegOutput = readOutput(pFfmpeg);
        int ffmpegExit = pFfmpeg.waitFor();

        assertEquals(0, ffmpegExit, "FFmpeg should exit with code 0");
        assertTrue(ffmpegOutput.toLowerCase().contains("ffmpeg version"), "FFmpeg output should contain version");

        // 2. Verify FFprobe version check from Java ProcessBuilder
        ProcessBuilder pbFfprobe = new ProcessBuilder("ffprobe", "-version");
        pbFfprobe.redirectErrorStream(true);
        Process pFfprobe = pbFfprobe.start();
        String ffprobeOutput = readOutput(pFfprobe);
        int ffprobeExit = pFfprobe.waitFor();

        assertEquals(0, ffprobeExit, "FFprobe should exit with code 0");
        assertTrue(ffprobeOutput.toLowerCase().contains("ffprobe version"), "FFprobe output should contain version");
    }

    @Test
    void test2_FileValidationAndSupportedFormats() throws Exception {
        // Empty file rejection
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.mp4", "video/mp4", new byte[0]);
        Exception exEmpty = assertThrows(RuntimeException.class, () ->
                fileService.saveRecording(testClassId, emptyFile));
        assertTrue(exEmpty.getMessage().contains("File is empty"), "Empty file should be rejected");

        // Unsupported extension rejection
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file", "document.txt", "text/plain", "hello world".getBytes());
        Exception exUnsupported = assertThrows(RuntimeException.class, () ->
                fileService.saveRecording(testClassId, unsupportedFile));
        assertTrue(exUnsupported.getMessage().contains("Unsupported file type"), "Non-media file should be rejected");

        // Supported formats verification
        String[] supportedExts = {"mp4", "mp3", "wav", "m4a"};
        for (String ext : supportedExts) {
            MockMultipartFile validDummy = new MockMultipartFile(
                    "file", "sample." + ext, "application/octet-stream", new byte[]{1, 2, 3, 4});
            String saved = fileService.saveRecording(testClassId + "_" + ext, validDummy);
            assertNotNull(saved, "Supported extension ." + ext + " should save");
            assertTrue(new File(saved).exists(), "Saved file must exist on disk");
            fileService.deleteFile(saved);
        }
    }

    @Test
    void test3_FullPipelineWithRealVideoToMongoDB() throws Exception {
        File realVideo = new File("../speech.mp4");
        assertTrue(realVideo.exists(), "Real recording speech.mp4 must exist for pipeline verification");
        assertTrue(realVideo.length() > 0, "speech.mp4 must not be empty");

        // Step 1: Upload recording through FileService
        try (FileInputStream fis = new FileInputStream(realVideo)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file", "speech.mp4", "video/mp4", fis);
            savedRecordingPath = fileService.saveRecording(testClassId, multipartFile);
        }
        assertNotNull(savedRecordingPath);
        assertTrue(new File(savedRecordingPath).exists(), "Saved recording must exist in upload directory");

        // Step 2: Extract audio via MediaProcessingService
        extractedAudioPath = mediaProcessingService.extractAudio(savedRecordingPath, testClassId);
        assertNotNull(extractedAudioPath);
        File audioFile = new File(extractedAudioPath);
        assertTrue(audioFile.exists(), "Extracted audio file must exist");
        assertTrue(audioFile.length() > 0, "Extracted audio file must not be empty");

        // Step 3: Check duration and video stream
        int duration = mediaProcessingService.getDuration(savedRecordingPath);
        assertTrue(duration > 0, "Duration should be positive (detected: " + duration + "s)");

        boolean hasVideo = mediaProcessingService.hasVideoStream(savedRecordingPath);
        assertTrue(hasVideo, "speech.mp4 has video stream");

        // Step 4: Transcribe using WhisperService
        Transcript transcript = transcriptionService.transcribe(testClassId, extractedAudioPath);
        assertNotNull(transcript);
        assertNotNull(transcript.getId());
        assertEquals(testClassId, transcript.getClassId());
        assertNotNull(transcript.getSegments());
        assertFalse(transcript.getSegments().isEmpty(), "Transcript must contain segments");
        assertNotNull(transcript.getFullText());
        assertTrue(transcript.getFullText().toLowerCase().contains("classbridge"),
                "Transcript should mention Classbridge");

        // Step 5: Verify Timestamps
        for (Transcript.Segment seg : transcript.getSegments()) {
            assertTrue(seg.getEnd() >= seg.getStart(), "Segment end must be >= start");
            assertNotNull(seg.getText());
            assertFalse(seg.getText().isBlank(), "Segment text must not be blank");
        }

        // Step 6: Verify MongoDB storage
        Transcript stored = transcriptRepository.findByClassId(testClassId).orElse(null);
        assertNotNull(stored, "Transcript must be present in MongoDB");
        assertEquals(transcript.getId(), stored.getId());
        assertEquals(transcript.getFullText(), stored.getFullText());
        assertEquals(transcript.getSegments().size(), stored.getSegments().size());
        assertTrue(stored.getDuration() > 0, "Duration should be stored");
    }

    @Test
    void test4_ProcessingStatusTransitionsAndFailureHandling() {
        // Create initial ClassRecord
        ClassRecord record = ClassRecord.builder()
                .id(testClassId)
                .userId("test_user_1")
                .className("Test Biology Class")
                .status("PENDING")
                .build();
        record = classRecordRepository.save(record);
        assertEquals("PENDING", record.getStatus());

        // Transition: UPLOADED
        record.setStatus("UPLOADED");
        record.setRecordingPath("dummy/path.mp4");
        classRecordRepository.save(record);
        assertEquals("UPLOADED", classRecordRepository.findById(testClassId).get().getStatus());

        // Transition: PROCESSING
        record.setStatus("PROCESSING");
        classRecordRepository.save(record);
        assertEquals("PROCESSING", classRecordRepository.findById(testClassId).get().getStatus());

        // Transition: AUDIO_READY
        record.setStatus("AUDIO_READY");
        record.setAudioPath("dummy/audio.wav");
        classRecordRepository.save(record);
        assertEquals("AUDIO_READY", classRecordRepository.findById(testClassId).get().getStatus());

        // Transition: TRANSCRIBED
        record.setStatus("TRANSCRIBED");
        classRecordRepository.save(record);
        assertEquals("TRANSCRIBED", classRecordRepository.findById(testClassId).get().getStatus());

        // Test Failure scenario: status set to FAILED with failureReason
        record.setStatus("FAILED");
        record.setFailureReason("Transcription service connection timed out");
        classRecordRepository.save(record);

        ClassRecord failedRecord = classRecordRepository.findById(testClassId).get();
        assertEquals("FAILED", failedRecord.getStatus());
        assertNotNull(failedRecord.getFailureReason());
        assertTrue(failedRecord.getFailureReason().contains("timed out"));
    }

    private String readOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
