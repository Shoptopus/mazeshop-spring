package com.shoptopus.core.engagement.repository;

import com.shoptopus.core.engagement.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    List<Rating> findByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue(String ratableType, UUID ratableId);
    List<Rating> findByRatableTypeAndDeletedAtIsNullAndEnabledTrue(String ratableType);
    List<Rating> findByRatableTypeAndRatableIdAndDeletedAtIsNullOrderByCreatedAtDesc(String ratableType, UUID ratableId);
    Optional<Rating> findByUserIdAndRatableTypeAndRatableIdAndDeletedAtIsNull(UUID userId, String ratableType, UUID ratableId);
    long countByRatableTypeAndRatableIdAndDeletedAtIsNullAndEnabledTrue(String ratableType, UUID ratableId);
    List<Rating> findByStoreIdAndDeletedAtIsNullAndEnabledTrue(UUID storeId);

    @Query("""
            select r.ratableId, count(r)
            from Rating r
            where r.ratableType = :ratableType
              and r.ratableId in :ratableIds
              and r.deletedAt is null
              and r.enabled = true
            group by r.ratableId
            """)
    List<Object[]> countByRatableTypeAndRatableIdInAndDeletedAtIsNullAndEnabledTrueGroupByRatableId(String ratableType, Collection<UUID> ratableIds);
}
