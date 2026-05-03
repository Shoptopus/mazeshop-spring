package com.shoptopus.core.engagement;

import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.engagement.dto.BannerResponse;
import com.shoptopus.core.engagement.dto.RatingRequest;
import com.shoptopus.core.engagement.dto.RatingResponse;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.engagement.repository.NotificationRepository;
import com.shoptopus.core.engagement.repository.RatingRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.storefront.StorefrontStoreResolver;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EngagementService {

    private final RatingRepository ratingRepository;
    private final BannerRepository bannerRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ManagedFileService managedFileService;
    private final JdbcTemplate jdbcTemplate;
    private final StorefrontStoreResolver storefrontStoreResolver;

    public EngagementService(
            RatingRepository ratingRepository,
            BannerRepository bannerRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            ManagedFileService managedFileService,
            JdbcTemplate jdbcTemplate,
            StorefrontStoreResolver storefrontStoreResolver
    ) {
        this.ratingRepository = ratingRepository;
        this.bannerRepository = bannerRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.managedFileService = managedFileService;
        this.jdbcTemplate = jdbcTemplate;
        this.storefrontStoreResolver = storefrontStoreResolver;
    }

    @Transactional("mainTransactionManager")
    public RatingResponse rateProduct(UUID productId, RatingRequest request, String userEmail) {
        return saveRating(userEmail, null, "product", productId, request);
    }

    @Transactional("mainTransactionManager")
    public RatingResponse saveRating(String userEmail, UUID orderId, String ratableType, UUID ratableId, RatingRequest request) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        validateReviewTarget(user, orderId, ratableType, ratableId);

        var rating = ratingRepository.findByUserIdAndRatableTypeAndRatableIdAndDeletedAtIsNull(user.getId(), ratableType, ratableId)
                .orElseGet(Rating::new);
        boolean created = rating.getId() == null;
        rating.setUser(user);
        rating.setRatableType(ratableType);
        rating.setRatableId(ratableId);
        rating.setRating(request.rating());
        rating.setTitle(request.title());
        rating.setDescription(request.description());
        rating.setLanguagePrefix(request.languagePrefix() == null ? "en" : request.languagePrefix());
        if (rating.getSlug() == null || rating.getSlug().isBlank()) {
            rating.setSlug("rating-" + UUID.randomUUID());
        }
        ratingRepository.save(rating);
        managedFileService.attachSingle("rating", rating.getId(), "rating_image", request.image());
        managedFileService.attachSingle("rating", rating.getId(), "rating_video", request.video());
        recalculateProductRatingIfApplicable(rating);
        Map<String, Object> notificationData = new LinkedHashMap<>();
        notificationData.put("message", user.getName() + " left a " + request.rating() + "-star review.");
        notificationData.put("ratable_type", ratableType);
        notificationData.put("ratable_id", ratableId);
        notificationData.put("order_id", orderId);
        notificationData.put("rating", request.rating());
        notifyAdmins(
                "new_rating",
                created ? "New review received" : "Review updated",
                notificationData
        );

        return toResponse(rating);
    }

    public List<RatingResponse> getProductRatings(UUID productId) {
        return ratingRepository.findByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue("product", productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ratingFeed(String ratableType, UUID ratableId, Integer starRating, Integer page, Integer perPage) {
        String safeType = ratableType == null || ratableType.isBlank() ? null : ratableType.trim().toLowerCase(Locale.ROOT);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 10 : Math.min(perPage, 50);

        List<Rating> base = ratingsForBreakdown(safeType, ratableId);
        List<Rating> filtered = base.stream()
                .filter(rating -> starRating == null || rating.getRating() != null && rating.getRating().equals(starRating))
                .sorted(Comparator.comparing(Rating::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("ratable_type", safeType);
        filters.put("ratable_id", ratableId);
        filters.put("star_rating", starRating);
        response.put("filters", filters);
        response.put("summary", Map.of(
                "average_rating", average(filtered),
                "total_reviews", (long) filtered.size(),
                "breakdown", breakdown(base)
        ));
        response.put("items", filtered.subList(fromIndex, toIndex).stream().map(this::ratingFeedRow).toList());
        response.put("meta", Map.of(
                "page", safePage,
                "per_page", safePerPage,
                "total", total,
                "total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage)
        ));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userRating(String userEmail, String ratableType, UUID ratableId) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        return ratingRepository.findByUserIdAndRatableTypeAndRatableIdAndDeletedAtIsNull(user.getId(), ratableType, ratableId)
                .map(this::ratingFeedRow)
                .orElse(null);
    }

    @Transactional("mainTransactionManager")
    public void recalculateProductRating(UUID productId) {
        productRepository.findById(productId).ifPresent(product -> {
            List<Rating> activeRatings = ratingRepository.findByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue("product", productId);
            product.setRating(activeRatings.isEmpty() ? 0d : average(activeRatings));
            productRepository.save(product);
            recalculateStoreRating(product.getStore());
        });
    }

    @Transactional("mainTransactionManager")
    public void recalculateProductRatingIfApplicable(Rating rating) {
        if (rating != null && "product".equalsIgnoreCase(rating.getRatableType()) && rating.getRatableId() != null) {
            recalculateProductRating(rating.getRatableId());
        }
    }

    @Transactional(readOnly = true)
    public long productReviewCount(UUID productId) {
        return productId == null ? 0L : ratingRepository.countByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue("product", productId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> productReviewCounts(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new LinkedHashMap<>();
        ratingRepository.countByRatableTypeAndRatableIdInAndDeletedAtIsNullAndEnabledTrueGroupByRatableId("product", productIds)
                .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        return counts;
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public List<BannerResponse> getBanners(Locale locale) {
        return getBanners(locale, null);
    }

    @Transactional(readOnly = true, transactionManager = "mainTransactionManager")
    public List<BannerResponse> getBanners(Locale locale, String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        return bannerRepository.findByDeletedAtIsNullAndEnabledTrueOrderByCreatedAtDesc().stream()
                .filter(banner -> matchesStore(banner.getStore(), resolvedStore))
                .map(b -> new BannerResponse(
                        b.getId(),
                        TranslatedText.resolve(b.getName(), locale),
                        b.getSlug(),
                        toPublicMediaUrl(b.getImage()),
                        toPublicMediaUrl(managedFileService.findAttachedSingle("banner", b.getId(), "banner_video")),
                        TranslatedText.resolve(b.getButtonText(), locale),
                        TranslatedText.resolve(b.getContent(), locale),
                        b.getUrl(),
                        b.getClickCount()
                ))
                .toList();
    }

    @Transactional("mainTransactionManager")
    public void clickBanner(UUID bannerId) {
        int updated = jdbcTemplate.update("""
                update banners
                set click_count = coalesce(click_count, 0) + 1,
                    updated_at = now()
                where id = ?::uuid
                  and deleted_at is null
                """, bannerId.toString());
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Banner not found", "Banner not found");
        }
    }

    @Transactional("mainTransactionManager")
    public void recalculateStoreRating(Store store) {
        if (store == null) {
            return;
        }
        List<Rating> activeRatings = ratingRepository.findByStoreIdAndDeletedAtIsNullAndEnabledTrue(store.getId());
        store.setRatingCount(activeRatings.size());
        store.setRatingAverage(java.math.BigDecimal.valueOf(activeRatings.isEmpty() ? 0d : average(activeRatings))
                .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private boolean matchesStore(Store store, StorefrontStoreResolver.ResolvedStore resolvedStore) {
        if (!resolvedStore.multiStoreEnabled()) {
            return true;
        }
        if (resolvedStore.superstore()) {
            return store != null && store.getStatus() == StoreStatus.ACTIVE;
        }
        return store != null
                && resolvedStore.store() != null
                && resolvedStore.store().getId().equals(store.getId())
                && store.getStatus() == StoreStatus.ACTIVE;
    }

    private String toPublicMediaUrl(Map<String, Object> file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Object path = file.get("path");
        if (!(path instanceof String mediaPath) || mediaPath.isBlank()) {
            Object directUrl = file.get("url");
            if (directUrl instanceof String direct && !direct.isBlank()) {
                return absoluteUrl(direct);
            }
            return null;
        }
        return absoluteUrl(managedFileService.publicUrl(mediaPath));
    }

    private void validateReviewTarget(User user, UUID orderId, String ratableType, UUID ratableId) {
        if (ratableType == null || ratableType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ratable type is required.");
        }
        if (ratableId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ratable id is required.");
        }
        if (!"product".equalsIgnoreCase(ratableType) && !"order".equalsIgnoreCase(ratableType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ratable type is invalid.");
        }
        if ("product".equalsIgnoreCase(ratableType)) {
            productRepository.findById(ratableId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Product not found."));
        } else {
            orderRepository.findByIdAndDeletedAtIsNull(ratableId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order not found."));
        }
        if (orderId == null) {
            return;
        }
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found"));
        if ("order".equalsIgnoreCase(ratableType) && !order.getId().equals(ratableId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order review target is invalid.");
        }
        if ("product".equalsIgnoreCase(ratableType) && order.getItems().stream().noneMatch(item -> item.getProduct() != null && ratableId.equals(item.getProduct().getId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Product was not part of this order.");
        }
    }

    private RatingResponse toResponse(Rating rating) {
        long reviewCount = rating.getRatableId() == null ? 0L : ratingRepository.countByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue(rating.getRatableType(), rating.getRatableId());
        List<Rating> ratings = rating.getRatableId() == null ? List.of() : ratingRepository.findByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue(rating.getRatableType(), rating.getRatableId());
        return new RatingResponse(
                rating.getId(),
                rating.getRating(),
                rating.getTitle(),
                rating.getDescription(),
                rating.getLanguagePrefix(),
                rating.isVerified(),
                rating.getRatableType(),
                rating.getRatableId(),
                reviewCount,
                average(ratings),
                managedFileService.findAttachedSingle("rating", rating.getId(), "rating_image"),
                managedFileService.findAttachedSingle("rating", rating.getId(), "rating_video")
        );
    }

    private Map<String, Object> ratingFeedRow(Rating rating) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rating.getId());
        row.put("rating", rating.getRating());
        row.put("title", rating.getTitle());
        row.put("content", rating.getDescription());
        row.put("language_prefix", rating.getLanguagePrefix());
        row.put("verified", rating.isVerified());
        row.put("created_at", rating.getCreatedAt());
        row.put("updated_at", rating.getUpdatedAt());
        row.put("ratable_type", rating.getRatableType());
        row.put("ratable_id", rating.getRatableId());
        row.put("image", rating.getId() == null ? null : toPublicMediaUrl(managedFileService.findAttachedSingle("rating", rating.getId(), "rating_image")));
        row.put("video", rating.getId() == null ? null : toPublicMediaUrl(managedFileService.findAttachedSingle("rating", rating.getId(), "rating_video")));
        if (rating.getUser() != null) {
            row.put("author", Map.of(
                    "id", rating.getUser().getId(),
                    "name", rating.getUser().getName(),
                    "email", rating.getUser().getEmail()
            ));
        } else {
            row.put("author", null);
        }
        return row;
    }

    private List<Rating> ratingsForBreakdown(String ratableType, UUID ratableId) {
        if (ratableType == null) {
            return ratingRepository.findAll().stream()
                    .filter(rating -> rating.getDeletedAt() == null && rating.isEnabled())
                    .toList();
        }
        if (ratableId == null) {
            return ratingRepository.findByRatableTypeAndDeletedAtIsNullAndEnabledTrue(ratableType);
        }
        return ratingRepository.findByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue(ratableType, ratableId);
    }

    private List<Map<String, Object>> breakdown(List<Rating> ratings) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            final int currentStar = star;
            long count = ratings.stream().filter(rating -> rating.getRating() != null && rating.getRating() == currentStar).count();
            rows.add(Map.of("rating", star, "count", count));
        }
        return rows;
    }

    private double average(List<Rating> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return 0d;
        }
        return Math.round(ratings.stream()
                .filter(rating -> rating.getRating() != null)
                .mapToInt(Rating::getRating)
                .average()
                .orElse(0d) * 10d) / 10d;
    }

    private String absoluteUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().path(value.startsWith("/") ? value : "/" + value).toUriString();
        } catch (Exception ex) {
            return value;
        }
    }

    public List<Notification> getNotifications(String email) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public long unreadNotifications(String email) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow();
        return notificationRepository.countByUserIdAndReadAtIsNull(user.getId());
    }

    @Transactional("mainTransactionManager")
    public void clearNotifications(String email) {
        var notifications = getNotifications(email);
        notifications.forEach(n -> n.setReadAt(OffsetDateTime.now()));
        notificationRepository.saveAllAndFlush(notifications);
    }

    @Transactional("mainTransactionManager")
    public void notifyUser(User user, String type, String title, Map<String, Object> data) {
        if (user == null || user.getDeletedAt() != null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setData(new LinkedHashMap<>(data == null ? Map.of() : data));
        notificationRepository.save(notification);
    }

    @Transactional("mainTransactionManager")
    public void notifyUserByEmail(String email, String type, String title, Map<String, Object> data) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .ifPresent(user -> notifyUser(user, type, title, data));
    }

    @Transactional("mainTransactionManager")
    public void notifyAdmins(String type, String title, Map<String, Object> data) {
        adminRecipients().forEach(user -> notifyUser(user, type, title, data));
    }

    private Set<User> adminRecipients() {
        List<String> emails = jdbcTemplate.query("""
                select distinct u.email
                from users u
                join model_has_roles mr on mr.user_id = u.id
                join roles r on r.id = mr.role_id
                left join role_has_permissions rp on rp.role_id = r.id
                left join permissions p on p.id = rp.permission_id
                where u.deleted_at is null
                  and lower(coalesce(u.email, '')) <> ''
                  and (
                    r.name = 'super_admin'
                    or p.name in ('messages.view', 'messages.manage', 'orders.view', 'orders.manage', 'ratings.view', 'ratings.manage', 'users.view', 'users.manage')
                  )
                order by u.email
                """, (rs, rowNum) -> rs.getString(1));
        Set<User> users = new LinkedHashSet<>();
        for (String email : emails) {
            userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresent(users::add);
        }
        return users;
    }
}
