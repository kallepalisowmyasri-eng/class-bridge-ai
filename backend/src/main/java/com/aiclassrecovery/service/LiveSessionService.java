package com.aiclassrecovery.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.aiclassrecovery.dto.ClassRequest;
import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.model.LiveSession;
import com.aiclassrecovery.model.Subject;
import com.aiclassrecovery.repository.ClassRecordRepository;
import com.aiclassrecovery.repository.LiveSessionRepository;
import com.aiclassrecovery.repository.SubjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Core service for Phase 14 — Live Class Session.
 *
 * Flow:
 *   startSession()       → creates LiveSession document, sets up temp dir
 *   processAudioChunk()  → saves chunk, calls Whisper /transcribe-chunk,
 *                          appends to liveTranscript, quick-Gemini for topics
 *   endSession()         → merges chunks via FFmpeg, triggers full pipeline
 */
@Service
public class LiveSessionService {

    private static final Logger log = LoggerFactory.getLogger(LiveSessionService.class);

    private final LiveSessionRepository liveSessionRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRecordRepository classRecordRepository;
    private final ClassService classService;
    private final GeminiService geminiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient whisperClient;
    private final String geminiApiUrl;
    private final String geminiApiKey;

    /** Base directory where we create per-session temp folders */
    private final Path liveSessionsBase;

