package com.aiclassrecovery.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mp3", "wav", "m4a");
    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024; // 2GB

    private final Path uploadDir;
    private final Path audioDir;

    public FileService(@Value("${file.upload-dir}") String uploadDir,
                       @Value("${file.audio-dir}") String audioDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.audioDir = Paths.get(audioDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            Files.createDirectories(this.audioDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    public String saveRecording(String classId, MultipartFile file) {
        validateFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String filename = classId + "_" + UUID.randomUUID() + "." + extension;
        Path destination = uploadDir.resolve(filename);

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }

        return destination.toString();
    }

    public Resource loadRecording(String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(uploadDir) && !path.startsWith(audioDir)) {
            throw new RuntimeException("Access denied");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists()) {
                return resource;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file", e);
        }
        throw new RuntimeException("File not found: " + filePath);
    }

    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new RuntimeException("File exceeds 2GB limit");
        }

        String ext = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new RuntimeException("Unsupported file type: ." + ext + ". Allowed: mp4, mp3, wav, m4a");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new RuntimeException("Invalid filename");
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
