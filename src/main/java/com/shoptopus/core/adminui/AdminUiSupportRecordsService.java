package com.shoptopus.core.adminui;

import com.shoptopus.core.admin.AdminNoteService;
import com.shoptopus.core.admin.AdminFileService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.FileContent;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.files.repository.FileContentRepository;
import com.shoptopus.core.logging.AuditTrail;
import com.shoptopus.core.logging.EventLog;
import com.shoptopus.core.logging.repository.AuditTrailRepository;
import com.shoptopus.core.logging.repository.EventLogRepository;
import com.shoptopus.core.note.Note;
import com.shoptopus.core.note.repository.NoteRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiSupportRecordsService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FileContentRepository fileContentRepository;
    private final ManagedFileService managedFileService;
    private final AdminFileService adminFileService;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final EventLogRepository eventLogRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final AdminNoteService adminNoteService;
    private final AdminStoreContext adminStoreContext;

    public AdminUiSupportRecordsService(
            FileContentRepository fileContentRepository,
            ManagedFileService managedFileService,
            AdminFileService adminFileService,
            NoteRepository noteRepository,
            UserRepository userRepository,
            EventLogRepository eventLogRepository,
            AuditTrailRepository auditTrailRepository,
            AdminNoteService adminNoteService,
            AdminStoreContext adminStoreContext
    ) {
        this.fileContentRepository = fileContentRepository;
        this.managedFileService = managedFileService;
        this.adminFileService = adminFileService;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.eventLogRepository = eventLogRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.adminNoteService = adminNoteService;
        this.adminStoreContext = adminStoreContext;
    }

    public Map<String, Object> files(String search, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<FileContent> filtered = fileContentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(file -> matchesSearch(file.getName(), search) || matchesSearch(file.getSlug(), search) || matchesSearch(file.getMimeType(), search))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::fileRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of("search", search == null ? "" : search)
        );
    }

    public Map<String, Object> file(String id) {
        FileContent file = findFile(id);
        return fileRow(file);
    }

    public Map<String, Object> createFile(Map<String, Object> body) {
        Object id = adminFileService.create(body).get("id");
        return file(String.valueOf(id));
    }

    public Map<String, Object> updateFile(String id, Map<String, Object> body) {
        adminFileService.update(id, body);
        return file(id);
    }

    public Map<String, Object> deleteFile(String id) {
        return adminFileService.delete(id);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> optimizeFile(String id) {
        FileContent file = findFile(id);
        managedFileService.optimizeImage(file);
        return fileRow(file);
    }

    public Map<String, Object> notes(String search, String noteableType, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<Note> filtered = noteRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(note -> matchesSearch(note.getText(), search))
                .filter(note -> noteableType == null || noteableType.isBlank() || "all".equalsIgnoreCase(noteableType)
                        || noteableType.equalsIgnoreCase(String.valueOf(note.getNoteableType())))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::noteRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "noteable_type", noteableType == null ? "all" : noteableType
                )
        );
    }

    public Map<String, Object> note(String id) {
        Note note = findNote(id);
        return noteRow(note);
    }

    public Map<String, Object> noteFormOptions() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .sorted(Comparator.comparing(user -> user.getEmail() == null ? "" : user.getEmail(), String.CASE_INSENSITIVE_ORDER))
                .limit(100)
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("name", user.getName());
                    row.put("email", user.getEmail());
                    return row;
                })
                .toList();

        return Map.of(
                "noteable_types", List.of("user", "customer", "order", "product", "payment", "cart"),
                "users", users
        );
    }

    public Map<String, Object> createNote(Map<String, Object> body) {
        Object id = adminNoteService.create(body).get("id");
        return note(String.valueOf(id));
    }

    public Map<String, Object> updateNote(String id, Map<String, Object> body) {
        adminNoteService.update(id, body);
        return note(id);
    }

    public Map<String, Object> deleteNote(String id) {
        adminNoteService.delete(id);
        return Map.of("id", id);
    }

    @Transactional(readOnly = true, transactionManager = "logsTransactionManager")
    public Map<String, Object> eventLogs(String search, String type, String dateFrom, String dateTo, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<EventLog> scopedLogs = scopedEventLogs();
        List<EventLog> filtered = scopedLogs.stream()
                .filter(log -> matchesSearch(log.getMessage(), search)
                        || matchesSearch(log.getEventableType(), search)
                        || matchesSearch(log.getData(), search))
                .filter(log -> type == null || type.isBlank() || "all".equalsIgnoreCase(type)
                        || type.equalsIgnoreCase(eventLogType(log)))
                .filter(log -> withinDateRange(log.getCreatedAt(), dateFrom, dateTo))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        List<String> typeOptions = scopedLogs.stream()
                .map(this::eventLogType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::eventLogRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "type", type == null ? "all" : type,
                        "types", typeOptions,
                        "date_from", dateFrom == null ? "" : dateFrom,
                        "date_to", dateTo == null ? "" : dateTo
                )
        );
    }

    @Transactional(readOnly = true, transactionManager = "logsTransactionManager")
    public Map<String, Object> eventLog(String id) {
        EventLog eventLog = findEventLog(id);
        return eventLogRow(eventLog);
    }

    @Transactional(readOnly = true, transactionManager = "logsTransactionManager")
    public Map<String, Object> audits(String search, String type, String userId, String dateFrom, String dateTo, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<AuditTrail> scopedAudits = scopedAudits();
        List<AuditTrail> filtered = scopedAudits.stream()
                .filter(audit -> matchesSearch(audit.getEntityType(), search)
                        || matchesSearch(audit.getEntityId(), search)
                        || matchesSearch(audit.getOperation(), search))
                .filter(audit -> type == null || type.isBlank() || "all".equalsIgnoreCase(type)
                        || type.equalsIgnoreCase(String.valueOf(audit.getEntityType())))
                .filter(audit -> userId == null || userId.isBlank() || "all".equalsIgnoreCase(userId)
                        || userId.equalsIgnoreCase(String.valueOf(audit.getUserId())))
                .filter(audit -> withinDateRange(audit.getCreatedAt(), dateFrom, dateTo))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        List<String> entityTypes = scopedAudits.stream()
                .map(AuditTrail::getEntityType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<Map<String, Object>> users = scopedAudits.stream()
                .map(AuditTrail::getUserId)
                .filter(value -> value != null)
                .distinct()
                .map(this::userOption)
                .filter(value -> value != null)
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("email")), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::auditRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "type", type == null ? "all" : type,
                        "types", entityTypes,
                        "user_id", userId == null ? "all" : userId,
                        "users", users,
                        "date_from", dateFrom == null ? "" : dateFrom,
                        "date_to", dateTo == null ? "" : dateTo
                )
        );
    }

    @Transactional(readOnly = true, transactionManager = "logsTransactionManager")
    public Map<String, Object> audit(String id) {
        AuditTrail audit = findAudit(id);
        return auditRow(audit);
    }

    private Map<String, Object> fileRow(FileContent file) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", file.getId());
        row.put("name", file.getName());
        row.put("mime_type", file.getMimeType());
        row.put("size", file.getSize());
        row.put("path", file.getPath());
        row.put("slug", file.getSlug());
        row.put("type", file.getType());
        row.put("fileable_type", file.getFileableType());
        row.put("fileable_id", file.getFileableId());
        row.put("attached_to", attachedTo(file));
        row.put("download_url", managedFileService.publicUrl(file.getPath()));
        row.put("created_at", format(file.getCreatedAt()));
        row.put("updated_at", format(file.getUpdatedAt()));
        managedFileService.imageDimensions(file).ifPresent(dimensions -> {
            row.put("width", dimensions.width());
            row.put("height", dimensions.height());
        });
        return row;
    }

    private Map<String, Object> attachedTo(FileContent file) {
        if (file.getFileableType() == null || file.getFileableId() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", file.getFileableType());
        row.put("id", file.getFileableId());
        return row;
    }

    private Map<String, Object> noteRow(Note note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", note.getId());
        row.put("text", note.getText());
        row.put("slug", note.getSlug());
        row.put("noteable_type", note.getNoteableType());
        row.put("noteable_id", note.getNoteableId());
        row.put("created_at", format(note.getCreatedAt()));
        row.put("updated_at", format(note.getUpdatedAt()));
        if (note.getUser() == null) {
            row.put("user", null);
        } else {
            row.put("user", Map.of(
                    "id", note.getUser().getId(),
                    "name", note.getUser().getName(),
                    "email", note.getUser().getEmail()
            ));
        }
        return row;
    }

    private Map<String, Object> eventLogRow(EventLog eventLog) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", eventLog.getId());
        row.put("message", eventLog.getMessage());
        row.put("type", eventLogType(eventLog));
        row.put("raw_type", eventLog.getType());
        row.put("eventable_type", eventLog.getEventableType());
        row.put("eventable_id", eventLog.getEventableId());
        row.put("data", eventLog.getData());
        row.put("created_at", format(eventLog.getCreatedAt()));
        row.put("updated_at", format(eventLog.getUpdatedAt()));
        return row;
    }

    private Map<String, Object> auditRow(AuditTrail audit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", audit.getId());
        row.put("operation", audit.getOperation());
        row.put("entity_type", audit.getEntityType());
        row.put("entity_id", audit.getEntityId());
        row.put("before_state", audit.getBeforeState());
        row.put("after_state", audit.getAfterState());
        row.put("created_at", format(audit.getCreatedAt()));
        row.put("updated_at", format(audit.getUpdatedAt()));
        row.put("user_id", audit.getUserId());
        User user = audit.getUserId() == null ? null : userRepository.findById(audit.getUserId()).orElse(null);
        if (user == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", user.getId());
            userRow.put("name", user.getName());
            userRow.put("email", user.getEmail());
            row.put("user", userRow);
        }
        return row;
    }

    private FileContent findFile(String id) {
        try {
            return fileContentRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found", "File not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found", "File not found");
        }
    }

    private Note findNote(String id) {
        try {
            return noteRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Note not found", "Note not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Note not found", "Note not found");
        }
    }

    private EventLog findEventLog(String id) {
        try {
            return eventLogRepository.findById(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Event log not found", "Event log not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Event log not found", "Event log not found");
        }
    }

    private AuditTrail findAudit(String id) {
        try {
            return auditTrailRepository.findById(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audit not found", "Audit not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Audit not found", "Audit not found");
        }
    }

    private boolean matchesSearch(String value, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> pageMeta(int page, int perPage, int total) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("per_page", perPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / perPage));
        return meta;
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }

    private String eventLogType(EventLog eventLog) {
        if (eventLog.getType() == null) {
            return "info";
        }
        return switch (eventLog.getType()) {
            case 1 -> "failure";
            default -> "success";
        };
    }

    private boolean withinDateRange(OffsetDateTime timestamp, String dateFrom, String dateTo) {
        if (timestamp == null) {
            return false;
        }
        OffsetDateTime from = parseDateStart(dateFrom);
        OffsetDateTime to = parseDateEnd(dateTo);
        if (from != null && timestamp.isBefore(from)) {
            return false;
        }
        return to == null || !timestamp.isAfter(to);
    }

    private OffsetDateTime parseDateStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime parseDateEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).plusDays(1).atStartOfDay().minusSeconds(1).atOffset(ZoneOffset.UTC);
    }

    private Map<String, Object> userOption(UUID userId) {
        return userRepository.findById(userId).map(user -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("name", user.getName());
            row.put("email", user.getEmail());
            return row;
        }).orElse(null);
    }

    private boolean matchesActiveStore(EventLog eventLog) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && activeStore.getId().equals(eventLog.getStoreId());
    }

    private boolean matchesActiveStore(AuditTrail auditTrail) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && activeStore.getId().equals(auditTrail.getStoreId());
    }

    private List<EventLog> scopedEventLogs() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return eventLogRepository.findAll().stream()
                    .sorted(Comparator.comparing(EventLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return eventLogRepository.findByStoreIdOrderByCreatedAtDesc(activeStore.getId());
    }

    private List<AuditTrail> scopedAudits() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return auditTrailRepository.findAllByOrderByCreatedAtDesc();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return auditTrailRepository.findByStoreIdOrderByCreatedAtDesc(activeStore.getId());
    }
}
