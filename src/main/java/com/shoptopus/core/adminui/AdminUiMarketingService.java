package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.admin.AdminBannerService;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.Rating;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.engagement.repository.RatingRepository;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiMarketingService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RatingRepository ratingRepository;
    private final BannerRepository bannerRepository;
    private final AdminBannerService adminBannerService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final EngagementService engagementService;
    private final AdminStoreContext adminStoreContext;

    public AdminUiMarketingService(
            RatingRepository ratingRepository,
            BannerRepository bannerRepository,
            AdminBannerService adminBannerService,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            EngagementService engagementService,
            AdminStoreContext adminStoreContext
    ) {
        this.ratingRepository = ratingRepository;
        this.bannerRepository = bannerRepository;
        this.adminBannerService = adminBannerService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.engagementService = engagementService;
        this.adminStoreContext = adminStoreContext;
    }

    public Map<String, Object> ratings(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<Rating> filtered = ratingRepository.findAll().stream()
                .filter(rating -> rating.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(rating -> matchesRatingSearch(rating, search))
                .filter(rating -> matchesRatingStatus(rating, status))
                .sorted(Comparator.comparing(Rating::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::ratingRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", List.of(
                                Map.of("value", "all", "label", "All"),
                                Map.of("value", "enabled", "label", "Enabled"),
                                Map.of("value", "disabled", "label", "Disabled"),
                                Map.of("value", "verified", "label", "Verified"),
                                Map.of("value", "unverified", "label", "Unverified")
                        )
                )
        );
    }

    public Map<String, Object> rating(String id) {
        return ratingDetail(findRating(id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateRatingStatus(String id, Map<String, Object> body) {
        Rating rating = findRating(id);
        if (body == null || !body.containsKey("enabled")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The enabled field is required.");
        }
        rating.setEnabled(readBoolean(body.get("enabled"), rating.isEnabled()));
        ratingRepository.save(rating);
        engagementService.recalculateProductRatingIfApplicable(rating);
        return ratingDetail(rating);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateRatingVerification(String id, Map<String, Object> body) {
        Rating rating = findRating(id);
        if (body == null || !body.containsKey("verified")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The verified field is required.");
        }
        rating.setVerified(readBoolean(body.get("verified"), rating.isVerified()));
        ratingRepository.save(rating);
        engagementService.recalculateProductRatingIfApplicable(rating);
        return ratingDetail(rating);
    }

    public Map<String, Object> banners(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 20 : Math.min(perPage, 100);

        List<Banner> filtered = bannerRepository.findAll().stream()
                .filter(banner -> banner.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .filter(banner -> matchesBannerSearch(banner, search))
                .filter(banner -> matchesBannerStatus(banner, status))
                .sorted(Comparator.comparing(Banner::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::bannerRow).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", List.of(
                                Map.of("value", "all", "label", "All"),
                                Map.of("value", "enabled", "label", "Enabled"),
                                Map.of("value", "disabled", "label", "Disabled")
                        )
                )
        );
    }

    public Map<String, Object> banner(String id) {
        return bannerDetail(findBanner(id));
    }

    public Map<String, Object> bannerFormOptions() {
        return Map.of(
                "booleans", List.of(
                        Map.of("value", true, "label", "Enabled"),
                        Map.of("value", false, "label", "Disabled")
                )
        );
    }

    public Map<String, Object> createBanner(Map<String, Object> body) {
        Map<String, Object> created = adminBannerService.create(body);
        scopedBanner(String.valueOf(created.get("id")));
        return banner(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateBanner(String id, Map<String, Object> body) {
        scopedBanner(id);
        adminBannerService.update(id, body);
        scopedBanner(id);
        return banner(id);
    }

    public Map<String, Object> deleteBanner(String id) {
        scopedBanner(id);
        adminBannerService.delete(id);
        return Map.of("id", id);
    }

    private boolean matchesRatingSearch(Rating rating, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return contains(rating.getTitle(), term)
                || contains(rating.getDescription(), term)
                || contains(rating.getRatableType(), term)
                || (rating.getUser() != null && contains(rating.getUser().getEmail(), term))
                || (rating.getUser() != null && contains(rating.getUser().getName(), term));
    }

    private boolean matchesBannerSearch(Banner banner, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return contains(banner.getSlug(), term)
                || contains(banner.getUrl(), term)
                || banner.getName().values().stream().anyMatch(value -> contains(value, term))
                || banner.getButtonText().values().stream().anyMatch(value -> contains(value, term))
                || banner.getContent().values().stream().anyMatch(value -> contains(value, term));
    }

    private boolean matchesRatingStatus(Rating rating, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> rating.isEnabled();
            case "disabled" -> !rating.isEnabled();
            case "verified" -> rating.isVerified();
            case "unverified" -> !rating.isVerified();
            default -> true;
        };
    }

    private boolean matchesBannerStatus(Banner banner, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "enabled" -> banner.isEnabled();
            case "disabled" -> !banner.isEnabled();
            default -> true;
        };
    }

    private Map<String, Object> ratingRow(Rating rating) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rating.getId());
        row.put("rating", rating.getRating());
        row.put("title", rating.getTitle());
        row.put("description", rating.getDescription());
        row.put("enabled", rating.isEnabled());
        row.put("verified", rating.isVerified());
        row.put("status_label", ratingStatusLabel(rating));
        row.put("ratable_type", rating.getRatableType());
        row.put("ratable_id", rating.getRatableId());
        row.put("relation", relationRow(rating));
        row.put("created_at", format(rating.getCreatedAt()));
        if (rating.getUser() == null) {
            row.put("user", null);
        } else {
            row.put("user", Map.of(
                    "id", rating.getUser().getId(),
                    "name", rating.getUser().getName(),
                    "email", rating.getUser().getEmail()
            ));
        }
        return row;
    }

    private Map<String, Object> ratingDetail(Rating rating) {
        Map<String, Object> row = new LinkedHashMap<>(ratingRow(rating));
        row.put("slug", rating.getSlug());
        row.put("language_prefix", rating.getLanguagePrefix());
        row.put("updated_at", format(rating.getUpdatedAt()));
        row.put("image", null);
        row.put("video", null);
        row.put("moderation_options", Map.of(
                "enabled", List.of(
                        Map.of("value", true, "label", "Enabled"),
                        Map.of("value", false, "label", "Disabled")
                ),
                "verified", List.of(
                        Map.of("value", true, "label", "Verified"),
                        Map.of("value", false, "label", "Unverified")
                )
        ));
        return row;
    }

    private Map<String, Object> relationRow(Rating rating) {
        if (rating.getRatableId() == null || rating.getRatableType() == null) {
            return null;
        }
        if ("product".equalsIgnoreCase(rating.getRatableType())) {
            return productRepository.findById(rating.getRatableId())
                    .map(product -> relation("product", product.getId(), translatedName(product.getName()), "/products/" + product.getId()))
                    .orElse(relation("product", rating.getRatableId(), String.valueOf(rating.getRatableId()), "/products/" + rating.getRatableId()));
        }
        if ("order".equalsIgnoreCase(rating.getRatableType())) {
            return orderRepository.findByIdAndDeletedAtIsNull(rating.getRatableId())
                    .map(order -> relation("order", order.getId(), order.getSlug(), "/orders/" + order.getId()))
                    .orElse(relation("order", rating.getRatableId(), String.valueOf(rating.getRatableId()), "/orders/" + rating.getRatableId()));
        }
        return relation(rating.getRatableType(), rating.getRatableId(), String.valueOf(rating.getRatableId()), null);
    }

    private Map<String, Object> relation(String type, Object id, String label, String href) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("id", id);
        row.put("label", label);
        row.put("href", href);
        return row;
    }

    private Map<String, Object> bannerRow(Banner banner) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", banner.getId());
        row.put("name", banner.getName());
        row.put("name_display", translatedName(banner.getName()));
        row.put("button_text", banner.getButtonText());
        row.put("button_text_display", translatedName(banner.getButtonText()));
        row.put("content", banner.getContent());
        row.put("content_display", translatedName(banner.getContent()));
        row.put("slug", banner.getSlug());
        row.put("url", banner.getUrl());
        row.put("enabled", banner.isEnabled());
        row.put("status_label", banner.isEnabled() ? "Enabled" : "Disabled");
        row.put("click_count", banner.getClickCount());
        row.put("image", banner.getImage());
        row.put("video", adminBannerService.show(String.valueOf(banner.getId())).get("video"));
        row.put("created_at", format(banner.getCreatedAt()));
        return row;
    }

    private Map<String, Object> bannerDetail(Banner banner) {
        Map<String, Object> row = new LinkedHashMap<>(bannerRow(banner));
        row.put("image", banner.getImage());
        row.put("updated_at", format(banner.getUpdatedAt()));
        row.put("form_options", bannerFormOptions());
        return row;
    }

    private String translatedName(Map<String, String> name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.containsKey("en")) {
            return String.valueOf(name.get("en"));
        }
        return String.valueOf(name.values().iterator().next());
    }

    private String ratingStatusLabel(Rating rating) {
        if (!rating.isEnabled()) {
            return "Disabled";
        }
        return rating.isVerified() ? "Verified" : "Pending verification";
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private Map<String, Object> pageMeta(int page, int perPage, int total) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("per_page", perPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / perPage));
        return meta;
    }

    private Rating findRating(String id) {
        try {
            return ratingRepository.findById(UUID.fromString(id))
                    .filter(item -> item.getDeletedAt() == null)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rating not found", "Rating not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Rating not found", "Rating not found");
        }
    }

    private Banner findBanner(String id) {
        try {
            return bannerRepository.findById(UUID.fromString(id))
                    .filter(item -> item.getDeletedAt() == null)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Banner not found", "Banner not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Banner not found", "Banner not found");
        }
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return fallback;
        }
        return "1".equals(text) || "true".equals(text) || "yes".equals(text) || "on".equals(text);
    }

    private Banner scopedBanner(String id) {
        Banner banner = findBanner(id);
        if (adminStoreContext.multiStoreEnabled() && banner.getStore() == null) {
            banner.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
            return bannerRepository.save(banner);
        }
        return banner;
    }

    private boolean matchesActiveStore(Rating rating) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && rating.getStore() != null && activeStore.getId().equals(rating.getStore().getId());
    }

    private boolean matchesActiveStore(Banner banner) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && banner.getStore() != null && activeStore.getId().equals(banner.getStore().getId());
    }
}
