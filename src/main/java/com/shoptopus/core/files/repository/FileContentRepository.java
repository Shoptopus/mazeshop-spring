package com.shoptopus.core.files.repository;

import com.shoptopus.core.files.FileContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileContentRepository extends JpaRepository<FileContent, UUID> {

    List<FileContent> findByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<FileContent> findByIdAndDeletedAtIsNull(UUID id);

    List<FileContent> findByFileableTypeAndFileableIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(String fileableType, UUID fileableId);

    List<FileContent> findByFileableTypeAndFileableIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(String fileableType, UUID fileableId, String type);

    List<FileContent> findByFileableTypeAndFileableIdAndTypeInAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(String fileableType, UUID fileableId, List<String> types);

    List<FileContent> findByFileableTypeAndFileableIdInAndTypeInAndDeletedAtIsNullOrderByFileableIdAscSortOrderAscCreatedAtAsc(String fileableType, List<UUID> fileableIds, List<String> types);

    List<FileContent> findByFileableTypeAndFileableIdInAndTypeAndDeletedAtIsNullOrderByFileableIdAscSortOrderAscCreatedAtAsc(String fileableType, List<UUID> fileableIds, String type);
}
