package com.shoptopus.core.files;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
public class ManagedFileCleanupAspect {

    private final ManagedFileService managedFileService;

    public ManagedFileCleanupAspect(ManagedFileService managedFileService) {
        this.managedFileService = managedFileService;
    }

    @Around("@annotation(clearManagedFiles)")
    public Object clearFilesAfterDelete(ProceedingJoinPoint joinPoint, ClearManagedFiles clearManagedFiles) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof Map<?, ?> map) {
            extractIds(map).forEach(id -> managedFileService.clearFor(clearManagedFiles.fileableType(), id));
        }
        return result;
    }

    private List<UUID> extractIds(Map<?, ?> map) {
        if (map.get("ids") instanceof List<?> ids) {
            return ids.stream()
                    .map(this::parseUuid)
                    .flatMap(Optional::stream)
                    .toList();
        }
        return parseUuid(map.get("id")).stream().toList();
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
}
