package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.note.Note;
import com.shoptopus.core.note.repository.NoteRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminNoteService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;

    public AdminNoteService(NoteRepository noteRepository, UserRepository userRepository) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = noteRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "notes");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    public Map<String, Object> show(String id) {
        Optional<UUID> noteId = uuid(id);
        if (noteId.isPresent()) {
            Optional<Note> note = noteRepository.findByIdAndDeletedAtIsNull(noteId.orElseThrow());
            if (note.isPresent()) {
                return Map.of(
                        "message", "note show",
                        "data", toRow(note.orElseThrow())
                );
            }
        }
        return Map.of(
                "message", "note show",
                "data", Map.of("id", id)
        );
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        String text = requireText(body);
        Note note = new Note();
        note.setId(UUID.randomUUID());
        note.setUser(resolveUser(body.get("user_id")).orElse(null));
        note.setNoteableType(str(body, "noteable_type"));
        note.setNoteableId(uuid(str(body, "noteable_id")).orElse(null));
        note.setText(text);
        note.setSlug(defaultSlug(body, note.getId()));
        noteRepository.save(note);
        return Map.of("id", note.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID noteId = uuid(id).orElse(null);
        if (noteId == null) {
            return Map.of("id", id);
        }
        String text = requireText(body);
        Optional<Note> note = noteRepository.findByIdAndDeletedAtIsNull(noteId);
        if (note.isEmpty()) {
            return Map.of("id", noteId);
        }
        Note entity = note.orElseThrow();
        entity.setText(text);
        if (body != null && body.containsKey("user_id")) {
            entity.setUser(resolveUser(body.get("user_id")).orElse(null));
        }
        if (body != null && body.containsKey("noteable_type")) {
            entity.setNoteableType(str(body, "noteable_type"));
        }
        if (body != null && body.containsKey("noteable_id")) {
            entity.setNoteableId(uuid(str(body, "noteable_id")).orElse(null));
        }
        if (body != null && body.containsKey("slug")) {
            entity.setSlug(defaultSlug(body, entity.getId()));
        }
        noteRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID noteId = uuid(id).orElse(null);
        if (noteId == null) {
            return Map.of("id", id);
        }
        noteRepository.findByIdAndDeletedAtIsNull(noteId).ifPresent(note -> {
            note.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            noteRepository.save(note);
        });
        return Map.of("id", noteId);
    }

    private Map<String, Object> toRow(Note note) {
        User user = note.getUser();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", note.getId());
        row.put("user_id", user == null ? null : user.getId());
        if (user == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", user.getId());
            userRow.put("email", user.getEmail());
            userRow.put("first_name", user.getFirstName());
            userRow.put("last_name", user.getLastName());
            row.put("user", userRow);
        }
        row.put("noteable_type", note.getNoteableType());
        row.put("noteable_id", note.getNoteableId());
        row.put("text", note.getText());
        row.put("slug", note.getSlug());
        return row;
    }

    private Optional<User> resolveUser(Object rawUserId) {
        UUID userId = uuid(rawUserId == null ? null : String.valueOf(rawUserId)).orElse(null);
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null);
    }

    private String requireText(Map<String, Object> body) {
        String text = str(body, "text");
        if ((text == null || text.isBlank()) && body != null) {
            text = str(body, "content");
        }
        if (text == null || text.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The text field is required.");
        }
        return text;
    }

    private String defaultSlug(Map<String, Object> body, UUID id) {
        String slug = str(body, "slug");
        return (slug == null || slug.isBlank()) ? "note-" + id : slug;
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        return String.valueOf(body.get(key));
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
