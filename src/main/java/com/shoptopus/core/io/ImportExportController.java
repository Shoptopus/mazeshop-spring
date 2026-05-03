package com.shoptopus.core.io;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/io")
public class ImportExportController {

    private final ImportExportService importExportService;

    public ImportExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> export() {
        var export = importExportService.exportCategoriesCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.get("fileName") + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body((String) export.get("content"));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<?> validate(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Validation", importExportService.validateCategoriesCsv(file));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<?> importCsv(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Import", importExportService.importCategoriesCsv(file));
    }
}
