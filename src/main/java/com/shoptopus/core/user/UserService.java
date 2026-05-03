package com.shoptopus.core.user;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.payment.PaymentSource;
import com.shoptopus.core.payment.repository.PaymentSourceRepository;
import com.shoptopus.core.user.dto.*;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PaymentSourceRepository paymentSourceRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserRepository userRepository, AddressRepository addressRepository, PaymentSourceRepository paymentSourceRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.paymentSourceRepository = paymentSourceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AddressResponse> getAddresses(String email) {
        return getAddresses(email, null);
    }

    public List<AddressResponse> getAddresses(String email, String filterId) {
        var user = requireUser(email);
        var addresses = addressRepository.findByUserIdAndDeletedAtIsNull(user.getId());
        Set<UUID> filterIds = parseFilterIds(filterId);
        return addresses.stream()
                .filter(a -> filterIds.isEmpty() || filterIds.contains(a.getId()))
                .map(this::toAddress)
                .toList();
    }

    @Transactional("mainTransactionManager")
    public AddressResponse createAddress(String email, AddressRequest request) {
        var user = requireUser(email);
        var address = new Address();
        applyAddress(address, request);
        address.setUser(user);
        address.setSlug(UUID.randomUUID().toString());
        addressRepository.save(address);
        return toAddress(address);
    }

    public AddressResponse getAddress(String email, UUID addressId) {
        var address = requireOwnedAddress(email, addressId);
        return toAddress(address);
    }

    @Transactional("mainTransactionManager")
    public AddressResponse updateAddress(String email, UUID addressId, AddressRequest request) {
        var address = requireOwnedAddress(email, addressId);
        applyAddress(address, request);
        addressRepository.save(address);
        return toAddress(address);
    }

    @Transactional("mainTransactionManager")
    public void deleteAddress(String email, UUID addressId) {
        var address = requireOwnedAddress(email, addressId);
        address.setDeletedAt(OffsetDateTime.now());
        addressRepository.save(address);
    }

    public AccountResponse getAccount(String email) {
        return toAccount(requireUser(email));
    }

    @Transactional("mainTransactionManager")
    public AccountResponse updateAccount(String email, AccountUpdateRequest request) {
        var user = requireUser(email);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setName(request.firstName() + " " + request.lastName());
        userRepository.save(user);
        return toAccount(user);
    }

    @Transactional("mainTransactionManager")
    public void deleteAccount(String email) {
        deleteAccount(email, false);
    }

    @Transactional("mainTransactionManager")
    public void deleteAccount(String email, boolean anonimize) {
        var user = requireUser(email);
        if (anonimize) {
            user.setEmail(UUID.randomUUID() + "@deactivated.com");
        } else {
            user.setEmail("DEACTIVATED-" + UUID.randomUUID() + "@deactivated.com");
        }
        if (user.getName() != null && !user.getName().isBlank()) {
            user.setName("Deactivated User");
        }
        user.setDeletedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    public List<UUID> favorites(String email) {
        var user = requireUser(email);
        return jdbcTemplate.queryForList("select product_id from favorited_products where user_id = ?", UUID.class, user.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> toggleFavorite(String email, UUID productId) {
        var user = requireUser(email);
        Integer productExists = jdbcTemplate.queryForObject(
                "select count(*) from products where id = ? and deleted_at is null",
                Integer.class,
                productId
        );
        if (productExists == null || productExists == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Product not found");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from favorited_products where user_id = ? and product_id = ?",
                Integer.class,
                user.getId(),
                productId
        );
        boolean favorited;
        if (exists != null && exists > 0) {
            jdbcTemplate.update("delete from favorited_products where user_id = ? and product_id = ?", user.getId(), productId);
            favorited = false;
        } else {
            jdbcTemplate.update("insert into favorited_products(user_id, product_id) values (?, ?)", user.getId(), productId);
            favorited = true;
        }
        syncFavoriteCount(productId);
        return Map.of(
                "product_id", productId,
                "favorited", favorited
        );
    }

    private void syncFavoriteCount(UUID productId) {
        jdbcTemplate.update("""
                update products
                set favourites = (
                    select count(*)::bigint
                    from favorited_products
                    where product_id = ?
                )
                where id = ?
                """, productId, productId);
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
    }

    public List<PaymentSourceResponse> getPaymentSources(String email) {
        var user = requireUser(email);
        return paymentSourceRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
                .map(ps -> new PaymentSourceResponse(ps.getId(), ps.getName(), ps.getBrand(), ps.getLastFour(), false, java.util.Map.of(
                        "source_id", ps.getSourceId(),
                        "exp_month", ps.getExpMonth(),
                        "exp_year", ps.getExpYear(),
                        "stripe_user_id", ps.getStripeUserId(),
                        "payment_method_id", ps.getPaymentMethodId()
                )))
                .toList();
    }

    @Transactional("mainTransactionManager")
    public PaymentSourceResponse upsertPaymentSource(String email, UUID id, java.util.Map<String, Object> body) {
        var user = requireUser(email);
        var source = paymentSourceRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId()).orElseGet(() -> {
            var p = new PaymentSource();
            p.setId(id);
            p.setUser(user);
            return p;
        });
        source.setName(str(body, "provider", source.getName() == null ? "card" : source.getName()));
        source.setSlug(source.getSlug() == null ? "payment-source-" + UUID.randomUUID() : source.getSlug());
        source.setSourceId(str(body, "source_id", source.getSourceId() == null ? id.toString() : source.getSourceId()));
        source.setExpMonth(str(body, "exp_month", source.getExpMonth() == null ? "01" : source.getExpMonth()));
        source.setExpYear(str(body, "exp_year", source.getExpYear() == null ? "2030" : source.getExpYear()));
        source.setLastFour(str(body, "last4", source.getLastFour() == null ? "4242" : source.getLastFour()));
        source.setBrand(str(body, "brand", source.getBrand() == null ? "visa" : source.getBrand()));
        source.setStripeUserId(str(body, "stripe_user_id", source.getStripeUserId() == null ? "" : source.getStripeUserId()));
        source.setPaymentMethodId(parseInt(str(body, "payment_method_id", source.getPaymentMethodId() == null ? "1" : String.valueOf(source.getPaymentMethodId())), 1));
        paymentSourceRepository.save(source);
        return new PaymentSourceResponse(source.getId(), source.getName(), source.getBrand(), source.getLastFour(), false, java.util.Map.of(
                "source_id", source.getSourceId(),
                "exp_month", source.getExpMonth(),
                "exp_year", source.getExpYear(),
                "stripe_user_id", source.getStripeUserId(),
                "payment_method_id", source.getPaymentMethodId()
        ));
    }

    @Transactional("mainTransactionManager")
    public void deletePaymentSource(String email, UUID id) {
        var user = requireUser(email);
        var source = paymentSourceRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment source not found", "Payment source not found"));
        source.setDeletedAt(OffsetDateTime.now());
        paymentSourceRepository.save(source);
    }

    public java.util.Map<String, Object> dashboard(String email) {
        var user = requireUser(email);
        int addresses = addressRepository.findByUserIdAndDeletedAtIsNull(user.getId()).size();
        int favorites = favorites(email).size();
        int paymentSources = paymentSourceRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId()).size();
        return java.util.Map.of(
                "user_id", user.getId(),
                "addresses", addresses,
                "favorites", favorites,
                "payment_sources", paymentSources
        );
    }

    private void applyAddress(Address address, AddressRequest request) {
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setNote(request.note());
        address.setTown(request.town());
        address.setPostCode(request.postCode());
        address.setCountry(request.country() == null || request.country().isBlank() ? "UK" : request.country());
        address.setName(request.name());
        address.setLat(request.lat());
        address.setLon(request.lon());
    }

    private Address requireOwnedAddress(String email, UUID addressId) {
        var user = requireUser(email);
        var address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Address not found", "Address not found"));
        if (address.isDeleted()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Address not found", "Address not found");
        }
        if (!address.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This action is unauthorized.");
        }
        return address;
    }

    private Set<UUID> parseFilterIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(s -> {
                    try {
                        ids.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                });
        return ids;
    }

    private AddressResponse toAddress(Address a) {
        return new AddressResponse(a.getId(), a.getAddressLine1(), a.getAddressLine2(), a.getNote(), a.getTown(), a.getPostCode(), a.getCountry(), a.getName(), a.getLat(), a.getLon());
    }

    private AccountResponse toAccount(User user) {
        return new AccountResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(), user.getName());
    }

    private String str(java.util.Map<String, Object> body, String key, String fallback) {
        Object val = body.get(key);
        return val == null ? fallback : String.valueOf(val);
    }

    private boolean bool(java.util.Map<String, Object> body, String key, boolean fallback) {
        Object val = body.get(key);
        if (val == null) {
            return fallback;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(val)) || "1".equals(String.valueOf(val));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
