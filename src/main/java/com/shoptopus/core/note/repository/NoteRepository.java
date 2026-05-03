package com.shoptopus.core.note.repository;

import com.shoptopus.core.note.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<Note> findByIdAndDeletedAtIsNull(UUID id);
}
