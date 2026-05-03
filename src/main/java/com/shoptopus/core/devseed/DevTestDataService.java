package com.shoptopus.core.devseed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.ManagedFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(value = "app.dev.test-data.enabled", havingValue = "true")
public class DevTestDataService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final DateTimeFormatter MYSQL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DevTestDataProperties properties;
    private final MySqlDumpParser parser;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ManagedFileService managedFileService;
    private final Path mediaRoot;
    private final String mediaPublicBaseUrl;

    public DevTestDataService(
            DevTestDataProperties properties,
            MySqlDumpParser parser,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ManagedFileService managedFileService,
            @Value("${app.media.storage-path}") String mediaStoragePath,
            @Value("${app.media.public-base-url:/media}") String mediaPublicBaseUrl
    ) {
        this.properties = properties;
        this.parser = parser;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.managedFileService = managedFileService;
        this.mediaRoot = Path.of(mediaStoragePath).toAbsolutePath().normalize();
        this.mediaPublicBaseUrl = mediaPublicBaseUrl;
    }

    public Map<String, Object> seed() {
        Path dumpPath = requireExistingFile(properties.getDumpPath(), "MySQL dump");
        Path imagesRoot = requireExistingFile(properties.getImagesPath(), "Test-data images");

        cleanupInternal();

        UUID runId = UUID.randomUUID();
        jdbc.update(
                "insert into dev_seed_runs(id, seed_key, source_dump, status, started_at) values (?, ?, ?, ?, now())",
                runId,
                properties.getSeedKey(),
                dumpPath.toString(),
                "running"
        );

        Map<String, MySqlDumpParser.ParsedTable> dump = parser.parse(dumpPath);
        SeedContext context = new SeedContext(runId, imagesRoot);
        try {
            importUsers(dump.get("users"), context);
            importRoles(dump.get("roles"), context);
            importRoleAssignments(dump.get("model_has_roles"), context);
            importAddresses(dump.get("addresses"), context);
            importProductCategories(dump.get("product_categories"), context);
            importProductTags(dump.get("product_tags"), context);
            importProductAttributes(dump.get("product_attributes"), context);
            importProductAttributeOptions(dump.get("product_attribute_options"), context);
            importProducts(dump.get("products"), context);
            importProductCategoryLinks(dump.get("product_product_category"), context);
            importProductTagLinks(dump.get("product_product_tag"), context);
            importProductVariants(dump.get("product_variants"), context);
            importBanners(dump.get("banners"), context);
            importDeliveryTypes(dump.get("delivery_types"), context);
            importDeliveryRules(dump.get("delivery_rules"), context);
            importVoucherCodes(dump.get("voucher_codes"), context);
            importDiscountRules(dump.get("discount_rules"), context);
            importDiscountRuleLinks(dump.get("discount_rule_product"), dump.get("discount_rule_product_category"), context);
            importCarts(dump.get("carts"), context);
            importCartItems(dump.get("cart_product"), context);
            importPaymentSources(dump.get("payment_sources"), context);
            importOrders(dump.get("orders"), context);
            importOrderItems(dump.get("order_product"), context);
            importPayments(dump.get("payments"), context);
            importRatings(dump.get("ratings"), context);
            importNotes(dump.get("notes"), context);

            jdbc.update("update dev_seed_runs set status = ?, completed_at = now() where id = ?", "completed", runId);
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.putAll(context.insertCounts);
            counts.put("copied_files", context.relativeFiles.size());
            return Map.of(
                    "seed_key", properties.getSeedKey(),
                    "run_id", runId,
                    "status", "completed",
                    "counts", counts
            );
        } catch (RuntimeException ex) {
            context.relativeFiles.stream()
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteCopiedFile);
            cleanupInternal();
            throw ex;
        }
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> cleanup() {
        int deletedRows = cleanupInternal();
        return Map.of(
                "seed_key", properties.getSeedKey(),
                "deleted_rows", deletedRows,
                "status", "clean"
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Integer seededRecords = jdbc.queryForObject(
                "select count(*) from dev_seed_records r join dev_seed_runs s on s.id = r.run_id where s.seed_key = ?",
                Integer.class,
                properties.getSeedKey()
        );
        Integer seededFiles = jdbc.queryForObject(
                "select count(*) from dev_seed_files f join dev_seed_runs s on s.id = f.run_id where s.seed_key = ?",
                Integer.class,
                properties.getSeedKey()
        );
        List<Map<String, Object>> runs = jdbc.queryForList(
                "select id, status, started_at, completed_at from dev_seed_runs where seed_key = ? order by started_at desc",
                properties.getSeedKey()
        );
        return Map.of(
                "enabled", properties.isEnabled(),
                "seed_key", properties.getSeedKey(),
                "dump_path", properties.getDumpPath(),
                "images_path", properties.getImagesPath(),
                "seeded_records", seededRecords == null ? 0 : seededRecords,
                "seeded_files", seededFiles == null ? 0 : seededFiles,
                "runs", runs
        );
    }

    private int cleanupInternal() {
        List<UUID> runIds = jdbc.queryForList(
                "select id from dev_seed_runs where seed_key = ? order by started_at desc",
                UUID.class,
                properties.getSeedKey()
        );
        int deleted = 0;
        for (UUID runId : runIds) {
            List<UUID> fileIds = jdbc.queryForList(
                    "select record_id::uuid from dev_seed_records where run_id = ? and table_name = 'file_contents'",
                    UUID.class,
                    runId
            );
            for (UUID fileId : fileIds) {
                managedFileService.deleteById(fileId);
                deleted++;
            }
            deleted += deleteTracked(runId, "notes", "uuid");
            deleted += deleteTracked(runId, "ratings", "uuid");
            deleted += deleteTracked(runId, "payments", "uuid");
            deleted += deleteTracked(runId, "orders", "uuid");
            deleted += deleteTracked(runId, "payment_sources", "uuid");
            deleted += deleteTracked(runId, "carts", "uuid");
            deleted += deleteTracked(runId, "discount_rules", "uuid");
            deleted += deleteTracked(runId, "voucher_codes", "uuid");
            deleted += deleteTracked(runId, "delivery_rules", "uuid");
            deleted += deleteTracked(runId, "delivery_types", "uuid");
            deleted += deleteTracked(runId, "banners", "uuid");
            deleted += deleteTracked(runId, "product_variants", "uuid");
            deleted += deleteTracked(runId, "products", "uuid");
            deleted += deleteTracked(runId, "product_attribute_options", "uuid");
            deleted += deleteTracked(runId, "product_attributes", "uuid");
            deleted += deleteTracked(runId, "product_tags", "uuid");
            deleted += deleteTracked(runId, "product_categories", "uuid");
            deleted += deleteTracked(runId, "addresses", "uuid");
            deleted += deleteTracked(runId, "users", "uuid");
            deleted += deleteTracked(runId, "roles", "bigint");

            List<String> relativeFiles = jdbc.queryForList(
                    "select relative_path from dev_seed_files where run_id = ? order by relative_path desc",
                    String.class,
                    runId
            );
            for (String relativePath : relativeFiles) {
                deleteCopiedFile(relativePath);
            }
            jdbc.update("delete from dev_seed_runs where id = ?", runId);
        }
        return deleted;
    }

    private int deleteTracked(UUID runId, String tableName, String idType) {
        List<String> ids = jdbc.queryForList(
                "select record_id from dev_seed_records where run_id = ? and table_name = ? order by record_id desc",
                String.class,
                runId,
                tableName
        );
        int deleted = 0;
        for (String id : ids) {
            String sql = switch (idType) {
                case "uuid" -> "delete from " + tableName + " where id = cast(? as uuid)";
                case "bigint" -> "delete from " + tableName + " where id = cast(? as bigint)";
                default -> throw new IllegalArgumentException("Unsupported id type: " + idType);
            };
            deleted += jdbc.update(sql, id);
        }
        return deleted;
    }

    private void importUsers(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            String email = row.get("email");
            UUID sourceId = uuid(row.get("id"));
            UUID existingId = queryUuid("select id from users where lower(email) = lower(?) limit 1", email);
            if (existingId != null) {
                context.userIds.put(sourceId, existingId);
                continue;
            }
            Map<String, Object> avatar = copyGenericImagePayload(context, parseJsonMap(row.get("avatar")), "avatars", "user-avatars");
            jdbc.update("""
                    insert into users(
                        id, first_name, last_name, email, password, name, initials, prefix, phone, avatar,
                        email_verified_at, client_ref, temporary, is_favorite, slug, last_seen, deleted_at, created_at, updated_at
                    ) values (
                        cast(? as uuid), ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb),
                        cast(? as timestamptz), ?, cast(? as boolean), cast(? as boolean), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz)
                    )
                    """,
                    sourceId,
                    row.get("first_name"),
                    row.get("last_name"),
                    email,
                    row.get("password"),
                    row.get("name"),
                    row.get("initials"),
                    blankToNull(row.get("prefix")),
                    blankToNull(row.get("phone")),
                    json(avatar),
                    ts(row.get("email_verified_at")),
                    row.get("client_ref"),
                    bool(row.get("temporary")),
                    bool(row.get("is_favorite")),
                    row.get("slug"),
                    ts(row.get("last_seen")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.userIds.put(sourceId, sourceId);
            trackRecord(context.runId, "users", sourceId.toString(), context);
        }
    }

    private void importRoles(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            Long sourceRoleId = Long.valueOf(row.get("id"));
            String roleName = row.get("name");
            List<Long> existing = jdbc.query(
                    "select id from roles where name = ? limit 1",
                    (rs, rowNum) -> rs.getLong(1),
                    roleName
            );
            if (!existing.isEmpty()) {
                context.roleIds.put(sourceRoleId, existing.get(0));
                continue;
            }
            jdbc.update(
                    "insert into roles(name, created_at, updated_at) values (?, now(), now())",
                    roleName
            );
            Long createdRoleId = jdbc.query(
                    "select id from roles where name = ? limit 1",
                    (rs, rowNum) -> rs.getLong(1),
                    roleName
            ).get(0);
            context.roleIds.put(sourceRoleId, createdRoleId);
            trackRecord(context.runId, "roles", createdRoleId.toString(), context);
        }
    }

    private void importRoleAssignments(MySqlDumpParser.ParsedTable assignmentsTable, SeedContext context) {
        if (assignmentsTable == null) {
            return;
        }
        for (Map<String, String> row : assignmentsTable.mappedRows()) {
            if (!"App\\Models\\User".equals(row.get("model_type"))) {
                continue;
            }
            UUID userId = context.userIds.get(uuid(row.get("model_id")));
            if (userId == null) {
                continue;
            }
            Long targetRoleId = context.roleIds.get(Long.valueOf(row.get("role_id")));
            if (targetRoleId == null) {
                continue;
            }
            jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?) on conflict do nothing", targetRoleId, userId);
        }
    }

    private void importAddresses(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID sourceId = uuid(row.get("id"));
            UUID userId = context.userIds.get(uuid(row.get("user_id")));
            if (userId == null) {
                continue;
            }
            jdbc.update("""
                    insert into addresses(id, address_line_1, town, post_code, slug, user_id, country, name, address_line_2, lat, lon, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), ?, ?, ?, ?, cast(? as uuid), ?, ?, ?, ?, ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    sourceId,
                    row.get("address_line_1"),
                    row.get("town"),
                    row.get("post_code"),
                    row.get("slug"),
                    userId,
                    defaultIfBlank(row.get("country"), "UK"),
                    blankToNull(row.get("name")),
                    blankToNull(row.get("address_line_2")),
                    blankToNull(row.get("lat")),
                    blankToNull(row.get("lon")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.addressIds.put(sourceId, sourceId);
            trackRecord(context.runId, "addresses", sourceId.toString(), context);
        }
    }

    private void importProductCategories(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        Set<UUID> inserted = new LinkedHashSet<>();
        List<Map<String, String>> rows = table.mappedRows();
        while (inserted.size() < rows.size()) {
            int before = inserted.size();
            for (Map<String, String> row : rows) {
                UUID id = uuid(row.get("id"));
                if (inserted.contains(id)) {
                    continue;
                }
                String parentValue = row.get("parent_id");
                if (parentValue != null && !inserted.contains(uuid(parentValue))) {
                    continue;
                }
                jdbc.update("""
                        insert into product_categories(id, parent_id, name, slug, description, menu_image, header_image, enabled, deleted_at, created_at, updated_at)
                        values (cast(? as uuid), cast(? as uuid), cast(? as jsonb), ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                        """,
                        id,
                        nullableUuid(parentValue),
                        json(parseJsonMap(row.get("name"))),
                        row.get("slug"),
                        json(parseJsonMap(row.get("description"))),
                        json(parseJsonMap(row.get("menu_image"))),
                        json(parseJsonMap(row.get("header_image"))),
                        bool(row.get("enabled")),
                        ts(row.get("deleted_at")),
                        ts(row.get("created_at")),
                        ts(row.get("updated_at"))
                );
                inserted.add(id);
                context.categoryIds.put(id, id);
                trackRecord(context.runId, "product_categories", id.toString(), context);
            }
            if (before == inserted.size()) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to seed test data", "Product category parent chain could not be resolved.");
            }
        }
    }

    private void importProductTags(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            Map<String, Object> badge = copyGenericImagePayload(context, parseJsonMap(row.get("badge")), "tags", "product-tags");
            jdbc.update("""
                    insert into product_tags(id, name, slug, description, badge, display_badge, enabled, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as jsonb), ?, cast(? as jsonb), cast(? as jsonb), cast(? as boolean), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    json(parseJsonMap(row.get("description"))),
                    json(badge),
                    bool(row.get("display_badge")),
                    bool(row.get("enabled")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.tagIds.put(id, id);
            trackRecord(context.runId, "product_tags", id.toString(), context);
        }
    }

    private void importProductAttributes(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            Map<String, Object> image = copyGenericImagePayload(context, parseJsonMap(row.get("image")), "attributes", "product-attributes");
            jdbc.update("""
                    insert into product_attributes(id, name, slug, type, image, enabled, show_in_filters, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as jsonb), ?, cast(? as integer), cast(? as jsonb), cast(? as boolean), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    integer(row.get("type")),
                    json(image),
                    bool(row.get("enabled")),
                    bool(defaultIfBlank(row.get("show_in_filters"), "true")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.attributeIds.put(id, id);
            trackRecord(context.runId, "product_attributes", id.toString(), context);
        }
    }

    private void importProductAttributeOptions(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID attributeId = context.attributeIds.get(uuid(row.get("product_attribute_id")));
            if (attributeId == null) {
                continue;
            }
            Map<String, Object> image = copyGenericImagePayload(context, parseJsonMap(row.get("image")), "attribute-options", "product-attribute-options");
            jdbc.update("""
                    insert into product_attribute_options(id, name, slug, value, product_attribute_id, image, enabled, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as jsonb), ?, ?, cast(? as uuid), cast(? as jsonb), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    row.get("value"),
                    attributeId,
                    json(image),
                    bool(row.get("enabled")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.attributeOptionIds.put(id, id);
            trackRecord(context.runId, "product_attribute_options", id.toString(), context);
        }
    }

    private void importProducts(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            Map<String, Object> coverPhoto = createProductAttachment(context, parseJsonMap(row.get("cover_photo")), id);
            jdbc.update("""
                    insert into products(id, name, slug, short_description, description, price, headline, subtitle, status, purchase_count,
                        stock, backup_stock, sku, virtual, weight, cover_photo, available_attribute_options, rating, deleted_at, created_at, updated_at)
                    values (
                        cast(? as uuid), cast(? as jsonb), ?, cast(? as jsonb), cast(? as jsonb), cast(? as numeric), cast(? as jsonb), cast(? as jsonb), cast(? as integer), cast(? as bigint),
                        cast(? as bigint), cast(? as bigint), ?, cast(? as boolean), cast(? as integer), cast(? as jsonb), cast(? as jsonb), cast(? as numeric), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz)
                    )
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    json(parseJsonMap(row.get("short_description"))),
                    json(parseJsonMap(row.get("description"))),
                    numeric(row.get("price")),
                    json(parseJsonMap(row.get("headline"))),
                    json(parseJsonMap(row.get("subtitle"))),
                    integer(row.get("status")),
                    longValue(row.get("purchase_count")),
                    longValue(row.get("stock")),
                    longValue(row.get("backup_stock")),
                    blankToNull(row.get("sku")),
                    bool(row.get("virtual")),
                    integer(row.get("weight")),
                    json(coverPhoto),
                    "{}",
                    numeric(row.get("rating")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.productIds.put(id, id);
            trackRecord(context.runId, "products", id.toString(), context);
        }
    }

    private void importProductCategoryLinks(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID productId = context.productIds.get(uuid(row.get("product_id")));
            UUID categoryId = context.categoryIds.get(uuid(row.get("product_category_id")));
            if (productId == null || categoryId == null) {
                continue;
            }
            jdbc.update("insert into product_product_category(product_id, product_category_id) values (?, ?) on conflict do nothing", productId, categoryId);
        }
    }

    private void importProductTagLinks(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID productId = context.productIds.get(uuid(row.get("product_id")));
            UUID tagId = context.tagIds.get(uuid(row.get("product_tag_id")));
            if (productId == null || tagId == null) {
                continue;
            }
            jdbc.update("insert into product_product_tag(product_id, product_tag_id) values (?, ?) on conflict do nothing", productId, tagId);
        }
    }

    private void importProductVariants(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID productId = context.productIds.get(uuid(row.get("product_id")));
            if (productId == null) {
                continue;
            }
            jdbc.update("""
                    insert into product_variants(id, slug, price, product_id, data, stock, sku, description, attribute_options, enabled, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), ?, cast(? as numeric), cast(? as uuid), ?, cast(? as bigint), ?, ?, cast(? as jsonb), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    row.get("slug"),
                    numeric(row.get("price")),
                    productId,
                    blankToNull(row.get("data")),
                    longValue(row.get("stock")),
                    blankToNull(row.get("sku")),
                    blankToNull(row.get("description")),
                    row.get("attribute_options") == null ? "{}" : row.get("attribute_options"),
                    bool(defaultIfBlank(row.get("enabled"), "1")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.variantIds.put(id, id);
            trackRecord(context.runId, "product_variants", id.toString(), context);
        }
    }

    private void importBanners(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            Map<String, Object> image = copyGenericImagePayload(context, parseJsonMap(row.get("image")), "banners", "banners");
            jdbc.update("""
                    insert into banners(id, name, slug, image, click_count, enabled, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as jsonb), ?, cast(? as jsonb), cast(? as bigint), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    json(image),
                    longValue(defaultIfBlank(row.get("click_count"), "0")),
                    bool(row.get("enabled")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.bannerIds.put(id, id);
            trackRecord(context.runId, "banners", id.toString(), context);
        }
    }

    private void importDeliveryTypes(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            jdbc.update("""
                    insert into delivery_types(id, name, slug, description, price, enabled, enabled_by_default_on_creation, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as jsonb), ?, cast(? as jsonb), cast(? as numeric), cast(? as boolean), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    json(parseJsonMap(row.get("description"))),
                    numeric(defaultIfBlank(row.get("price"), "0")),
                    bool(row.get("enabled")),
                    bool(defaultIfBlank(row.get("enabled_by_default_on_creation"), row.get("enabled"))),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.deliveryTypeIds.put(id, id);
            trackRecord(context.runId, "delivery_types", id.toString(), context);
        }
    }

    private void importDeliveryRules(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID deliveryTypeId = context.deliveryTypeIds.get(uuid(row.get("delivery_type_id")));
            if (deliveryTypeId == null) {
                continue;
            }
            jdbc.update("""
                    insert into delivery_rules(id, delivery_type_id, name, slug, country, min_cart_price, max_cart_price, postcodes, min_weight, max_weight, min_distance, max_distance, lat, lon, price, enabled, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), cast(? as jsonb), ?, ?, cast(? as numeric), cast(? as numeric), cast(? as jsonb), cast(? as integer), cast(? as integer), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as boolean), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    deliveryTypeId,
                    json(parseJsonMap(row.get("name"))),
                    row.get("slug"),
                    blankToNull(row.get("country")),
                    numeric(row.get("min_cart_price")),
                    numeric(row.get("max_cart_price")),
                    row.get("postcodes") == null ? "[]" : row.get("postcodes"),
                    integer(row.get("min_weight")),
                    integer(row.get("max_weight")),
                    numeric(row.get("min_distance")),
                    numeric(row.get("max_distance")),
                    numeric(row.get("lat")),
                    numeric(row.get("lon")),
                    numeric(defaultIfBlank(row.get("price"), "0")),
                    bool(row.get("enabled")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.deliveryRuleIds.put(id, id);
            trackRecord(context.runId, "delivery_rules", id.toString(), context);
        }
    }

    private void importVoucherCodes(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            jdbc.update("""
                    insert into voucher_codes(id, amount, code, valid_from, valid_until, discount_type, enabled, slug, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as numeric), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as integer), cast(? as boolean), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    numeric(defaultIfBlank(row.get("amount"), "0")),
                    row.get("code"),
                    ts(row.get("valid_from")),
                    ts(row.get("valid_until")),
                    integer(defaultIfBlank(row.get("discount_type"), "0")),
                    bool(defaultIfBlank(row.get("enabled"), "1")),
                    row.get("slug"),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.voucherIds.put(id, id);
            trackRecord(context.runId, "voucher_codes", id.toString(), context);
        }
    }

    private void importDiscountRules(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            jdbc.update("""
                    insert into discount_rules(id, type, name, amount, valid_from, valid_until, enabled, slug, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as integer), cast(? as jsonb), cast(? as numeric), cast(? as timestamptz), cast(? as timestamptz), cast(? as boolean), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    integer(defaultIfBlank(row.get("type"), "0")),
                    json(parseJsonMap(row.get("name"))),
                    numeric(defaultIfBlank(row.get("amount"), "0")),
                    ts(row.get("valid_from")),
                    ts(row.get("valid_until")),
                    bool(defaultIfBlank(row.get("enabled"), "1")),
                    row.get("slug"),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.discountIds.put(id, id);
            trackRecord(context.runId, "discount_rules", id.toString(), context);
        }
    }

    private void importDiscountRuleLinks(MySqlDumpParser.ParsedTable productLinks, MySqlDumpParser.ParsedTable categoryLinks, SeedContext context) {
        if (productLinks != null) {
            for (Map<String, String> row : productLinks.mappedRows()) {
                UUID discountId = context.discountIds.get(uuid(row.get("discount_rule_id")));
                UUID productId = context.productIds.get(uuid(row.get("product_id")));
                if (discountId != null && productId != null) {
                    jdbc.update("insert into discount_rule_product(discount_rule_id, product_id) values (?, ?) on conflict do nothing", discountId, productId);
                }
            }
        }
        if (categoryLinks != null) {
            for (Map<String, String> row : categoryLinks.mappedRows()) {
                UUID discountId = context.discountIds.get(uuid(row.get("discount_rule_id")));
                UUID categoryId = context.categoryIds.get(uuid(row.get("product_category_id")));
                if (discountId != null && categoryId != null) {
                    jdbc.update("insert into discount_rule_product_category(discount_rule_id, product_category_id) values (?, ?) on conflict do nothing", discountId, categoryId);
                }
            }
        }
    }

    private void importCarts(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            jdbc.update("""
                    insert into carts(id, user_id, ip_address, slug, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), ?, ?, cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    userId,
                    blankToNull(row.get("ip_address")),
                    row.get("slug"),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.cartIds.put(id, id);
            trackRecord(context.runId, "carts", id.toString(), context);
        }
    }

    private void importCartItems(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID cartId = context.cartIds.get(uuid(row.get("cart_id")));
            UUID productId = context.productIds.get(uuid(row.get("product_id")));
            UUID variantId = nullableUuidMapped(context.variantIds, row.get("product_variant_id"));
            if (cartId == null || productId == null) {
                continue;
            }
            jdbc.update("""
                    insert into cart_product(id, cart_id, product_id, product_variant_id, quantity, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as bigint), cast(? as timestamptz), cast(? as timestamptz))
                    on conflict do nothing
                    """,
                    uuid(row.get("id")),
                    cartId,
                    productId,
                    variantId,
                    longValue(defaultIfBlank(row.get("quantity"), "1")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
        }
    }

    private void importPaymentSources(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            jdbc.update("""
                    insert into payment_sources(id, user_id, name, slug, source_id, exp_month, exp_year, last_four, brand, stripe_user_id, payment_method_id, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), ?, ?, ?, ?, ?, ?, ?, ?, cast(? as integer), cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    userId,
                    clip(row.get("name"), 100),
                    clip(row.get("slug"), 255),
                    clip(blankToNull(row.get("source_id")), 255),
                    normalizeCardFragment(blankToNull(row.get("exp_month")), 20),
                    normalizeCardFragment(blankToNull(row.get("exp_year")), 20),
                    normalizeLastFour(blankToNull(row.get("last_four"))),
                    clip(blankToNull(row.get("brand")), 120),
                    clip(blankToNull(row.get("stripe_user_id")), 255),
                    integer(defaultIfBlank(row.get("payment_method_id"), "0")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.paymentSourceIds.put(id, id);
            trackRecord(context.runId, "payment_sources", id.toString(), context);
        }
    }

    private void importOrders(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID addressId = context.addressIds.get(uuid(row.get("address_id")));
            if (addressId == null) {
                continue;
            }
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            UUID deliveryTypeId = nullableUuidMapped(context.deliveryTypeIds, row.get("delivery_type_id"));
            UUID voucherId = nullableUuidMapped(context.voucherIds, row.get("voucher_code_id"));
            jdbc.update("""
                    insert into orders(id, address_id, user_id, delivery_type_id, voucher_code_id, original_price, subtotal, total_price, total_discount, delivery_cost, currency_code, status, slug, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), ?, cast(? as integer), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    addressId,
                    userId,
                    deliveryTypeId,
                    voucherId,
                    numeric(defaultIfBlank(row.get("original_price"), "0")),
                    numeric(defaultIfBlank(row.get("subtotal"), "0")),
                    numeric(defaultIfBlank(row.get("total_price"), "0")),
                    numeric(defaultIfBlank(row.get("total_discount"), "0")),
                    numeric(defaultIfBlank(row.get("delivery_cost"), "0")),
                    defaultIfBlank(row.get("currency_code"), "GBP"),
                    integer(defaultIfBlank(row.get("status"), "0")),
                    row.get("slug"),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.orderIds.put(id, id);
            trackRecord(context.runId, "orders", id.toString(), context);
        }
    }

    private void importOrderItems(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID orderId = context.orderIds.get(uuid(row.get("order_id")));
            UUID productId = nullableUuidMapped(context.productIds, row.get("product_id"));
            UUID variantId = nullableUuidMapped(context.variantIds, row.get("product_variant_id"));
            if (orderId == null) {
                continue;
            }
            jdbc.update("""
                    insert into order_product(id, order_id, product_id, product_variant_id, amount, name, original_unit_price, unit_price, full_price, final_price, unit_discount, total_discount, returned, returned_at, return_reason, slug, urls, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as uuid), cast(? as integer), ?, cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as numeric), cast(? as boolean), cast(? as timestamptz), ?, ?, cast(? as jsonb), cast(? as timestamptz), cast(? as timestamptz))
                    on conflict do nothing
                    """,
                    uuid(row.get("id")),
                    orderId,
                    productId,
                    variantId,
                    integer(defaultIfBlank(row.get("amount"), "1")),
                    row.get("name"),
                    numeric(defaultIfBlank(row.get("original_unit_price"), "0")),
                    numeric(defaultIfBlank(row.get("unit_price"), "0")),
                    numeric(defaultIfBlank(row.get("full_price"), "0")),
                    numeric(defaultIfBlank(row.get("final_price"), "0")),
                    numeric(defaultIfBlank(row.get("unit_discount"), "0")),
                    numeric(defaultIfBlank(row.get("total_discount"), "0")),
                    bool(defaultIfBlank(row.get("returned"), "0")),
                    ts(row.get("returned_at")),
                    blankToNull(row.get("return_reason")),
                    row.get("slug"),
                    row.get("urls") == null ? "{}" : row.get("urls"),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
        }
    }

    private void importPayments(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            UUID paymentSourceId = nullableUuidMapped(context.paymentSourceIds, row.get("payment_source_id"));
            jdbc.update("""
                    insert into payments(id, payable_type, payable_id, amount, slug, user_id, payment_source_id, proof, status, payment_ref, method_ref, type, description, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), ?, ?, cast(? as numeric), ?, cast(? as uuid), cast(? as uuid), cast(? as jsonb), cast(? as integer), ?, ?, cast(? as integer), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    row.get("payable_type"),
                    row.get("payable_id"),
                    numeric(defaultIfBlank(row.get("amount"), "0")),
                    row.get("slug"),
                    userId,
                    paymentSourceId,
                    row.get("proof") == null ? "{}" : row.get("proof"),
                    integer(defaultIfBlank(row.get("status"), "0")),
                    blankToNull(row.get("payment_ref")),
                    blankToNull(row.get("method_ref")),
                    integer(defaultIfBlank(row.get("type"), "0")),
                    blankToNull(row.get("description")),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.paymentIds.put(id, id);
            trackRecord(context.runId, "payments", id.toString(), context);
        }
    }

    private void importRatings(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            jdbc.update("""
                    insert into ratings(id, user_id, ratable_type, ratable_id, rating, language_prefix, description, title, enabled, verified, slug, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), ?, cast(? as uuid), cast(? as integer), ?, ?, ?, cast(? as boolean), cast(? as boolean), ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    userId,
                    row.get("ratable_type"),
                    nullableUuid(row.get("ratable_id")),
                    integer(defaultIfBlank(row.get("rating"), "0")),
                    blankToNull(row.get("language_prefix")),
                    blankToNull(row.get("description")),
                    blankToNull(row.get("title")),
                    bool(defaultIfBlank(row.get("enabled"), "1")),
                    bool(defaultIfBlank(row.get("verified"), "1")),
                    row.get("slug"),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.ratingIds.put(id, id);
            trackRecord(context.runId, "ratings", id.toString(), context);
        }
    }

    private void importNotes(MySqlDumpParser.ParsedTable table, SeedContext context) {
        if (table == null) {
            return;
        }
        for (Map<String, String> row : table.mappedRows()) {
            UUID id = uuid(row.get("id"));
            UUID userId = nullableUuidMapped(context.userIds, row.get("user_id"));
            jdbc.update("""
                    insert into notes(id, user_id, noteable_type, noteable_id, text, slug, deleted_at, created_at, updated_at)
                    values (cast(? as uuid), cast(? as uuid), ?, cast(? as uuid), ?, ?, cast(? as timestamptz), cast(? as timestamptz), cast(? as timestamptz))
                    """,
                    id,
                    userId,
                    blankToNull(row.get("noteable_type")),
                    nullableUuid(row.get("noteable_id")),
                    row.get("text"),
                    row.get("slug"),
                    ts(row.get("deleted_at")),
                    ts(row.get("created_at")),
                    ts(row.get("updated_at"))
            );
            context.noteIds.put(id, id);
            trackRecord(context.runId, "notes", id.toString(), context);
        }
    }

    private Map<String, Object> createProductAttachment(SeedContext context, Map<String, Object> sourceImage, UUID productId) {
        Path source = resolveImageSource(context.imagesRoot, sourceImage, "products");
        if (source == null) {
            return new LinkedHashMap<>();
        }
        String originalName = sourceImage == null ? source.getFileName().toString() : defaultIfBlank((String) sourceImage.get("file_name"), source.getFileName().toString());
        String relativePath = copySeedImage(context, source, "products", originalName);
        var file = managedFileService.createUploadedFile(
                stripExtension(originalName),
                originalName,
                detectMimeType(source),
                size(source),
                relativePath,
                "cover_photo",
                "product",
                productId
        );
        trackRecord(context.runId, "file_contents", file.getId().toString(), context);
        return managedFileService.toPayload(file);
    }

    private Map<String, Object> copyGenericImagePayload(SeedContext context, Map<String, Object> sourceImage, String exactFolder, String fallbackDomain) {
        Path source = resolveImageSource(context.imagesRoot, sourceImage, exactFolder);
        if (source == null) {
            return new LinkedHashMap<>();
        }
        String originalName = sourceImage == null ? source.getFileName().toString() : defaultIfBlank((String) sourceImage.get("file_name"), source.getFileName().toString());
        String relativePath = copySeedImage(context, source, fallbackDomain, originalName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", publicUrl(relativePath));
        payload.put("file_name", originalName);
        return payload;
    }

    private Path resolveImageSource(Path imagesRoot, Map<String, Object> sourceImage, String folderHint) {
        String requestedName = sourceImage == null ? null : (String) sourceImage.get("file_name");
        if (requestedName != null && !requestedName.isBlank()) {
            try (Stream<Path> files = Files.walk(imagesRoot)) {
                Optional<Path> exact = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase(requestedName))
                        .findFirst();
                if (exact.isPresent()) {
                    return exact.get();
                }
            } catch (IOException ignored) {
            }
        }
        List<Path> candidates = new ArrayList<>();
        Path folder = imagesRoot.resolve(folderHint);
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.walk(folder, 1)) {
                candidates.addAll(files.filter(Files::isRegularFile).sorted().toList());
            } catch (IOException ignored) {
            }
        }
        if (candidates.isEmpty()) {
            try (Stream<Path> files = Files.walk(imagesRoot, 1)) {
                candidates.addAll(files.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .sorted()
                        .toList());
            } catch (IOException ignored) {
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(Math.floorMod(Objects.hash(folderHint, requestedName), candidates.size()));
    }

    private String copySeedImage(SeedContext context, Path source, String domain, String preferredName) {
        String safeName = preferredName == null || preferredName.isBlank() ? source.getFileName().toString() : preferredName;
        String fileName = UUID.randomUUID() + extension(safeName);
        Path relative = Path.of("seed", properties.getSeedKey(), domain, fileName);
        Path target = mediaRoot.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to seed test images", "Unable to copy seed image " + source.getFileName());
        }
        String relativePath = relative.toString().replace('\\', '/');
        context.relativeFiles.add(relativePath);
        jdbc.update("insert into dev_seed_files(run_id, relative_path) values (?, ?) on conflict do nothing", context.runId, relativePath);
        return relativePath;
    }

    private void deleteCopiedFile(String relativePath) {
        try {
            Path target = mediaRoot.resolve(relativePath).normalize();
            if (!target.startsWith(mediaRoot)) {
                return;
            }
            Files.deleteIfExists(target);
            Path current = target.getParent();
            while (current != null && !current.equals(mediaRoot) && !current.equals(mediaRoot.getParent())) {
                boolean hasFiles;
                try (var children = Files.list(current)) {
                    hasFiles = children.findAny().isPresent();
                }
                if (!Files.exists(current) || hasFiles) {
                    break;
                }
                Files.deleteIfExists(current);
                current = current.getParent();
            }
        } catch (IOException ignored) {
        }
    }

    private void trackRecord(UUID runId, String tableName, String recordId, SeedContext context) {
        jdbc.update("insert into dev_seed_records(run_id, table_name, record_id) values (?, ?, ?) on conflict do nothing", runId, tableName, recordId);
        context.insertCounts.merge(tableName, 1, Integer::sum);
    }

    private Path requireExistingFile(String pathValue, String label) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Development seed is not configured", label + " path is not configured.");
        }
        Path path = Path.of(pathValue).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Development seed is not configured", label + " path does not exist: " + path);
        }
        return path;
    }

    private String publicUrl(String relativePath) {
        String base = mediaPublicBaseUrl == null || mediaPublicBaseUrl.isBlank() ? "/media" : mediaPublicBaseUrl;
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalizedBase + "/" + relativePath.replaceFirst("^/+", "");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize JSON payload", ex);
        }
    }

    private Map<String, Object> parseJsonMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private UUID nullableUuidMapped(Map<UUID, UUID> mappings, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return mappings.getOrDefault(UUID.fromString(value), UUID.fromString(value));
    }

    private UUID queryUuid(String sql, Object... args) {
        List<UUID> result = jdbc.query(sql, (rs, rowNum) -> rs.getObject(1, UUID.class), args);
        return result.isEmpty() ? null : result.get(0);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Boolean bool(String value) {
        if (value == null) {
            return null;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private Integer integer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private Long longValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private BigDecimal numeric(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private Timestamp ts(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.parse(value, MYSQL_TS);
        return Timestamp.from(dateTime.toInstant(ZoneOffset.UTC));
    }

    private String detectMimeType(Path source) {
        try {
            String detected = Files.probeContentType(source);
            return detected == null ? "application/octet-stream" : detected;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    private long size(Path source) {
        try {
            return Files.size(source);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : ".bin";
    }

    private String clip(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeCardFragment(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            return null;
        }
        return value;
    }

    private String normalizeLastFour(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 10) {
            return value;
        }
        String digitsOnly = value.replaceAll("\\D", "");
        if (digitsOnly.length() >= 4) {
            return digitsOnly.substring(digitsOnly.length() - 4);
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static final class SeedContext {
        private final UUID runId;
        private final Path imagesRoot;
        private final Map<String, Integer> insertCounts = new LinkedHashMap<>();
        private final List<String> relativeFiles = new ArrayList<>();
        private final Map<UUID, UUID> userIds = new HashMap<>();
        private final Map<Long, Long> roleIds = new HashMap<>();
        private final Map<UUID, UUID> addressIds = new HashMap<>();
        private final Map<UUID, UUID> categoryIds = new HashMap<>();
        private final Map<UUID, UUID> tagIds = new HashMap<>();
        private final Map<UUID, UUID> attributeIds = new HashMap<>();
        private final Map<UUID, UUID> attributeOptionIds = new HashMap<>();
        private final Map<UUID, UUID> productIds = new HashMap<>();
        private final Map<UUID, UUID> variantIds = new HashMap<>();
        private final Map<UUID, UUID> bannerIds = new HashMap<>();
        private final Map<UUID, UUID> deliveryTypeIds = new HashMap<>();
        private final Map<UUID, UUID> deliveryRuleIds = new HashMap<>();
        private final Map<UUID, UUID> voucherIds = new HashMap<>();
        private final Map<UUID, UUID> discountIds = new HashMap<>();
        private final Map<UUID, UUID> cartIds = new HashMap<>();
        private final Map<UUID, UUID> paymentSourceIds = new HashMap<>();
        private final Map<UUID, UUID> orderIds = new HashMap<>();
        private final Map<UUID, UUID> paymentIds = new HashMap<>();
        private final Map<UUID, UUID> ratingIds = new HashMap<>();
        private final Map<UUID, UUID> noteIds = new HashMap<>();

        private SeedContext(UUID runId, Path imagesRoot) {
            this.runId = runId;
            this.imagesRoot = imagesRoot;
        }
    }
}
