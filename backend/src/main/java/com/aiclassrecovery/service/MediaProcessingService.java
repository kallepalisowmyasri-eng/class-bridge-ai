package com.aiclassrecovery.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private final Path audioDir;
    private final Path uploadDir;

    public MediaProcessingService(@Value("${file.audio-dir}") String audioDir,
                                  @Value("${file.upload-dir}") String uploadDir) {
        this.audioDir = Paths.get(audioDir).toAbsolutePath().normalize();
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.audioDir);
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create audio directory", e);
        }
    }

    /**
     * Extracts audio from a video/audio file and converts to 16kHz mono WAV (Whisper-friendly).
     * Returns the path to the extracted WAV file.
     */
    public String extractAudio(String inputFilePath, String classId) {
        Path input = Paths.get(inputFilePath).toAbsolutePath().normalize();
        if (!Files.exists(input)) {
            throw new RuntimeException("Input file not found: " + inputFilePath);
        }
        if (!input.startsWith(audioDir) &&
            !input.startsWith(uploadDir) &&
            !input.startsWith(Paths.get(System.getProperty("user.home")).resolve("classbridge").toAbsolutePath().normalize())) {
            throw new RuntimeException("Input file not in allowed directory");
        }

        String outputFilename = classId + "_audio.wav";
        Path outputPath = audioDir.resolve(outputFilename);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", input.toString(),
                "-ar", "16000",
                "-ac", "1",
                "-vn",
                outputPath.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg audio extraction timed out after 30 minutes");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.error("FFmpeg failed with exit code {}: {}", exitCode, output);
                throw new RuntimeException("FFmpeg audio extraction failed: " + output);
            }

            log.info("Audio extracted successfully: {}", outputPath);
            return outputPath.toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to run FFmpeg. Is it installed?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Audio extraction was interrupted", e);
        }
    }

    /**
     * Gets the duration of a media file in seconds using ffprobe.
     */
    public int getDuration(String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                log.warn("ffprobe timed out after 5 minutes");
                return 0;
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.warn("ffprobe failed with exit code {}: {}", exitCode, output);
                return 0;
            }

            String trimmed = output.trim();
            if (trimmed.isEmpty()) {
                return 0;
            }

            double seconds = Double.parseDouble(trimmed);
            return (int) Math.round(seconds);

        } catch (IOException e) {
            log.warn("Failed to run ffprobe. Is it installed?", e);
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (NumberFormatException e) {
            log.warn("Could not parse duration from ffprobe output", e);
            return 0;
        }
    }

    /**
     * Checks if the media file has a video stream using ffprobe.
     */
    public boolean hasVideoStream(String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_type",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(1, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0 && output.trim().equalsIgnoreCase("video");
        } catch (Exception e) {
            log.warn("Failed to check for video stream", e);
            return false;
        }
    }

    /**
     * Extracts keyframes from the video using scene detection.
     */
    public List<String> extractFrames(String inputFilePath, String classId) {
        Path input = Paths.get(inputFilePath).toAbsolutePath().normalize();
        if (!Files.exists(input)) {
            throw new RuntimeException("Input file not found: " + inputFilePath);
        }

        Path framesDir = audioDir.resolve("frames").resolve(classId);
        try {
            Files.createDirectories(framesDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create frames directory", e);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", input.toString(),
                "-vf", "scale=-1:480,select=gt(scene\\,0.02)",
                "-vsync", "vfr",
                "-q:v", "5",
                framesDir.resolve("frame_%04d.jpg").toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                log.warn("FFmpeg scene-based frame extraction timed out for class {}", classId);
            }

            List<String> frames = getJpgFrames(framesDir);

            // Fallback 1: If scene detection yielded 0 frames (e.g. static slides presentation), extract periodic frames (1 per minute)
            if (frames.isEmpty()) {
                log.info("Scene detection yielded 0 frames for class {}. Falling back to periodic extraction (1 frame/60s)...", classId);
                ProcessBuilder pbFallback = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", input.toString(),
                    "-vf", "scale=-1:480,fps=1/60",
                    "-vsync", "vfr",
                    "-q:v", "5",
                    framesDir.resolve("frame_%04d.jpg").toString()
                );
                pbFallback.redirectErrorStream(true);
                Process procFb = pbFallback.start();
                readProcessOutput(procFb);
                procFb.waitFor(5, TimeUnit.MINUTES);
                frames = getJpgFrames(framesDir);
            }

            // Fallback 2: If still empty (e.g. video < 60s), capture at least 1 keyframe
            if (frames.isEmpty()) {
                log.info("Periodic extraction yielded 0 frames for class {}. Capturing initial frame...", classId);
                ProcessBuilder pbSingle = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", input.toString(),
                    "-vf", "scale=-1:480",
                    "-vframes", "1",
                    "-q:v", "5",
                    framesDir.resolve("frame_0001.jpg").toString()
                );
                pbSingle.redirectErrorStream(true);
                Process procSingle = pbSingle.start();
                readProcessOutput(procSingle);
                procSingle.waitFor(1, TimeUnit.MINUTES);
                frames = getJpgFrames(framesDir);
            }

            // Cap to 30 frames by uniformly sampling
            if (frames.size() > 30) {
                List<String> sampled = new ArrayList<>();
                double step = (double) frames.size() / 30;
                for (int i = 0; i < 30; i++) {
                    sampled.add(frames.get((int) (i * step)));
                }
                frames = sampled;
            }
            
            log.info("Extracted {} keyframes for class {}", frames.size(), classId);
            return frames;

        } catch (Exception e) {
            log.warn("Failed to extract frames for class {}: {}", classId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getJpgFrames(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(file -> !Files.isDirectory(file) && file.toString().toLowerCase().endsWith(".jpg"))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void deleteAudio(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("Failed to delete audio file: {}", filePath, e);
        }
    }
    
    public void deleteFrames(String classId) {
        if (classId == null || classId.isBlank()) return;
        Path framesDir = audioDir.resolve("frames").resolve(classId);
        if (Files.exists(framesDir)) {
            try {
                try (Stream<Path> walk = Files.walk(framesDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
            } catch (IOException e) {
                log.warn("Failed to delete frames directory for class: {}", classId, e);
            }
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
}
