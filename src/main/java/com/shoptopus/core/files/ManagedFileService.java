package com.shoptopus.core.files;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.repository.FileContentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ManagedFileService {
    private static final int MAX_IMAGE_DIMENSION = 1800;
    private static final float JPEG_QUALITY = 0.7f;

    private final FileContentRepository fileContentRepository;
    private final Path storageRoot;
    private final String publicBaseUrl;

    public ManagedFileService(
            FileContentRepository fileContentRepository,
            @Value("${app.media.storage-path}") String storagePath,
            @Value("${app.media.public-base-url:/media}") String publicBaseUrl
    ) {
        this.fileContentRepository = fileContentRepository;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional("mainTransactionManager")
    public FileContent createUploadedFile(String name, String originalName, String mimeType, Long size, String path, String type, String fileableType, UUID fileableId) {
        return createUploadedFile(name, originalName, mimeType, size, path, type, fileableType, fileableId, 0);
    }

    @Transactional("mainTransactionManager")
    public FileContent createUploadedFile(String name, String originalName, String mimeType, Long size, String path, String type, String fileableType, UUID fileableId, Integer sortOrder) {
        FileContent file = new FileContent();
        file.setId(UUID.randomUUID());
        file.setName(name);
        file.setOriginalName(originalName);
        file.setMimeType(mimeType);
        file.setSize(size);
        file.setPath(path);
        file.setSlug("file-" + file.getId());
        file.setType(type);
        file.setFileableType(fileableType);
        file.setFileableId(fileableId);
        file.setSortOrder(sortOrder == null ? 0 : sortOrder);
        refreshImageDimensions(file);
        return fileContentRepository.save(file);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> attachSingle(String fileableType, UUID fileableId, String type, Map<String, Object> filePayload) {
        if (filePayload == null || filePayload.isEmpty()) {
            clearFor(fileableType, fileableId, type);
            return null;
        }

        UUID fileId = parseUuid(filePayload.get("id"))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));
        FileContent file = fileContentRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));

        List<FileContent> existing = fileContentRepository.findByFileableTypeAndFileableIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId, type);
        for (FileContent current : existing) {
            if (!current.getId().equals(fileId)) {
                deleteFile(current);
            }
        }

        file.setType(type);
        file.setFileableType(fileableType);
        file.setFileableId(fileableId);
        file.setSortOrder(0);
        fileContentRepository.save(file);
        return toPayload(file);
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> syncOrderedSelection(
            String fileableType,
            UUID fileableId,
            String regularType,
            String selectedType,
            List<Map<String, Object>> orderedFiles,
            UUID selectedId
    ) {
        List<FileContent> existing = fileContentRepository
                .findByFileableTypeAndFileableIdAndTypeInAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
                        fileableType, fileableId, List.of(regularType, selectedType));

        Map<UUID, FileContent> currentById = new LinkedHashMap<>();
        existing.forEach(file -> currentById.put(file.getId(), file));

        List<UUID> targetIds = new ArrayList<>();
        if (orderedFiles != null) {
            for (Map<String, Object> filePayload : orderedFiles) {
                UUID fileId = parseUuid(filePayload == null ? null : filePayload.get("id"))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));
                targetIds.add(fileId);
            }
        }

        if (selectedId == null && !targetIds.isEmpty()) {
            selectedId = targetIds.get(0);
        }
        if (selectedId != null && !targetIds.contains(selectedId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected cover photo is invalid.");
        }

        for (FileContent current : existing) {
            if (!targetIds.contains(current.getId())) {
                deleteFile(current);
            }
        }

        List<FileContent> saved = new ArrayList<>();
        for (int index = 0; index < targetIds.size(); index++) {
            UUID fileId = targetIds.get(index);
            FileContent file = fileContentRepository.findByIdAndDeletedAtIsNull(fileId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));
            file.setFileableType(fileableType);
            file.setFileableId(fileableId);
            file.setType(fileId.equals(selectedId) ? selectedType : regularType);
            file.setSortOrder(index);
            saved.add(fileContentRepository.save(file));
        }

        saved.sort(Comparator.comparing(FileContent::getSortOrder).thenComparing(FileContent::getCreatedAt));
        return saved.stream().map(this::toPayload).toList();
    }

    @Transactional("mainTransactionManager")
    public List<Map<String, Object>> syncFiles(String fileableType, UUID fileableId, String type, List<Map<String, Object>> orderedFiles) {
        List<FileContent> existing = fileContentRepository
                .findByFileableTypeAndFileableIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId, type);

        List<UUID> targetIds = new ArrayList<>();
        if (orderedFiles != null) {
            for (Map<String, Object> filePayload : orderedFiles) {
                UUID fileId = parseUuid(filePayload == null ? null : filePayload.get("id"))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));
                targetIds.add(fileId);
            }
        }

        for (FileContent current : existing) {
            if (!targetIds.contains(current.getId())) {
                deleteFile(current);
            }
        }

        List<FileContent> saved = new ArrayList<>();
        for (int index = 0; index < targetIds.size(); index++) {
            UUID fileId = targetIds.get(index);
            FileContent file = fileContentRepository.findByIdAndDeletedAtIsNull(fileId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is invalid."));
            file.setFileableType(fileableType);
            file.setFileableId(fileableId);
            file.setType(type);
            file.setSortOrder(index);
            saved.add(fileContentRepository.save(file));
        }

        saved.sort(Comparator.comparing(FileContent::getSortOrder).thenComparing(FileContent::getCreatedAt));
        return saved.stream().map(this::toPayload).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> filesFor(String fileableType, UUID fileableId) {
        return fileContentRepository.findByFileableTypeAndFileableIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId).stream()
                .map(this::toPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> filesFor(String fileableType, UUID fileableId, List<String> types) {
        return fileContentRepository.findByFileableTypeAndFileableIdAndTypeInAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId, types).stream()
                .map(this::toPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<Map<String, Object>>> filesForMany(String fileableType, List<UUID> fileableIds, List<String> types) {
        if (fileableIds == null || fileableIds.isEmpty()) {
            return Map.of();
        }
        return fileContentRepository.findByFileableTypeAndFileableIdInAndTypeInAndDeletedAtIsNullOrderByFileableIdAscSortOrderAscCreatedAtAsc(
                        fileableType,
                        fileableIds,
                        types
                ).stream()
                .collect(Collectors.groupingBy(
                        FileContent::getFileableId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toPayload, Collectors.toList())
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findAttachedSingle(String fileableType, UUID fileableId, String type) {
        return fileContentRepository.findByFileableTypeAndFileableIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId, type).stream()
                .reduce((first, second) -> second)
                .map(this::toPayload)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Map<String, Object>> findAttachedSingles(String fileableType, List<UUID> fileableIds, String type) {
        if (fileableIds == null || fileableIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Object>> singles = new LinkedHashMap<>();
        fileContentRepository.findByFileableTypeAndFileableIdInAndTypeAndDeletedAtIsNullOrderByFileableIdAscSortOrderAscCreatedAtAsc(
                        fileableType,
                        fileableIds,
                        type
                )
                .forEach(file -> singles.put(file.getFileableId(), toPayload(file)));
        return singles;
    }

    @Transactional("mainTransactionManager")
    public void clearFor(String fileableType, UUID fileableId) {
        List<FileContent> files = fileContentRepository.findByFileableTypeAndFileableIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId);
        files.forEach(this::deleteFile);
    }

    @Transactional("mainTransactionManager")
    public void clearFor(String fileableType, UUID fileableId, String type) {
        List<FileContent> files = fileContentRepository.findByFileableTypeAndFileableIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(fileableType, fileableId, type);
        files.forEach(this::deleteFile);
    }

    @Transactional("mainTransactionManager")
    public void deleteById(UUID fileId) {
        fileContentRepository.findByIdAndDeletedAtIsNull(fileId).ifPresent(this::deleteFile);
    }

    @Transactional("mainTransactionManager")
    public FileContent optimizeImage(FileContent file) {
        if (file.getMimeType() == null || !file.getMimeType().toLowerCase().startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file is not an image.");
        }

        Path source = resolveStoredPath(file.getPath());
        if (!Files.exists(source)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found", "The stored file could not be found.");
        }

        try {
            OptimizedImage optimized = optimizeImageBytes(Files.readAllBytes(source));
            String nextRelativePath = stripExtension(file.getPath()) + ".jpg";
            Path target = resolveStoredPath(nextRelativePath);
            if (!target.startsWith(storageRoot)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid media path.");
            }
            Files.createDirectories(target.getParent());
            Files.write(target, optimized.bytes());
            if (!target.equals(source)) {
                Files.deleteIfExists(source);
            }
            file.setPath(nextRelativePath);
            file.setMimeType("image/jpeg");
            file.setSize((long) optimized.bytes().length);
            applyImageDimensions(file, optimized.dimensions());
            return fileContentRepository.save(file);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to optimise image", "Unable to optimise the selected image.");
        }
    }

    public StoredImage optimizeImageUpload(byte[] sourceBytes, String originalName, String safeFolder) {
        try {
            OptimizedImage optimized = optimizeImageBytes(sourceBytes);
            String fileName = UUID.randomUUID() + ".jpg";
            Path targetDirectory = storageRoot.resolve(safeFolder).resolve(java.time.LocalDate.now().toString()).normalize();
            Path targetFile = targetDirectory.resolve(fileName).normalize();
            if (!targetFile.startsWith(storageRoot)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid media folder.");
            }
            Files.createDirectories(targetDirectory);
            Files.write(targetFile, optimized.bytes());
            String relativePath = storageRoot.relativize(targetFile).toString().replace('\\', '/');
            return new StoredImage(
                    stripExtension(originalName),
                    originalName,
                    "image/jpeg",
                    (long) optimized.bytes().length,
                    relativePath,
                    fileName,
                    safeFolder,
                    optimized.dimensions().width(),
                    optimized.dimensions().height()
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to upload image", "Unable to store uploaded image");
        }
    }

    public void refreshImageDimensions(FileContent file) {
        file.setWidth(null);
        file.setHeight(null);
        storedImageDimensions(file.getPath(), file.getMimeType())
                .ifPresent(dimensions -> applyImageDimensions(file, dimensions));
    }

    public Optional<ImageDimensions> imageDimensions(FileContent file) {
        if (file == null || file.getMimeType() == null || !file.getMimeType().toLowerCase().startsWith("image/")) {
            return Optional.empty();
        }
        if (file.getWidth() != null && file.getHeight() != null) {
            return Optional.of(new ImageDimensions(file.getWidth(), file.getHeight()));
        }
        return storedImageDimensions(file.getPath(), file.getMimeType());
    }

    private Optional<ImageDimensions> storedImageDimensions(String path, String mimeType) {
        if (path == null || path.isBlank() || mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
            return Optional.empty();
        }
        try {
            Path source = resolveStoredPath(path);
            if (!Files.exists(source)) {
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(source.toFile());
            if (image == null) {
                return Optional.empty();
            }
            return Optional.of(new ImageDimensions(image.getWidth(), image.getHeight()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public Map<String, Object> toPayload(FileContent file) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", file.getId());
        payload.put("name", file.getName());
        payload.put("original_name", file.getOriginalName());
        payload.put("mime_type", file.getMimeType());
        payload.put("size", file.getSize());
        payload.put("path", file.getPath());
        payload.put("url", publicUrl(file.getPath()));
        payload.put("slug", file.getSlug());
        payload.put("type", file.getType());
        payload.put("fileable_type", file.getFileableType());
        payload.put("fileable_id", file.getFileableId());
        payload.put("sort_order", file.getSortOrder());
        if (file.getWidth() != null && file.getHeight() != null) {
            payload.put("width", file.getWidth());
            payload.put("height", file.getHeight());
        }
        return payload;
    }

    public String publicUrl(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return storedPath;
        }
        if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
            return storedPath;
        }

        String normalizedBase = normalizedPublicBase();
        if (storedPath.startsWith(normalizedBase + "/") || storedPath.equals(normalizedBase)) {
            return storedPath;
        }
        if (storedPath.startsWith("/")) {
            return storedPath;
        }
        return normalizedBase + "/" + storedPath.replaceFirst("^/+", "");
    }

    public void deleteFile(FileContent file) {
        deletePhysicalFile(file.getPath());
        fileContentRepository.delete(file);
    }

    private void deletePhysicalFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        Path file = resolveStoredPath(storedPath);
        if (!file.startsWith(storageRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    private Optional<UUID> parseUuid(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(String.valueOf(value)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Path resolveStoredPath(String storedPath) {
        String normalizedBase = normalizedPublicBase();
        String relative = storedPath;
        if (storedPath.startsWith(normalizedBase + "/")) {
            relative = storedPath.substring(normalizedBase.length());
        }
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path candidate = Path.of(relative);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return storageRoot.resolve(relative).normalize();
    }

    private String normalizedPublicBase() {
        String base = publicBaseUrl == null ? "/media" : publicBaseUrl.trim();
        String normalizedBase = base.startsWith("/") ? base : "/" + base;
        return normalizedBase.replaceAll("/+$", "");
    }

    private OptimizedImage optimizeImageBytes(byte[] sourceBytes) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (source == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected file must be a supported image.");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth = sourceWidth;
        int targetHeight = sourceHeight;
        if (sourceWidth > MAX_IMAGE_DIMENSION || sourceHeight > MAX_IMAGE_DIMENSION) {
            double scale = Math.min((double) MAX_IMAGE_DIMENSION / sourceWidth, (double) MAX_IMAGE_DIMENSION / sourceHeight);
            targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
            targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        }

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to optimise image", "No JPEG writer is available.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(target, null, null), params);
            writer.dispose();
            return new OptimizedImage(output.toByteArray(), new ImageDimensions(targetWidth, targetHeight));
        }
    }

    private void applyImageDimensions(FileContent file, ImageDimensions dimensions) {
        if (file == null || dimensions == null) {
            return;
        }
        file.setWidth(dimensions.width());
        file.setHeight(dimensions.height());
    }

    private String stripExtension(String value) {
        if (value == null) {
            return "";
        }
        int index = value.lastIndexOf('.');
        return index > 0 ? value.substring(0, index) : value;
    }

    public record StoredImage(
            String displayName,
            String originalName,
            String contentType,
            long size,
            String relativePath,
            String fileName,
            String folder,
            int width,
            int height
    ) {
    }

    private record OptimizedImage(byte[] bytes, ImageDimensions dimensions) {
    }

    public record ImageDimensions(int width, int height) {
    }
}
