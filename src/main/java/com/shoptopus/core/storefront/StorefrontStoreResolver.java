package com.shoptopus.core.storefront;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.store.DefaultStoreResolver;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.store.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class StorefrontStoreResolver {

    private final boolean multiStoreEnabled;
    private final StoreRepository storeRepository;

    public StorefrontStoreResolver(
            @Value("${app.multistore.enabled:false}") boolean multiStoreEnabled,
            StoreRepository storeRepository
    ) {
        this.multiStoreEnabled = multiStoreEnabled;
        this.storeRepository = storeRepository;
    }

    public ResolvedStore resolve(String storeSlug) {
        if (!multiStoreEnabled) {
            return new ResolvedStore(defaultStore().orElse(null), false, false);
        }
        if (storeSlug == null || storeSlug.isBlank()) {
            Store store = defaultActiveStore().orElse(null);
            return new ResolvedStore(store, store == null, true);
        }
        Store store = storeRepository.findBySlugAndDeletedAtIsNull(storeSlug.trim().toLowerCase(Locale.ROOT))
                .filter(candidate -> candidate.getStatus() == StoreStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "Store not found"));
        return new ResolvedStore(store, false, true);
    }

    public List<Store> activeStores() {
        if (!multiStoreEnabled) {
            return defaultStore().map(List::of).orElse(List.of());
        }
        return storeRepository.findByDeletedAtIsNullAndStatusOrderByNameAsc(StoreStatus.ACTIVE);
    }

    public Optional<Store> defaultStore() {
        return storeRepository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc();
    }

    private Optional<Store> defaultActiveStore() {
        return defaultStore()
                .filter(store -> store.getStatus() == StoreStatus.ACTIVE)
                .or(() -> storeRepository.findByDeletedAtIsNullAndStatusOrderByNameAsc(StoreStatus.ACTIVE).stream().findFirst());
    }

    public Map<String, Object> storeRow(Store store) {
        if (store == null) {
            return null;
        }
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", displayName(store));
        row.put("slug", store.getSlug());
        row.put("tagline", store.getTagline() == null ? "" : store.getTagline());
        row.put("logo", store.getLogo());
        row.put("rating_average", store.getRatingAverage());
        row.put("rating_count", store.getRatingCount());
        row.put("status", store.getStatus() == null ? null : store.getStatus().name().toLowerCase(Locale.ROOT));
        return row;
    }

    public String displayName(Store store) {
        if (store == null) {
            return null;
        }
        if (multiStoreEnabled && DefaultStoreResolver.DEFAULT_STORE_ID.equals(store.getId())) {
            return "Marketplace";
        }
        return store.getName();
    }

    public record ResolvedStore(Store store, boolean superstore, boolean multiStoreEnabled) {
    }
}
