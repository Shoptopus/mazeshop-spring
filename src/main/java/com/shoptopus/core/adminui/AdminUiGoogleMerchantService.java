package com.shoptopus.core.adminui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttribute;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.googlemerchant.GoogleMerchantApiClient;
import com.shoptopus.core.googlemerchant.GoogleMerchantApiClient.GoogleMerchantSession;
import com.shoptopus.core.store.DefaultStoreResolver;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.storefront.StorefrontPricingService;
import com.shoptopus.core.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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

@Service
public class AdminUiGoogleMerchantService {

    private static final Logger log = LoggerFactory.getLogger(AdminUiGoogleMerchantService.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JdbcTemplate jdbc;
    private final AdminStoreContext adminStoreContext;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final StorefrontPricingService storefrontPricingService;
    private final ManagedFileService managedFileService;
    private final GoogleMerchantApiClient googleMerchantApiClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final boolean multiStoreEnabled;
    private final String storefrontBaseUrl;

    public AdminUiGoogleMerchantService(
            JdbcTemplate jdbc,
            AdminStoreContext adminStoreContext,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            StorefrontPricingService storefrontPricingService,
            ManagedFileService managedFileService,
            GoogleMerchantApiClient googleMerchantApiClient,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            @Value("${app.storefront.public-base-url:http://localhost:8080}") String storefrontBaseUrl
    ) {
        this.jdbc = jdbc;
        this.adminStoreContext = adminStoreContext;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.storefrontPricingService = storefrontPricingService;
        this.managedFileService = managedFileService;
        this.googleMerchantApiClient = googleMerchantApiClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.multiStoreEnabled = multiStoreEnabled;
        this.storefrontBaseUrl = storefrontBaseUrl;
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public Map<String, Object> detail(String email) {
        Store store = targetStore(email);
        GoogleMerchantConnection connection = loadConnection(store.getId()).orElse(defaultConnection(store.getId()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("store", storeRow(store));
        payload.put("connection", connectionRow(connection));
        payload.put("runs", recentRuns(store.getId()));
        return payload;
    }

    @Transactional(transactionManager = "mainTransactionManager")
    public Map<String, Object> update(String email, Map<String, Object> body) {
        Store store = targetStore(email);
        GoogleMerchantConnection current = loadConnection(store.getId()).orElse(defaultConnection(store.getId()));
        GoogleMerchantConnection next = mergedConnection(current, body);
        saveConnection(next);
        return detail(email);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    public Map<String, Object> sync(String email) {
        Store store = targetStore(email);
        GoogleMerchantConnection connection = requireConfiguredConnection(store.getId());
        UUID triggeredBy = userIdFor(email);
        executeSync(store, connection, triggeredBy, null);
        return detail(email);
    }

    @Transactional(transactionManager = "mainTransactionManager")
    public Map<String, Object> retryFailed(String email, UUID runId) {
        Store store = targetStore(email);
        GoogleMerchantConnection connection = requireConfiguredConnection(store.getId());
        UUID triggeredBy = userIdFor(email);
        GoogleMerchantSyncRun run = syncRun(store.getId(), runId);
        executeSync(store, connection, triggeredBy, run);
        return detail(email);
    }

    private void executeSync(Store store, GoogleMerchantConnection connection, UUID triggeredBy, GoogleMerchantSyncRun retryOf) {
        UUID runId = UUID.randomUUID();
        boolean retryMode = retryOf != null;
        log.info("Google Merchant sync starting runId={}, storeId={}, connectionId={}, mode={}, merchantId={}",
                runId, store.getId(), connection.id(), retryMode ? "retry_failed" : "full", connection.merchantId());
        insertRun(runId, connection.id(), store.getId(), triggeredBy, retryOf == null ? null : retryOf.id(), retryMode ? "retry_failed" : "full");
        GoogleMerchantConnection activeConnection = connection;
        int syncedItems = 0;
        int failedItems = 0;
        String runStatus = "completed";
        String summary = retryMode ? "Retried failed Google Merchant items." : "Google Merchant sync completed.";
        String connectionError = null;
        try {
            GoogleMerchantSession session = googleMerchantApiClient.authorize(activeConnection.serviceAccountJson());
            log.info("Google Merchant sync authorized runId={}, storeId={}, connectionId={}, sessionId={}, clientEmail={}",
                    runId, store.getId(), activeConnection.id(), session.sessionId(), session.clientEmail());
            activeConnection = ensureDataSource(store, activeConnection, session);
            List<SyncTarget> targets = retryMode
                    ? retryTargetsFor(store, retryOf, activeConnection)
                    : syncTargetsFor(store, activeConnection);
            log.info("Google Merchant sync prepared targets runId={}, storeId={}, connectionId={}, dataSourceName={}, targetCount={}, mode={}",
                    runId, store.getId(), activeConnection.id(), activeConnection.dataSourceName(), targets.size(), retryMode ? "retry_failed" : "full");

            jdbc.update("""
                    update google_merchant_sync_runs
                    set requested_items = ?,
                        updated_at = now()
                    where id = ?::uuid
                    """, targets.size(), runId.toString());

            Set<String> currentOfferIds = new LinkedHashSet<>();
            for (SyncTarget target : targets) {
                currentOfferIds.add(target.offerId());
                SyncResult result = syncTarget(runId, session, activeConnection, target);
                if (result.success()) {
                    syncedItems++;
                } else {
                    failedItems++;
                }
            }

            if (!retryMode) {
                for (SyncTarget staleTarget : staleDeleteTargets(store, activeConnection, currentOfferIds)) {
                    SyncResult result = syncTarget(runId, session, activeConnection, staleTarget);
                    if (result.success()) {
                        syncedItems++;
                    } else {
                        failedItems++;
                    }
                }
            }

            if (targets.isEmpty() && !retryMode) {
                summary = "No eligible products were available for Google Merchant sync.";
            } else if (targets.isEmpty()) {
                summary = "No failed Google Merchant items were available to retry.";
            }
            if (failedItems > 0 && syncedItems > 0) {
                runStatus = "partial";
                summary = "Google Merchant sync completed with failures.";
            } else if (failedItems > 0) {
                runStatus = "failed";
                summary = "Google Merchant sync failed.";
            }
        } catch (ApiException ex) {
            runStatus = "failed";
            summary = "Google Merchant sync failed before all items could be processed.";
            connectionError = ex.getMessage();
            log.error("Google Merchant sync failed runId={}, storeId={}, connectionId={}, error={}",
                    runId, store.getId(), activeConnection.id(), connectionError, ex);
        }

        jdbc.update("""
                update google_merchant_sync_runs
                set status = ?,
                    synced_items = ?,
                    failed_items = ?,
                    summary = ?,
                    finished_at = now(),
                    updated_at = now()
                where id = ?::uuid
                """,
                runStatus,
                syncedItems,
                failedItems,
                summary,
                runId.toString()
        );
        jdbc.update("""
                update google_merchant_connections
                set last_sync_at = now(),
                    last_sync_status = ?,
                    last_sync_error = ?,
                    updated_at = now()
                where id = ?::uuid
                """,
                runStatus,
                connectionError,
                activeConnection.id().toString()
        );
        log.info("Google Merchant sync finished runId={}, storeId={}, connectionId={}, status={}, syncedItems={}, failedItems={}",
                runId, store.getId(), activeConnection.id(), runStatus, syncedItems, failedItems);
        if (connectionError != null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Google Merchant sync failed", connectionError);
        }
    }

    private SyncResult syncTarget(UUID runId, GoogleMerchantSession session, GoogleMerchantConnection connection, SyncTarget target) {
        if ("missing".equals(target.action())) {
            insertSyncItem(runId, target, "failed", null, string(target.requestPayload().get("error")));
            return new SyncResult(false);
        }
        try {
            Map<String, Object> response;
            if ("delete".equals(target.action())) {
                googleMerchantApiClient.deleteProductInput(session, productInputName(connection, target.offerId()), connection.dataSourceName());
                response = Map.of("deleted", true);
            } else {
                response = googleMerchantApiClient.insertProductInput(session, connection.merchantId(), connection.dataSourceName(), target.requestPayload());
            }
            insertSyncItem(runId, target, "success", response, null);
            return new SyncResult(true);
        } catch (ApiException ex) {
            insertSyncItem(runId, target, "failed", null, ex.getMessage());
            return new SyncResult(false);
        }
    }

    private GoogleMerchantConnection ensureDataSource(Store store, GoogleMerchantConnection connection, GoogleMerchantSession session) {
        if (connection.dataSourceName() != null && !connection.dataSourceName().isBlank()) {
            log.info("Google Merchant using existing data source for storeId={}, connectionId={}, merchantId={}, dataSourceName={}, sessionId={}",
                    store.getId(), connection.id(), connection.merchantId(), connection.dataSourceName(), session.sessionId());
            return connection;
        }
        String displayName = connection.dataSourceDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = store.getName() + " Products API";
        }
        log.info("Google Merchant auto-creating data source for storeId={}, connectionId={}, merchantId={}, displayName={}",
                store.getId(), connection.id(), connection.merchantId(), displayName);
        Map<String, Object> response = googleMerchantApiClient.createPrimaryDataSource(
                session,
                connection.merchantId(),
                displayName,
                connection.feedLabel(),
                connection.contentLanguage(),
                connection.targetCountry()
        );
        String dataSourceName = string(response.get("name"));
        if (dataSourceName == null || dataSourceName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Google Merchant request failed", "Google Merchant did not return a data source name.");
        }
        log.info("Google Merchant data source created for storeId={}, connectionId={}, dataSourceName={}",
                store.getId(), connection.id(), dataSourceName);
        GoogleMerchantConnection updated = new GoogleMerchantConnection(
                connection.id(),
                connection.storeId(),
                connection.enabled(),
                connection.merchantId(),
                dataSourceName,
                stringOrDefault(response.get("displayName"), displayName),
                connection.contentLanguage(),
                connection.feedLabel(),
                connection.targetCountry(),
                connection.serviceAccountJson(),
                connection.lastSyncAt(),
                connection.lastSyncStatus(),
                connection.lastSyncError()
        );
        saveConnection(updated);
        return updated;
    }

    private List<SyncTarget> syncTargetsFor(Store store, GoogleMerchantConnection connection) {
        return eligibleProducts(store).stream()
                .flatMap(product -> targetsForProduct(store, product, connection).stream())
                .toList();
    }

    private List<Product> eligibleProducts(Store store) {
        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getStore() != null && Objects.equals(product.getStore().getId(), store.getId()))
                .filter(product -> product.getStatus() != null && product.getStatus() == 1)
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<SyncTarget> targetsForProduct(Store store, Product product, GoogleMerchantConnection connection) {
        List<ProductVariant> variants = productVariantRepository.findByProductIdAndDeletedAtIsNull(product.getId()).stream()
                .filter(ProductVariant::isEnabled)
                .toList();
        if (variants.isEmpty()) {
            return List.of(upsertTarget(store, product, null, connection));
        }
        return variants.stream()
                .map(variant -> upsertTarget(store, product, variant, connection))
                .toList();
    }

    private List<SyncTarget> retryTargetsFor(Store store, GoogleMerchantSyncRun retryOf, GoogleMerchantConnection connection) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,
                       product_id,
                       variant_id,
                       offer_id,
                       coalesce(title, '') as title,
                       action
                from google_merchant_sync_items
                where run_id = ?::uuid
                  and status = 'failed'
                order by created_at asc
                """, retryOf.id().toString());
        List<SyncTarget> targets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String action = string(row.get("action"));
            String offerId = string(row.get("offer_id"));
            if ("delete".equals(action)) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("name", productInputName(connection, offerId));
                targets.add(new SyncTarget(null, null, offerId, string(row.get("title")), "delete", request));
                continue;
            }
            UUID productId = uuid(row.get("product_id"));
            UUID variantId = uuid(row.get("variant_id"));
            Product product = productId == null ? null : productRepository.findById(productId).orElse(null);
            if (product == null || product.getDeletedAt() != null || product.getStore() == null
                    || !Objects.equals(product.getStore().getId(), store.getId())
                    || product.getStatus() == null || product.getStatus() != 1) {
                targets.add(missingRetryTarget(productId, variantId, offerId, string(row.get("title")),
                        "Referenced product is no longer eligible for Google Merchant sync."));
                continue;
            }
            ProductVariant variant = null;
            if (variantId != null) {
                variant = productVariantRepository.findById(variantId).orElse(null);
                if (variant == null || variant.getDeletedAt() != null || !variant.isEnabled()) {
                    targets.add(missingRetryTarget(productId, variantId, offerId, string(row.get("title")),
                            "Referenced product variant is no longer eligible for Google Merchant sync."));
                    variant = null;
                    continue;
                }
            }
            targets.add(upsertTarget(store, product, variant, connection));
        }
        return targets;
    }

    private List<SyncTarget> staleDeleteTargets(Store store, GoogleMerchantConnection connection, Set<String> currentOfferIds) {
        Set<String> previousOfferIds = new LinkedHashSet<>(jdbc.query("""
                select distinct i.offer_id
                from google_merchant_sync_items i
                join google_merchant_sync_runs r on r.id = i.run_id
                where r.store_id = ?::uuid
                  and i.status = 'success'
                  and i.action = 'upsert'
                order by i.offer_id
                """, (rs, rowNum) -> rs.getString("offer_id"), store.getId().toString()));
        previousOfferIds.removeAll(currentOfferIds);
        return previousOfferIds.stream()
                .map(offerId -> {
                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("name", productInputName(connection, offerId));
                    return new SyncTarget(null, null, offerId, offerId, "delete", request);
                })
                .toList();
    }

    private SyncTarget upsertTarget(Store store, Product product, ProductVariant variant, GoogleMerchantConnection connection) {
        String offerId = variant == null ? "product-" + product.getId() : "variant-" + variant.getId();
        Map<String, Object> payload = productInput(store, product, variant, connection, offerId);
        @SuppressWarnings("unchecked")
        Map<String, Object> productAttributes = (Map<String, Object>) payload.get("productAttributes");
        return new SyncTarget(
                product.getId(),
                variant == null ? null : variant.getId(),
                offerId,
                string(productAttributes == null ? null : productAttributes.get("title")),
                "upsert",
                payload
        );
    }

    private SyncTarget missingRetryTarget(UUID productId, UUID variantId, String offerId, String title, String message) {
        return new SyncTarget(
                productId,
                variantId,
                offerId,
                title == null || title.isBlank() ? offerId : title,
                "missing",
                Map.of("error", message)
        );
    }

    private Map<String, Object> productInput(Store store, Product product, ProductVariant variant, GoogleMerchantConnection connection, String offerId) {
        Locale locale = localeFor(connection.contentLanguage());
        VariantDescriptor variantDescriptor = variant == null ? VariantDescriptor.empty() : variantDescriptor(variant, locale);
        BigDecimal basePrice = variant == null ? safeMoney(product.getPrice()) : safeMoney(variant.getPrice());
        StorefrontPricingService.Pricing pricing = pricingFor(product, basePrice);
        String title = translated(product.getName(), locale);
        if (variant != null && variantDescriptor.label() != null && !variantDescriptor.label().isBlank()) {
            title = title + " - " + variantDescriptor.label();
        }
        String description = nonBlank(
                variant == null ? null : translated(variant.getDescription(), locale),
                translated(product.getDescription(), locale),
                translated(product.getShortDescription(), locale),
                title
        );
        String link = absoluteProductUrl(store, product);
        List<String> imageLinks = productImageUrls(product);
        Map<String, Object> productAttributes = new LinkedHashMap<>();
        productAttributes.put("title", title);
        productAttributes.put("description", description);
        productAttributes.put("link", link);
        productAttributes.put("canonicalLink", link);
        if (!imageLinks.isEmpty()) {
            productAttributes.put("imageLink", imageLinks.get(0));
        }
        if (imageLinks.size() > 1) {
            productAttributes.put("additionalImageLinks", imageLinks.subList(1, imageLinks.size()));
        }
        productAttributes.put("availability", availability(variant == null ? product.getStock() : variant.getStock()));
        productAttributes.put("condition", "NEW");
        productAttributes.put("brand", store.getName());
        productAttributes.put("identifierExists", false);
        productAttributes.put("price", moneyPayload(pricing.original()));
        if (pricing.discounted()) {
            productAttributes.put("salePrice", moneyPayload(pricing.finalAmount()));
        }
        productAttributes.put("sellOnGoogleQuantity", String.valueOf(Math.max(0L, variant == null ? safeLong(product.getStock()) : safeLong(variant.getStock()))));
        Map<String, Object> shippingWeight = shippingWeightPayload(product.getWeight());
        if (shippingWeight != null) {
            productAttributes.put("shippingWeight", shippingWeight);
        }
        List<String> productTypes = categoryPaths(product, locale);
        if (!productTypes.isEmpty()) {
            productAttributes.put("productTypes", productTypes);
        }
        if (product.getMetaTags() != null && !product.getMetaTags().isEmpty()) {
            String metaLabel = product.getMetaTags().stream().limit(5).collect(Collectors.joining(" | "));
            productAttributes.put("customLabel0", metaLabel.length() > 100 ? metaLabel.substring(0, 100) : metaLabel);
        }
        if (variant != null) {
            productAttributes.put("itemGroupId", product.getId().toString());
            if (variantDescriptor.color() != null) {
                productAttributes.put("color", variantDescriptor.color());
            }
            if (variantDescriptor.size() != null) {
                productAttributes.put("size", variantDescriptor.size());
            }
            if (variantDescriptor.material() != null) {
                productAttributes.put("material", variantDescriptor.material());
            }
            if (variantDescriptor.pattern() != null) {
                productAttributes.put("pattern", variantDescriptor.pattern());
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerId", offerId);
        payload.put("contentLanguage", connection.contentLanguage());
        payload.put("feedLabel", connection.feedLabel());
        payload.put("productAttributes", productAttributes);
        return payload;
    }

    private VariantDescriptor variantDescriptor(ProductVariant variant, Locale locale) {
        Map<String, Object> normalized = resolvedVariantOptionMap(variant);
        List<String> optionLabels = new ArrayList<>();
        String color = null;
        String size = null;
        String material = null;
        String pattern = null;
        for (Object value : normalized.values()) {
            UUID optionId = uuid(value);
            if (optionId == null) {
                continue;
            }
            ProductAttributeOption option = productAttributeOptionRepository.findById(optionId)
                    .filter(item -> item.getDeletedAt() == null && item.isEnabled())
                    .orElse(null);
            if (option == null) {
                continue;
            }
            String optionName = translated(option.getName(), locale);
            optionLabels.add(optionName);
            ProductAttribute attribute = option.getProductAttribute();
            String attributeKey = attributeKey(attribute, locale);
            if (matchesAttribute(attributeKey, "color", "colour")) {
                color = optionName;
            } else if (matchesAttribute(attributeKey, "size")) {
                size = optionName;
            } else if (matchesAttribute(attributeKey, "material")) {
                material = optionName;
            } else if (matchesAttribute(attributeKey, "pattern")) {
                pattern = optionName;
            }
        }
        return new VariantDescriptor(String.join(" ", optionLabels).trim(), color, size, material, pattern);
    }

    private Map<String, Object> resolvedVariantOptionMap(ProductVariant variant) {
        Map<String, Object> map = variant.attributeOptionsAsMap();
        if (!map.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((attributeRef, optionRef) -> {
                ProductAttributeOption option = resolveAttributeOption(attributeRef, optionRef);
                if (option != null && option.getProductAttribute() != null) {
                    normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
                }
            });
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Object item : variant.attributeOptionsAsList()) {
            UUID optionId = uuid(item);
            if (optionId == null) {
                continue;
            }
            productAttributeOptionRepository.findById(optionId)
                    .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                    .ifPresent(option -> {
                        if (option.getProductAttribute() != null) {
                            normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
                        }
                    });
        }
        return normalized;
    }

    private ProductAttributeOption resolveAttributeOption(Object attributeRef, Object optionRef) {
        String attributeKey = attributeRef == null ? null : String.valueOf(attributeRef);
        String optionKey = optionRef == null ? null : String.valueOf(optionRef);
        if (optionKey == null || optionKey.isBlank()) {
            return null;
        }
        UUID optionId = uuid(optionKey);
        if (optionId != null) {
            return productAttributeOptionRepository.findById(optionId)
                    .filter(option -> option.getDeletedAt() == null && option.isEnabled())
                    .orElse(null);
        }
        ProductAttribute attribute = resolveAttribute(attributeKey);
        if (attribute != null) {
            return productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId()).stream()
                    .filter(option -> optionKey.equalsIgnoreCase(option.getSlug()) || optionKey.equalsIgnoreCase(option.getValue()))
                    .findFirst()
                    .orElse(null);
        }
        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .flatMap(candidate -> productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(candidate.getId()).stream())
                .filter(option -> optionKey.equalsIgnoreCase(option.getSlug()) || optionKey.equalsIgnoreCase(option.getValue()))
                .findFirst()
                .orElse(null);
    }

    private ProductAttribute resolveAttribute(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return null;
        }
        UUID attributeId = uuid(attributeKey);
        if (attributeId != null) {
            return productAttributeRepository.findById(attributeId)
                    .filter(attribute -> attribute.getDeletedAt() == null && attribute.isEnabled())
                    .orElse(null);
        }
        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(attribute -> attributeKey.equalsIgnoreCase(attribute.getSlug()))
                .findFirst()
                .orElse(null);
    }

    private StorefrontPricingService.Pricing pricingFor(Product product, BigDecimal basePrice) {
        var rule = storefrontPricingService.bestDiscountRule(product).orElse(null);
        if (rule == null) {
            return new StorefrontPricingService.Pricing(basePrice, basePrice, false, null, null);
        }
        BigDecimal discounted = switch (rule.getType() == null ? 0 : rule.getType()) {
            case 1 -> basePrice.subtract(basePrice.multiply(rule.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            case 2 -> basePrice.subtract(rule.getAmount());
            default -> basePrice;
        };
        if (discounted.compareTo(BigDecimal.ZERO) < 0) {
            discounted = BigDecimal.ZERO;
        }
        return new StorefrontPricingService.Pricing(basePrice, discounted, discounted.compareTo(basePrice) < 0, rule.getType(), rule.getAmount());
    }

    private List<String> productImageUrls(Product product) {
        List<Map<String, Object>> gallery = managedFileService.filesFor("product", product.getId(), List.of("cover_photo", "gallery_photo"));
        if (gallery.isEmpty() && product.getCoverPhoto() != null && !product.getCoverPhoto().isEmpty()) {
            Object url = product.getCoverPhoto().get("url");
            if (url != null) {
                return List.of(absoluteUrl(String.valueOf(url)));
            }
        }
        return gallery.stream()
                .map(file -> string(file.get("url")))
                .filter(Objects::nonNull)
                .map(this::absoluteUrl)
                .distinct()
                .toList();
    }

    private String absoluteProductUrl(Store store, Product product) {
        String normalizedBase = storefrontBaseUrl.replaceAll("/+$", "");
        String scopedPrefix = "";
        if (multiStoreEnabled && store != null && store.getSlug() != null && !store.getSlug().isBlank()
                && !DefaultStoreResolver.DEFAULT_STORE_ID.equals(store.getId())) {
            scopedPrefix = "/" + store.getSlug();
        }
        return normalizedBase + scopedPrefix + "/product/" + product.getSlug();
    }

    private String absoluteUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        String normalizedBase = storefrontBaseUrl.replaceAll("/+$", "");
        String normalizedPath = raw.startsWith("/") ? raw : "/" + raw;
        return normalizedBase + normalizedPath;
    }

    private List<String> categoryPaths(Product product, Locale locale) {
        return product.getCategories().stream()
                .filter(category -> category.getDeletedAt() == null && category.isEnabled())
                .map(category -> categoryPath(category, locale))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String categoryPath(ProductCategory category, Locale locale) {
        List<String> parts = new ArrayList<>();
        ProductCategory current = category;
        while (current != null) {
            parts.add(translated(current.getName(), locale));
            current = current.getParent();
        }
        java.util.Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    private String productInputName(GoogleMerchantConnection connection, String offerId) {
        return "accounts/" + connection.merchantId() + "/productInputs/" + connection.contentLanguage() + "~" + connection.feedLabel() + "~" + offerId;
    }

    private void insertRun(UUID runId, UUID connectionId, UUID storeId, UUID triggeredBy, UUID retryOfRunId, String mode) {
        jdbc.update("""
                insert into google_merchant_sync_runs(
                    id, connection_id, store_id, triggered_by, retry_of_run_id, mode, status,
                    requested_items, synced_items, failed_items, summary, created_at, updated_at, started_at
                ) values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, 'running', 0, 0, 0, ?, now(), now(), now())
                """,
                runId.toString(),
                connectionId.toString(),
                storeId.toString(),
                triggeredBy == null ? null : triggeredBy.toString(),
                retryOfRunId == null ? null : retryOfRunId.toString(),
                mode,
                mode.equals("retry_failed") ? "Retrying failed Google Merchant items." : "Running Google Merchant sync."
        );
    }

    private void insertSyncItem(UUID runId, SyncTarget target, String status, Map<String, Object> responsePayload, String errorMessage) {
        jdbc.update("""
                insert into google_merchant_sync_items(
                    id, run_id, product_id, variant_id, offer_id, title, status, action,
                    request_payload, response_payload, error_message, created_at, updated_at
                ) values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, now(), now())
                """,
                UUID.randomUUID().toString(),
                runId.toString(),
                target.productId() == null ? null : target.productId().toString(),
                target.variantId() == null ? null : target.variantId().toString(),
                target.offerId(),
                target.title(),
                status,
                target.action(),
                json(target.requestPayload()),
                json(responsePayload),
                errorMessage
        );
    }

    private Optional<GoogleMerchantConnection> loadConnection(UUID storeId) {
        List<GoogleMerchantConnection> rows = jdbc.query("""
                select id,
                       store_id,
                       enabled,
                       merchant_id,
                       data_source_name,
                       data_source_display_name,
                       content_language,
                       feed_label,
                       target_country,
                       service_account_json,
                       last_sync_at,
                       last_sync_status,
                       last_sync_error
                from google_merchant_connections
                where store_id = ?::uuid
                  and deleted_at is null
                limit 1
                """, (rs, rowNum) -> new GoogleMerchantConnection(
                rs.getObject("id", UUID.class),
                rs.getObject("store_id", UUID.class),
                rs.getBoolean("enabled"),
                rs.getString("merchant_id"),
                rs.getString("data_source_name"),
                rs.getString("data_source_display_name"),
                rs.getString("content_language"),
                rs.getString("feed_label"),
                rs.getString("target_country"),
                rs.getString("service_account_json"),
                rs.getObject("last_sync_at", OffsetDateTime.class),
                rs.getString("last_sync_status"),
                rs.getString("last_sync_error")
        ), storeId.toString());
        return rows.stream().findFirst();
    }

    private void saveConnection(GoogleMerchantConnection connection) {
        jdbc.update("""
                insert into google_merchant_connections(
                    id, store_id, enabled, merchant_id, data_source_name, data_source_display_name,
                    content_language, feed_label, target_country, service_account_json,
                    last_sync_at, last_sync_status, last_sync_error, created_at, updated_at
                ) values (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?, ?, now(), now())
                on conflict (store_id) where deleted_at is null do update set
                    enabled = excluded.enabled,
                    merchant_id = excluded.merchant_id,
                    data_source_name = excluded.data_source_name,
                    data_source_display_name = excluded.data_source_display_name,
                    content_language = excluded.content_language,
                    feed_label = excluded.feed_label,
                    target_country = excluded.target_country,
                    service_account_json = excluded.service_account_json,
                    last_sync_at = excluded.last_sync_at,
                    last_sync_status = excluded.last_sync_status,
                    last_sync_error = excluded.last_sync_error,
                    updated_at = now()
                """,
                connection.id().toString(),
                connection.storeId().toString(),
                connection.enabled(),
                connection.merchantId(),
                connection.dataSourceName(),
                connection.dataSourceDisplayName(),
                connection.contentLanguage(),
                connection.feedLabel(),
                connection.targetCountry(),
                connection.serviceAccountJson(),
                connection.lastSyncAt() == null ? null : connection.lastSyncAt().toString(),
                connection.lastSyncStatus(),
                connection.lastSyncError()
        );
    }

    private Map<String, Object> connectionRow(GoogleMerchantConnection connection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", connection.id());
        row.put("enabled", connection.enabled());
        row.put("merchant_id", stringOrEmpty(connection.merchantId()));
        row.put("data_source_name", stringOrEmpty(connection.dataSourceName()));
        row.put("data_source_display_name", stringOrEmpty(connection.dataSourceDisplayName()));
        row.put("content_language", stringOrDefault(connection.contentLanguage(), "en"));
        row.put("feed_label", stringOrDefault(connection.feedLabel(), "GB"));
        row.put("target_country", stringOrDefault(connection.targetCountry(), "GB"));
        row.put("service_account_json", stringOrEmpty(connection.serviceAccountJson()));
        row.put("last_sync_at", connection.lastSyncAt() == null ? null : connection.lastSyncAt().format(ISO_FORMATTER));
        row.put("last_sync_status", connection.lastSyncStatus());
        row.put("last_sync_error", connection.lastSyncError());
        row.put("connected", connection.enabled()
                && connection.merchantId() != null && !connection.merchantId().isBlank()
                && connection.serviceAccountJson() != null && !connection.serviceAccountJson().isBlank());
        return row;
    }

    private List<Map<String, Object>> recentRuns(UUID storeId) {
        List<GoogleMerchantSyncRun> runs = jdbc.query("""
                select r.id,
                       r.connection_id,
                       r.store_id,
                       r.triggered_by,
                       r.retry_of_run_id,
                       r.mode,
                       r.status,
                       r.requested_items,
                       r.synced_items,
                       r.failed_items,
                       r.summary,
                       r.created_at,
                       r.updated_at,
                       r.started_at,
                       r.finished_at
                from google_merchant_sync_runs r
                where r.store_id = ?::uuid
                order by r.created_at desc
                limit 12
                """, (rs, rowNum) -> new GoogleMerchantSyncRun(
                rs.getObject("id", UUID.class),
                rs.getObject("connection_id", UUID.class),
                rs.getObject("store_id", UUID.class),
                rs.getObject("triggered_by", UUID.class),
                rs.getObject("retry_of_run_id", UUID.class),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getInt("requested_items"),
                rs.getInt("synced_items"),
                rs.getInt("failed_items"),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        ), storeId.toString());
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Map<String, Object>>> itemsByRun = syncItemsByRun(runs.stream().map(GoogleMerchantSyncRun::id).toList());
        return runs.stream().map(run -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", run.id());
            row.put("mode", run.mode());
            row.put("status", run.status());
            row.put("requested_items", run.requestedItems());
            row.put("synced_items", run.syncedItems());
            row.put("failed_items", run.failedItems());
            row.put("summary", run.summary());
            row.put("retry_of_run_id", run.retryOfRunId());
            row.put("created_at", format(run.createdAt()));
            row.put("started_at", format(run.startedAt()));
            row.put("finished_at", format(run.finishedAt()));
            row.put("items", itemsByRun.getOrDefault(run.id(), List.of()));
            row.put("can_retry", run.failedItems() > 0);
            return row;
        }).toList();
    }

    private Map<UUID, List<Map<String, Object>>> syncItemsByRun(List<UUID> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = runIds.stream().map(id -> "?::uuid").collect(Collectors.joining(", "));
        List<Object> params = runIds.stream().map(UUID::toString).collect(Collectors.toCollection(ArrayList::new));
        String sql = """
                select id,
                       run_id,
                       product_id,
                       variant_id,
                       offer_id,
                       title,
                       status,
                       action,
                       error_message,
                       created_at
                from google_merchant_sync_items
                where run_id in (%s)
                order by created_at asc
                """.formatted(placeholders);
        List<Map<String, Object>> rows = jdbc.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class));
                    row.put("run_id", rs.getObject("run_id", UUID.class));
                    row.put("product_id", rs.getObject("product_id", UUID.class));
                    row.put("variant_id", rs.getObject("variant_id", UUID.class));
                    row.put("offer_id", rs.getString("offer_id"));
                    row.put("title", rs.getString("title"));
                    row.put("status", rs.getString("status"));
                    row.put("action", rs.getString("action"));
                    row.put("error_message", rs.getString("error_message"));
                    row.put("created_at", format(rs.getObject("created_at", OffsetDateTime.class)));
                    return row;
                },
                params.toArray()
        );
        Map<UUID, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID runId = (UUID) row.get("run_id");
            grouped.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private GoogleMerchantSyncRun syncRun(UUID storeId, UUID runId) {
        return jdbc.query("""
                select id,
                       connection_id,
                       store_id,
                       triggered_by,
                       retry_of_run_id,
                       mode,
                       status,
                       requested_items,
                       synced_items,
                       failed_items,
                       summary,
                       created_at,
                       updated_at,
                       started_at,
                       finished_at
                from google_merchant_sync_runs
                where id = ?::uuid
                  and store_id = ?::uuid
                limit 1
                """, (rs, rowNum) -> new GoogleMerchantSyncRun(
                rs.getObject("id", UUID.class),
                rs.getObject("connection_id", UUID.class),
                rs.getObject("store_id", UUID.class),
                rs.getObject("triggered_by", UUID.class),
                rs.getObject("retry_of_run_id", UUID.class),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getInt("requested_items"),
                rs.getInt("synced_items"),
                rs.getInt("failed_items"),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        ), runId.toString(), storeId.toString()).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Google Merchant sync run not found."));
    }

    private GoogleMerchantConnection requireConfiguredConnection(UUID storeId) {
        GoogleMerchantConnection connection = loadConnection(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google Merchant settings have not been configured for this store."));
        if (!connection.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google Merchant syncing is disabled for this store.");
        }
        if (connection.merchantId() == null || connection.merchantId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google Merchant merchant ID is required.");
        }
        if (connection.serviceAccountJson() == null || connection.serviceAccountJson().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google Merchant service account JSON is required.");
        }
        return connection;
    }

    private GoogleMerchantConnection mergedConnection(GoogleMerchantConnection current, Map<String, Object> body) {
        boolean enabled = body != null && body.containsKey("enabled") ? bool(body.get("enabled"), current.enabled()) : current.enabled();
        String merchantId = trimmed(body == null || !body.containsKey("merchant_id") ? current.merchantId() : body.get("merchant_id"));
        String dataSourceName = trimmed(body == null || !body.containsKey("data_source_name") ? current.dataSourceName() : body.get("data_source_name"));
        String dataSourceDisplayName = trimmed(body == null || !body.containsKey("data_source_display_name") ? current.dataSourceDisplayName() : body.get("data_source_display_name"));
        String contentLanguage = normalizedLanguage(body == null || !body.containsKey("content_language") ? current.contentLanguage() : body.get("content_language"));
        String feedLabel = normalizedFeedLabel(body == null || !body.containsKey("feed_label") ? current.feedLabel() : body.get("feed_label"));
        String targetCountry = normalizedCountry(body == null || !body.containsKey("target_country") ? current.targetCountry() : body.get("target_country"));
        String serviceAccountJson = body == null || !body.containsKey("service_account_json")
                ? current.serviceAccountJson()
                : normalizedJson(body.get("service_account_json"));
        return new GoogleMerchantConnection(
                current.id(),
                current.storeId(),
                enabled,
                merchantId,
                dataSourceName,
                dataSourceDisplayName,
                contentLanguage,
                feedLabel,
                targetCountry,
                serviceAccountJson,
                current.lastSyncAt(),
                current.lastSyncStatus(),
                current.lastSyncError()
        );
    }

    private GoogleMerchantConnection defaultConnection(UUID storeId) {
        return new GoogleMerchantConnection(
                UUID.randomUUID(),
                storeId,
                false,
                "",
                "",
                "",
                "en",
                "GB",
                "GB",
                "",
                null,
                null,
                null
        );
    }

    private Store targetStore(String email) {
        if (!multiStoreEnabled) {
            return adminStoreContext.defaultStore()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No store is configured."));
        }
        return adminStoreContext.activeStoreOptional(email)
                .or(() -> adminStoreContext.defaultStore())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available."));
    }

    private UUID userIdFor(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(user -> user.getId())
                .orElse(null);
    }

    private Map<String, Object> storeRow(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", multiStoreEnabled && DefaultStoreResolver.DEFAULT_STORE_ID.equals(store.getId()) ? "Marketplace" : store.getName());
        row.put("slug", store.getSlug());
        row.put("status", store.getStatus() == null ? null : store.getStatus().name().toLowerCase(Locale.ROOT));
        return row;
    }

    private Map<String, Object> moneyPayload(BigDecimal amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("amountMicros", micros(amount));
        row.put("currencyCode", "GBP");
        return row;
    }

    private String micros(BigDecimal amount) {
        return safeMoney(amount)
                .multiply(BigDecimal.valueOf(1_000_000L))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String availability(Long stock) {
        return safeLong(stock) > 0L ? "IN_STOCK" : "OUT_OF_STOCK";
    }

    private Map<String, Object> shippingWeightPayload(Integer grams) {
        if (grams == null || grams <= 0) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", grams);
        row.put("unit", "g");
        return row;
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizedJson(Object raw) {
        String text = trimmed(raw);
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google service account JSON is not valid JSON.");
        }
    }

    private String normalizedLanguage(Object raw) {
        String value = trimmed(raw);
        return value == null || value.isBlank() ? "en" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizedFeedLabel(Object raw) {
        String value = trimmed(raw);
        if (value == null || value.isBlank()) {
            return "GB";
        }
        return value.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizedCountry(Object raw) {
        String value = trimmed(raw);
        if (value == null || value.isBlank()) {
            return "GB";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private String json(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String translated(Map<String, String> value, Locale locale) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String language = locale == null ? DEFAULT_LOCALE.getLanguage() : locale.getLanguage();
        if (value.containsKey(language) && value.get(language) != null && !value.get(language).isBlank()) {
            return value.get(language);
        }
        if (value.containsKey(DEFAULT_LOCALE.getLanguage()) && value.get(DEFAULT_LOCALE.getLanguage()) != null && !value.get(DEFAULT_LOCALE.getLanguage()).isBlank()) {
            return value.get(DEFAULT_LOCALE.getLanguage());
        }
        return value.values().stream().filter(Objects::nonNull).filter(item -> !item.isBlank()).findFirst().orElse("");
    }

    private Locale localeFor(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return Locale.forLanguageTag(languageCode);
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String attributeKey(ProductAttribute attribute, Locale locale) {
        if (attribute == null) {
            return "";
        }
        return (attribute.getSlug() + " " + translated(attribute.getName(), locale)).toLowerCase(Locale.ROOT);
    }

    private boolean matchesAttribute(String key, String... matches) {
        for (String match : matches) {
            if (key.contains(match.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String trimmed(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOrDefault(Object value, String fallback) {
        String text = string(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String stringOrEmpty(Object value) {
        return stringOrDefault(value, "");
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(ISO_FORMATTER);
    }

    private record GoogleMerchantConnection(
            UUID id,
            UUID storeId,
            boolean enabled,
            String merchantId,
            String dataSourceName,
            String dataSourceDisplayName,
            String contentLanguage,
            String feedLabel,
            String targetCountry,
            String serviceAccountJson,
            OffsetDateTime lastSyncAt,
            String lastSyncStatus,
            String lastSyncError
    ) {
    }

    private record GoogleMerchantSyncRun(
            UUID id,
            UUID connectionId,
            UUID storeId,
            UUID triggeredBy,
            UUID retryOfRunId,
            String mode,
            String status,
            int requestedItems,
            int syncedItems,
            int failedItems,
            String summary,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
    }

    private record SyncTarget(
            UUID productId,
            UUID variantId,
            String offerId,
            String title,
            String action,
            Map<String, Object> requestPayload
    ) {
    }

    private record SyncResult(boolean success) {
    }

    private record VariantDescriptor(
            String label,
            String color,
            String size,
            String material,
            String pattern
    ) {
        private static VariantDescriptor empty() {
            return new VariantDescriptor("", null, null, null, null);
        }
    }
}
