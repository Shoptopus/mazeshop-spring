package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.Rating;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.engagement.repository.RatingRepository;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.files.repository.FileContentRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminBulkMutationService {

    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final BannerRepository bannerRepository;
    private final FileContentRepository fileContentRepository;
    private final ManagedFileService managedFileService;
    private final JdbcTemplate jdbc;

    public AdminBulkMutationService(
            PaymentRepository paymentRepository,
            RatingRepository ratingRepository,
            BannerRepository bannerRepository,
            FileContentRepository fileContentRepository,
            ManagedFileService managedFileService,
            JdbcTemplate jdbc
    ) {
        this.paymentRepository = paymentRepository;
        this.ratingRepository = ratingRepository;
        this.bannerRepository = bannerRepository;
        this.fileContentRepository = fileContentRepository;
        this.managedFileService = managedFileService;
        this.jdbc = jdbc;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> paymentsStatus(Map<String, Object> body) {
        List<Payment> payments = requirePayments(body);
        if (body == null || !body.containsKey("status")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The status field is required.");
        }
        int status = readInteger(body.get("status"), "The status field is invalid.");
        payments.forEach(payment -> payment.setStatus(status));
        paymentRepository.saveAll(payments);
        return result(payments.stream().map(Payment::getId).toList());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> ratingsAvailability(Map<String, Object> body) {
        List<Rating> ratings = requireRatings(body);
        Object availabilityValue = readAvailabilityValue(body);
        if (availabilityValue == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The availability field is required.");
        }
        boolean availability = readBoolean(availabilityValue);
        ratings.forEach(rating -> rating.setEnabled(availability));
        ratingRepository.saveAll(ratings);
        return result(ratings.stream().map(Rating::getId).toList());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> ratingsVerified(Map<String, Object> body) {
        List<Rating> ratings = requireRatings(body);
        if (body == null || !body.containsKey("verified")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The verified field is required.");
        }
        boolean verified = readBoolean(body.get("verified"));
        ratings.forEach(rating -> rating.setVerified(verified));
        ratingRepository.saveAll(ratings);
        return result(ratings.stream().map(Rating::getId).toList());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bannersAvailability(Map<String, Object> body) {
        List<Banner> banners = requireBanners(body);
        Object availabilityValue = readAvailabilityValue(body);
        if (availabilityValue == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The availability field is required.");
        }
        boolean availability = readBoolean(availabilityValue);
        banners.forEach(banner -> banner.setEnabled(availability));
        bannerRepository.saveAll(banners);
        return result(banners.stream().map(Banner::getId).toList());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bannersDelete(Map<String, Object> body) {
        List<Banner> banners = requireBanners(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        banners.forEach(banner -> banner.setDeletedAt(now));
        bannerRepository.saveAll(banners);
        return result(banners.stream().map(Banner::getId).toList());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> filesDelete(Map<String, Object> body) {
        List<UUID> ids = requireIds(body);
        int index = 0;
        for (UUID id : ids) {
            var file = fileContentRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
            if (file == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + index + " is invalid.");
            }
            managedFileService.deleteFile(file);
            index++;
        }
        return result(ids);
    }

    private List<Payment> requirePayments(Map<String, Object> body) {
        List<UUID> ids = requireIds(body);
        List<Payment> payments = new ArrayList<>();
        int index = 0;
        for (UUID id : ids) {
            final int currentIndex = index;
            payments.add(paymentRepository.findById(id)
                    .filter(payment -> payment.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return payments;
    }

    private List<Rating> requireRatings(Map<String, Object> body) {
        List<UUID> ids = requireIds(body);
        List<Rating> ratings = new ArrayList<>();
        int index = 0;
        for (UUID id : ids) {
            final int currentIndex = index;
            ratings.add(ratingRepository.findById(id)
                    .filter(rating -> rating.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return ratings;
    }

    private List<Banner> requireBanners(Map<String, Object> body) {
        List<UUID> ids = requireIds(body);
        List<Banner> banners = new ArrayList<>();
        int index = 0;
        for (UUID id : ids) {
            final int currentIndex = index;
            banners.add(bannerRepository.findById(id)
                    .filter(banner -> banner.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return banners;
    }

    private List<UUID> requireIds(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        List<UUID> ids = new ArrayList<>();
        int index = 0;
        for (Object rawId : rawIds) {
            try {
                ids.add(UUID.fromString(String.valueOf(rawId)));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + index + " is invalid.");
            }
            index++;
        }
        return ids;
    }

    private int readInteger(Object value, String invalidMessage) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", invalidMessage);
        }
    }

    private Object readAvailabilityValue(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        if (body.containsKey("availability")) {
            return body.get("availability");
        }
        if (body.containsKey("enabled")) {
            return body.get("enabled");
        }
        return null;
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Object> result(List<UUID> ids) {
        return Map.of("ids", ids, "count", ids.size());
    }
}
