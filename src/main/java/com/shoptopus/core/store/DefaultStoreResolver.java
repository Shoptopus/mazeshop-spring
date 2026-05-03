package com.shoptopus.core.store;

import com.shoptopus.core.store.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultStoreResolver {

    public static final UUID DEFAULT_STORE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final StoreRepository storeRepository;

    public DefaultStoreResolver(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public Store defaultStore() {
        return storeRepository.findByIdAndDeletedAtIsNull(DEFAULT_STORE_ID)
                .or(() -> storeRepository.findFirstByDeletedAtIsNullOrderByCreatedAtAsc())
                .orElse(null);
    }
}
