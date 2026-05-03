package com.shoptopus.core.content.repository;

import com.shoptopus.core.content.Content;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {
    List<Content> findByContentableTypeAndContentableId(String contentableType, UUID contentableId);
}
