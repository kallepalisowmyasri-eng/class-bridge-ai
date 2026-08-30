package com.aiclassrecovery.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private final Path audioDir;

    public MediaProcessingService(@Value("${file.audio-dir}") String audioDir) {
        this.audioDir = Paths.get(audioDir);
        try {
            Files.createDirectories(this.audioDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create audio directory", e);
        }
    }

    /**
     * Extracts audio from a video/audio file and converts to 16kHz mono WAV (Whisper-friendly).
     * Returns the path to the extracted WAV file.
     */
    public String extractAudio(String inputFilePath, String classId) {
        Path input = Paths.get(inputFilePath).normalize();
        if (!Files.exists(input)) {
            throw new RuntimeException("Input file not found: " + inputFilePath);
        }
        if (!input.toAbsolutePath().startsWith(audioDir.toAbsolutePath().normalize()) &&
            !input.toAbsolutePath().startsWith(Paths.get(System.getProperty("user.home")).resolve("classbridge").toAbsolutePath())) {
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
