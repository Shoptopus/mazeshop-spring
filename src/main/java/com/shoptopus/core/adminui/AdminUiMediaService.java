package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.FileContent;
import com.shoptopus.core.files.ManagedFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiMediaService {

    private final Path storageRoot;
    private final ManagedFileService managedFileService;

    public AdminUiMediaService(
            @Value("${app.media.storage-path}") String storagePath,
            ManagedFileService managedFileService
    ) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.managedFileService = managedFileService;
    }

    public Map<String, Object> uploadImage(MultipartFile file, String folder) {
        StoredMedia stored = storeUpload(file, folder, true);
        FileContent saved = managedFileService.createUploadedFile(
                stored.displayName(),
                stored.originalName(),
                stored.contentType(),
                stored.size(),
                stored.relativePath(),
                stored.folder(),
                null,
                null
        );

        Map<String, Object> payload = new LinkedHashMap<>(managedFileService.toPayload(saved));
        payload.put("file_name", stored.fileName());
        payload.put("folder", stored.folder());
        return payload;
    }

    public Map<String, Object> uploadVideo(MultipartFile file, String folder) {
        return uploadVideo(file, folder, "product");
    }

    public Map<String, Object> uploadVideo(MultipartFile file, String folder, String profile) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The file field is required.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("video/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file must be a video.");
        }

        String safeFolder = sanitizeFolder(folder);
        String originalName = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        Path tempInput = null;
        Path targetFile = null;
        boolean success = false;
        try {
            tempInput = Files.createTempFile("admin-ui-video-", extension(originalName, contentType));
            try (InputStream stream = file.getInputStream()) {
                Files.copy(stream, tempInput, StandardCopyOption.REPLACE_EXISTING);
            }

            String fileName = UUID.randomUUID() + ".mp4";
            Path targetDirectory = storageRoot.resolve(safeFolder).resolve(LocalDate.now().toString()).normalize();
            targetFile = targetDirectory.resolve(fileName).normalize();
            if (!targetFile.startsWith(storageRoot)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid media folder.");
            }
            Files.createDirectories(targetDirectory);

            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add(tempInput.toString());
            command.addAll(commandProfile(profile, targetFile.toString()));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

            String ffmpegOutput;
            try (InputStream output = process.getInputStream()) {
                ffmpegOutput = new String(output.readAllBytes());
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process video", ffmpegOutput.isBlank() ? "ffmpeg failed" : ffmpegOutput.trim());
            }

            String relativePath = storageRoot.relativize(targetFile).toString().replace('\\', '/');
            FileContent saved = managedFileService.createUploadedFile(
                    stripExtension(originalName),
                    originalName,
                    "video/mp4",
                    Files.size(targetFile),
                    relativePath,
                    safeFolder,
                    null,
                    null
            );

            Map<String, Object> payload = new LinkedHashMap<>(managedFileService.toPayload(saved));
            payload.put("file_name", fileName);
            payload.put("folder", safeFolder);
            payload.put("processed", true);
            if (!"content".equalsIgnoreCase(profile)) {
                payload.put("duration_seconds", 15);
            }
            success = true;
            return payload;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to upload video", "Unable to store uploaded video");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process video", "Video processing interrupted");
        } finally {
            if (tempInput != null) {
                try {
                    Files.deleteIfExists(tempInput);
                } catch (IOException ignored) {
                }
            }
            if (!success && targetFile != null) {
                try {
                    Files.deleteIfExists(targetFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public Map<String, Object> uploadFile(MultipartFile file, String folder) {
        StoredMedia stored = storeUpload(file, folder, false);
        FileContent saved = managedFileService.createUploadedFile(
                stored.displayName(),
                stored.originalName(),
                stored.contentType(),
                stored.size(),
                stored.relativePath(),
                safeFileType(stored.folder()),
                null,
                null
        );

        Map<String, Object> payload = new LinkedHashMap<>(managedFileService.toPayload(saved));
        payload.put("file_name", stored.fileName());
        payload.put("folder", stored.folder());
        return payload;
    }

    private StoredMedia storeUpload(MultipartFile file, String folder, boolean requireImage) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The file field is required.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (requireImage && !contentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file must be an image.");
        }

        String safeFolder = sanitizeFolder(folder);
        String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        if (requireImage) {
            try {
                var stored = managedFileService.optimizeImageUpload(file.getBytes(), originalName, safeFolder);
                return new StoredMedia(
                        stored.displayName(),
                        stored.originalName(),
                        stored.contentType(),
                        stored.size(),
                        stored.relativePath(),
                        stored.fileName(),
                        stored.folder()
                );
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to upload image", "Unable to store uploaded image");
            }
        }
        String extension = extension(originalName, contentType);
        String fileName = UUID.randomUUID() + extension;
        Path targetDirectory = storageRoot.resolve(safeFolder).resolve(LocalDate.now().toString()).normalize();
        Path targetFile = targetDirectory.resolve(fileName).normalize();
        if (!targetFile.startsWith(storageRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid media folder.");
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to upload image", "Unable to store uploaded image");
        }

        String relativePath = storageRoot.relativize(targetFile).toString().replace('\\', '/');
        return new StoredMedia(stripExtension(originalName), originalName, contentType, file.getSize(), relativePath, fileName, safeFolder);
    }

    private String sanitizeFolder(String folder) {
        String value = folder == null || folder.isBlank() ? "general" : folder.trim().toLowerCase(Locale.ROOT);
        String normalized = value.replace('\\', '/');
        normalized = normalized.replaceAll("[^a-z0-9/_-]", "-");
        normalized = normalized.replaceAll("/+", "/");
        normalized = normalized.replaceAll("(^/|/$)", "");
        return normalized.isBlank() ? "general" : normalized;
    }

    private String safeFileType(String folder) {
        return folder == null || folder.isBlank() ? "general" : folder;
    }

    private List<String> commandProfile(String profile, String targetPath) {
        if ("content".equalsIgnoreCase(profile)) {
            return List.of(
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart",
                    targetPath
            );
        }
        if ("banner".equalsIgnoreCase(profile)) {
            return List.of(
                    "-an",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart",
                    targetPath
            );
        }
        if ("review".equalsIgnoreCase(profile)) {
            return List.of(
                    "-t", "10",
                    "-an",
                    "-vf", "scale=1280:720:force_original_aspect_ratio=decrease",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-movflags", "+faststart",
                    targetPath
            );
        }
        return List.of(
                "-t", "15",
                "-an",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                targetPath
        );
    }

    private String extension(String originalName, String contentType) {
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0 && idx < originalName.length() - 1) {
            return originalName.substring(idx).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> ".bin";
        };
    }

    private String stripExtension(String originalName) {
        String normalized = Normalizer.normalize(originalName, Normalizer.Form.NFKC);
        int idx = normalized.lastIndexOf('.');
        return idx > 0 ? normalized.substring(0, idx) : normalized;
    }

    private record StoredMedia(
            String displayName,
            String originalName,
            String contentType,
            long size,
            String relativePath,
            String fileName,
            String folder
    ) {
    }
}
