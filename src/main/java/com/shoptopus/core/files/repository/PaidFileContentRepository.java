package com.shoptopus.core.files.repository;

import com.shoptopus.core.files.PaidFileContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaidFileContentRepository extends JpaRepository<PaidFileContent, UUID> {
    List<PaidFileContent> findByFileableTypeAndFileableId(String fileableType, UUID fileableId);
}
