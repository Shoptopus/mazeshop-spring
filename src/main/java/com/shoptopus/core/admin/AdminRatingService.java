package com.shoptopus.core.admin;

import com.shoptopus.core.engagement.Rating;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.engagement.repository.RatingRepository;
import com.shoptopus.core.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminRatingService {

    private final RatingRepository ratingRepository;
    private final EngagementService engagementService;

    public AdminRatingService(RatingRepository ratingRepository, EngagementService engagementService) {
        this.ratingRepository = ratingRepository;
        this.engagementService = engagementService;
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> data = ratingRepository.findAll().stream()
                .filter(rating -> rating.getDeletedAt() == null)
                .map(this::toRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "ratings");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    public Map<String, Object> show(String id) {
        UUID ratingId = uuid(id).orElse(null);
        if (ratingId == null) {
            return Map.of("id", id);
        }
        return ratingRepository.findById(ratingId)
                .filter(rating -> rating.getDeletedAt() == null)
                .map(this::toRow)
                .orElseGet(() -> Map.of("id", ratingId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID ratingId = uuid(id).orElse(null);
        if (ratingId == null) {
            return Map.of("id", id);
        }
        ratingRepository.findById(ratingId).filter(rating -> rating.getDeletedAt() == null).ifPresent(rating -> {
            rating.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            ratingRepository.save(rating);
            engagementService.recalculateProductRatingIfApplicable(rating);
        });
        return Map.of("id", ratingId);
    }

    private Map<String, Object> toRow(Rating rating) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rating.getId());
        row.put("rating", rating.getRating());
        row.put("verified", rating.isVerified());
        row.put("enabled", rating.isEnabled());
        row.put("title", rating.getTitle());
        row.put("description", rating.getDescription());
        row.put("ratable_type", rating.getRatableType());
        row.put("ratable_id", rating.getRatableId());
        User user = rating.getUser();
        row.put("user_id", user == null ? null : user.getId());
        if (user == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", user.getId());
            userRow.put("email", user.getEmail());
            userRow.put("first_name", user.getFirstName());
            userRow.put("last_name", user.getLastName());
            row.put("user", userRow);
        }
        return row;
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