    public LiveSessionService(
            LiveSessionRepository liveSessionRepository,
            SubjectRepository subjectRepository,
            ClassRecordRepository classRecordRepository,
            ClassService classService,
            GeminiService geminiService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            @Value("${transcription.service.url:http://localhost:5000}") String whisperUrl,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}") String geminiApiUrl,
            @Value("${gemini.api.key:}") String geminiApiKey,
            @Value("${file.audio-dir}") String audioDir) {
        this.liveSessionRepository = liveSessionRepository;
        this.subjectRepository = subjectRepository;
        this.classRecordRepository = classRecordRepository;
        this.classService = classService;
        this.geminiService = geminiService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.whisperClient = WebClient.builder()
                .baseUrl(whisperUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
        this.geminiApiUrl = geminiApiUrl;
        this.geminiApiKey = geminiApiKey;
        // Store live session chunks in a sibling folder to the audio dir
        this.liveSessionsBase = Paths.get(audioDir).toAbsolutePath().normalize()
                .getParent().resolve("live-sessions");
        try {
            Files.createDirectories(this.liveSessionsBase);
        } catch (IOException e) {
            log.warn("Could not create live-sessions directory: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start a new live session for the given subject.
     *
     * @return the newly created LiveSession
     */
    public LiveSession startSession(String userId, String subjectId, String sessionName) {
        Subject subject = subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        // Create a unique temp directory for audio chunks
        String sessionDirName = "session-" + UUID.randomUUID();
        Path sessionDir = liveSessionsBase.resolve(sessionDirName);
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create session temp directory", e);
        }

        LiveSession session = LiveSession.builder()
                .userId(userId)
                .subjectId(subject.getId())
                .subject(subject.getName())
                .sessionName(sessionName != null && !sessionName.isBlank()
                        ? sessionName : subject.getName() + " — Live")
                .status("LIVE")
                .tempAudioDir(sessionDir.toString())
                .liveTranscript(new ArrayList<>())
                .liveTopics(new ArrayList<>())
                .liveKeyPoints(new ArrayList<>())
                .chunkCount(0)
                .approximateDurationSeconds(0)
                .build();

        session = liveSessionRepository.save(session);
        log.info("Live session {} started for user {} in subject {}", session.getId(), userId, subject.getName());
        return session;
    }

    /**
     * Process a single audio chunk received from the browser.
     *
     * Steps:
     *  1. Save chunk bytes to temp dir
     *  2. POST to Whisper /transcribe-chunk
     *  3. Append text to liveTranscript
     *  4. Quick Gemini analysis for topics/key-points
     *  5. Broadcast update over WebSocket
     *  6. Return result to the REST caller
     *
     * @return a map with keys: chunkText, liveTranscript, liveTopics, liveKeyPoints, chunkCount
     */
    public Map<String, Object> processAudioChunk(String sessionId, String userId, byte[] audioData, String contentType) {
        LiveSession session = getSessionForUser(sessionId, userId);

        if (!"LIVE".equals(session.getStatus())) {
            throw new RuntimeException("Session " + sessionId + " is not active (status: " + session.getStatus() + ")");
        }

        int chunkIndex = session.getChunkCount() + 1;
        String suffix = resolveSuffix(contentType);

        // 1. Save chunk
        Path chunkFile = Paths.get(session.getTempAudioDir())
                .resolve(String.format("chunk-%04d%s", chunkIndex, suffix));
        try {
            Files.write(chunkFile, audioData);
        } catch (IOException e) {
            throw new RuntimeException("Could not save audio chunk: " + e.getMessage(), e);
        }

        // 2. Transcribe via Whisper
        String chunkText = "";
        try {
            chunkText = transcribeChunk(chunkFile, suffix);
        } catch (Exception e) {
            log.warn("Whisper transcription failed for chunk {}/{}: {}", chunkIndex, sessionId, e.getMessage());
            chunkText = "[transcription unavailable]";
        }

        // 3. Update running transcript
        if (!chunkText.isBlank()) {
            session.getLiveTranscript().add(chunkText);
        }
        session.setChunkCount(chunkIndex);
        session.setApproximateDurationSeconds(chunkIndex * 5); // ~5s per chunk

        // 4. Quick Gemini analysis every 3 chunks (or first chunk)
        if (chunkIndex == 1 || chunkIndex % 3 == 0) {
            try {
                String joinedSoFar = String.join(" ", session.getLiveTranscript());
                if (!joinedSoFar.isBlank()) {
                    QuickAnalysisResult quick = quickGeminiAnalysis(joinedSoFar);
                    if (quick.topics != null && !quick.topics.isEmpty()) {
                        session.setLiveTopics(quick.topics);
                    }
                    if (quick.keyPoints != null && !quick.keyPoints.isEmpty()) {
                        session.setLiveKeyPoints(quick.keyPoints);
                    }
                }
            } catch (Exception e) {
                log.warn("Quick Gemini analysis failed for session {}: {}", sessionId, e.getMessage());
            }
        }

        session = liveSessionRepository.save(session);

        // 5. Broadcast over WebSocket
        Map<String, Object> update = buildUpdatePayload(session, chunkText);
        broadcastUpdate(sessionId, update);

        log.info("Processed chunk {}/{} ({} bytes, text: {} chars)",
                chunkIndex, sessionId, audioData.length, chunkText.length());

        return update;
    }

    /**
     * End the session: merge all audio chunks, create a ClassRecord and trigger full pipeline.
     *
     * @return the resultClassId (the ClassRecord that was created)
     */
    public String endSession(String sessionId, String userId) {
        LiveSession session = getSessionForUser(sessionId, userId);

        if (!"LIVE".equals(session.getStatus())) {
            throw new RuntimeException("Session " + sessionId + " is not active");
        }

        session.setStatus("ENDED");
        session.setEndedAt(Instant.now());
        session = liveSessionRepository.save(session);

        // Broadcast ended status
        broadcastUpdate(sessionId, Map.of("status", "ENDED", "message", "Session ended. Processing full pipeline..."));

        // Trigger async pipeline
        final LiveSession finalSession = session;
        triggerFullPipelineAsync(finalSession);

        return session.getId();
    }

    /**
     * Get current session state (for polling fallback).
     */
    public LiveSession getStatus(String sessionId, String userId) {
        return getSessionForUser(sessionId, userId);
    }

    /**
     * List all sessions for a user, newest first.
     */
    public List<LiveSession> listSessions(String userId) {
        return liveSessionRepository.findByUserId(userId).stream()
                .sorted((a, b) -> {
                    if (a.getStartedAt() == null) return 1;
                    if (b.getStartedAt() == null) return -1;
                    return b.getStartedAt().compareTo(a.getStartedAt());
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Async full pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void triggerFullPipelineAsync(LiveSession session) {
        String sessionId = session.getId();
        try {
            Path tempDir = Paths.get(session.getTempAudioDir());
            List<Path> chunks = getChunkFiles(tempDir);

            if (chunks.isEmpty()) {
                log.warn("No audio chunks found for session {}. Aborting pipeline.", sessionId);
                markSessionFailed(sessionId, "No audio chunks recorded");
                return;
            }

            // Merge all chunks into a single WAV file
            Path mergedPath = liveSessionsBase.resolve(sessionId + "_merged.wav");
            mergeChunksWithFFmpeg(chunks, mergedPath);
            session.setMergedAudioPath(mergedPath.toString());
            liveSessionRepository.save(session);
            log.info("Merged {} chunks for session {} → {}", chunks.size(), sessionId, mergedPath);

            // Create a ClassRequest and ClassRecord as if the user uploaded a file
            ClassRequest req = new ClassRequest();
            req.setSubjectId(session.getSubjectId());
            req.setClassName(session.getSessionName());
            req.setDescription("Recorded live on " + session.getStartedAt().toString().substring(0, 10));

            // Build ClassRecord directly (bypass multipart upload)
            ClassRecord record = ClassRecord.builder()
                    .subjectId(session.getSubjectId())
                    .subject(session.getSubject())
                    .className(session.getSessionName())
                    .description(req.getDescription())
                    .userId(session.getUserId())
                    .recordingPath(mergedPath.toString())
                    .originalFilename(session.getSessionName() + ".wav")
                    .status("UPLOADED")
                    .build();

            record = classRecordRepository.save(record);
            String classId = record.getId();

            // Link session → class
            session.setResultClassId(classId);
            liveSessionRepository.save(session);

            // Trigger the existing processing pipeline
            classService.processRecordingAsync(classId, mergedPath.toString());

            // Broadcast the classId so the frontend can redirect
            broadcastUpdate(sessionId, Map.of(
                    "status", "PIPELINE_STARTED",
                    "classId", classId,
                    "message", "Full processing pipeline started. You can view the class now."
            ));

            log.info("Full pipeline triggered for session {} → class {}", sessionId, classId);

        } catch (Exception e) {
            log.error("Full pipeline failed for session {}: {}", sessionId, e.getMessage(), e);
            markSessionFailed(sessionId, e.getMessage());
            broadcastUpdate(sessionId, Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private LiveSession getSessionForUser(String sessionId, String userId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Live session not found: " + sessionId));
        if (!userId.equals(session.getUserId())) {
            throw new AccessDeniedException("Access denied");
        }
        return session;
    }

    private String transcribeChunk(Path chunkFile, String suffix) {
        // Use multipart form upload to Whisper /transcribe-chunk
        try {
            byte[] fileBytes = Files.readAllBytes(chunkFile);
            String mimeType = suffix.equals(".webm") ? "audio/webm"
                    : suffix.equals(".ogg") ? "audio/ogg"
                    : "audio/wav";

            org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() { return "chunk" + suffix; }
                };

            String response = whisperClient.post()
                    .uri("/transcribe-chunk")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(new org.springframework.util.LinkedMultiValueMap<>(
                        Map.of("audio", List.of(
                            new org.springframework.http.HttpEntity<>(resource,
                                new org.springframework.http.HttpHeaders() {{
                                    setContentType(MediaType.parseMediaType(mimeType));
                                }})
                        ))
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isBlank()) return "";

            JsonNode node = objectMapper.readTree(response);
            JsonNode fullText = node.get("full_text");
            return fullText != null ? fullText.asText("").trim() : "";

        } catch (Exception e) {
            throw new RuntimeException("Whisper chunk transcription failed: " + e.getMessage(), e);
        }
    }

    private record QuickAnalysisResult(List<String> topics, List<String> keyPoints) {}

    private QuickAnalysisResult quickGeminiAnalysis(String transcriptSoFar) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return new QuickAnalysisResult(List.of(), List.of());
        }

        // Trim to last 8000 chars so the prompt stays small and cheap
        String text = transcriptSoFar.length() > 8000
                ? transcriptSoFar.substring(transcriptSoFar.length() - 8000)
                : transcriptSoFar;

        String prompt = "From this class transcript excerpt, return ONLY valid JSON with no markdown:\n" +
                "{\n" +
                "  \"topics\": [\"topic1\", \"topic2\", \"topic3\"],\n" +
                "  \"keyPoints\": [\"key point 1\", \"key point 2\", \"key point 3\"]\n" +
                "}\n" +
                "Rules: max 5 topics, max 5 key points, grounded only in the text, no extra text.\n\n" +
                "TRANSCRIPT:\n" + text;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 512
                )
        );

        String url = geminiApiUrl + "?key=" + geminiApiKey;

        try {
            String responseStr = WebClient.builder().build()
                    .post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseStr == null) return new QuickAnalysisResult(List.of(), List.of());

            JsonNode root = objectMapper.readTree(responseStr);
            String raw = root.at("/candidates/0/content/parts/0/text").asText("{}").trim();

            // Strip markdown fences if any
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }

            JsonNode result = objectMapper.readTree(raw);
            List<String> topics = objectMapper.convertValue(
                    result.get("topics"), new TypeReference<List<String>>() {});
            List<String> keyPoints = objectMapper.convertValue(
                    result.get("keyPoints"), new TypeReference<List<String>>() {});
            return new QuickAnalysisResult(
                    topics   != null ? topics    : List.of(),
                    keyPoints != null ? keyPoints : List.of()
            );
        } catch (Exception e) {
            log.warn("Quick Gemini parse failed: {}", e.getMessage());
            return new QuickAnalysisResult(List.of(), List.of());
        }
    }

    private void broadcastUpdate(String sessionId, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend("/topic/session/" + sessionId, payload);
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    private Map<String, Object> buildUpdatePayload(LiveSession session, String latestChunkText) {
        return Map.of(
                "sessionId",      session.getId(),
                "status",         session.getStatus(),
                "chunkCount",     session.getChunkCount(),
                "latestChunk",    latestChunkText,
                "liveTranscript", String.join(" ", session.getLiveTranscript()),
                "liveTopics",     session.getLiveTopics(),
                "liveKeyPoints",  session.getLiveKeyPoints(),
                "durationSeconds", session.getApproximateDurationSeconds()
        );
    }

    private void mergeChunksWithFFmpeg(List<Path> chunks, Path outputPath) throws IOException, InterruptedException {
        if (chunks.isEmpty()) {
            throw new RuntimeException("No chunks to merge");
        }

        // Single chunk — just convert directly, no concat needed
        if (chunks.size() == 1) {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", chunks.get(0).toString(),
                "-ar", "16000", "-ac", "1", "-vn",
                outputPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) { process.destroyForcibly(); throw new RuntimeException("FFmpeg convert timed out"); }
            if (process.exitValue() != 0) throw new RuntimeException("FFmpeg convert failed: " + output);
            return;
        }

        // Multiple chunks — use filter_complex concat (works with WebM/Ogg/WAV)
        // Build: ffmpeg -i chunk1 -i chunk2 ... -filter_complex "[0:a][1:a]...concat=n=N:v=0:a=1[out]" -map "[out]" ...
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        for (Path chunk : chunks) {
            cmd.add("-i");
            cmd.add(chunk.toString());
        }

        // Build the filter_complex string: [0:a][1:a][2:a]...concat=n=N:v=0:a=1[out]
        StringBuilder filterComplex = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            filterComplex.append("[").append(i).append(":a]");
        }
        filterComplex.append("concat=n=").append(chunks.size()).append(":v=0:a=1[out]");

        cmd.addAll(List.of(
            "-filter_complex", filterComplex.toString(),
            "-map", "[out]",
            "-ar", "16000",
            "-ac", "1",
            outputPath.toString()
        ));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = readProcessOutput(process);
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg merge timed out");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("FFmpeg merge failed: " + output);
        }
    }

    private List<Path> getChunkFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("chunk-"))
                    .sorted()
                    .toList();
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveSuffix(String contentType) {
        if (contentType == null) return ".webm";
        if (contentType.contains("webm")) return ".webm";
        if (contentType.contains("ogg"))  return ".ogg";
        if (contentType.contains("wav"))  return ".wav";
        if (contentType.contains("mp4"))  return ".mp4";
        return ".webm"; // default for browser MediaRecorder
    }

    private void markSessionFailed(String sessionId, String reason) {
        liveSessionRepository.findById(sessionId).ifPresent(s -> {
            s.setStatus("FAILED");
            s.setFailureReason(reason);
            liveSessionRepository.save(s);
        });
    }
}
