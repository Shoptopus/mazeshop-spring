package com.shoptopus.core.io;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExportApiTest extends IntegrationTestBase {

    @Autowired
    private ImportExportService importExportService;

    @Test
    void validate_import_and_export() {
        var csv = "slug,name_en,description_en,enabled\nfurniture,Furniture,Home furniture,true\n";

        var file = new MockMultipartFile("file", "categories.csv", "text/csv", csv.getBytes());

        var validateRes = importExportService.validateCategoriesCsv(file);
        assertThat(validateRes.get("status")).isEqualTo("success");

        var importRes = importExportService.importCategoriesCsv(file);
        assertThat(importRes.get("status")).isEqualTo("success");

        var exportRes = importExportService.exportCategoriesCsv();
        assertThat(exportRes.get("content").toString()).contains("slug,name_en,description_en,enabled");
        assertThat(exportRes.get("content").toString()).contains("furniture");
    }
}
