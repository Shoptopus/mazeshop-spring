package com.shoptopus.core.storefront;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.UserService;
import com.shoptopus.core.user.dto.AccountUpdateRequest;
import com.shoptopus.core.user.dto.AddressRequest;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class StorefrontAccountService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final EngagementService engagementService;

    public StorefrontAccountService(
            UserService userService,
            UserRepository userRepository,
            OrderRepository orderRepository,
            EngagementService engagementService
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.engagementService = engagementService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(String email) {
        var account = userService.getAccount(email);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.id());
        row.put("first_name", account.firstName());
        row.put("last_name", account.lastName());
        row.put("name", account.name());
        row.put("email", account.email());
        row.put("phone", account.phone());
        return row;
    }

    public Map<String, Object> updateProfile(String email, Map<String, Object> body) {
        AccountUpdateRequest request = new AccountUpdateRequest(
                str(body, "first_name", str(body, "firstName", null)),
                str(body, "last_name", str(body, "lastName", null)),
                null,
                str(body, "phone", null)
        );
        var account = userService.updateAccount(email, request);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.id());
        row.put("first_name", account.firstName());
        row.put("last_name", account.lastName());
        row.put("name", account.name());
        row.put("email", account.email());
        row.put("phone", account.phone());
        return row;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> addresses(String email) {
        return userService.getAddresses(email).stream().map(address -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", address.id());
            row.put("name", address.name());
            row.put("address_line_1", address.addressLine1());
            row.put("address_line_2", address.addressLine2());
            row.put("note", address.note());
            row.put("town", address.town());
            row.put("post_code", address.postCode());
            row.put("country", address.country());
            row.put("lat", address.lat());
            row.put("lon", address.lon());
            return row;
        }).toList();
    }

    public Map<String, Object> createAddress(String email, Map<String, Object> body) {
        return addressRow(userService.createAddress(email, addressRequest(body)));
    }

    public Map<String, Object> address(String email, UUID addressId) {
        return addressRow(userService.getAddress(email, addressId));
    }

    public Map<String, Object> updateAddress(String email, UUID addressId, Map<String, Object> body) {
        return addressRow(userService.updateAddress(email, addressId, addressRequest(body)));
    }

    public void deleteAddress(String email, UUID addressId) {
        userService.deleteAddress(email, addressId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> orders(String email) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        return orderRepository.findByUserIdAndDeletedAtIsNull(user.getId()).stream()
                .map(this::orderSummaryRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> order(String email, UUID orderId, Locale locale) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .filter(item -> item.getUser() != null && item.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found"));

        Map<String, Object> row = orderSummaryRow(order);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("id", order.getAddress().getId());
        address.put("address_line_1", order.getAddress().getAddressLine1());
        address.put("address_line_2", order.getAddress().getAddressLine2());
        address.put("town", order.getAddress().getTown());
        address.put("post_code", order.getAddress().getPostCode());
        address.put("country", order.getAddress().getCountry());
        row.put("address", address);
        if (order.getDeliveryType() == null) {
            row.put("delivery", null);
        } else {
            Map<String, Object> delivery = new LinkedHashMap<>();
            delivery.put("id", order.getDeliveryType().getId());
            delivery.put("slug", order.getDeliveryType().getSlug());
            delivery.put("name", order.getDeliveryType().getName());
            row.put("delivery", delivery);
        }
        row.put("items", order.getItems().stream().map(item -> orderItemRow(item, email, locale)).toList());
        row.put("order_review", engagementService.userRating(email, "order", order.getId()));
        return row;
    }

    private Map<String, Object> orderSummaryRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("status", order.getStatus());
        row.put("currency", order.getCurrencyCode());
        row.put("original_amount", order.getOriginalPrice());
        row.put("subtotal_amount", order.getSubtotal());
        row.put("discount_amount", order.getTotalDiscount());
        row.put("delivery_amount", order.getDeliveryCost());
        row.put("total_amount", order.getTotalPrice());
        row.put("original", money(order.getOriginalPrice()));
        row.put("subtotal", money(order.getSubtotal()));
        row.put("discount", money(order.getTotalDiscount()));
        row.put("delivery_cost", money(order.getDeliveryCost()));
        row.put("total", money(order.getTotalPrice()));
        row.put("created_at", order.getCreatedAt() == null ? null : order.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("store", storeRow(order.getStore()));
        return row;
    }

    private Map<String, Object> orderItemRow(OrderItem item, String email, Locale locale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("product_id", item.getProduct() == null ? null : item.getProduct().getId());
        row.put("product_variant_id", item.getProductVariant() == null ? null : item.getProductVariant().getId());
        row.put("product_slug", item.getProduct() == null ? null : item.getProduct().getSlug());
        row.put("store", storeRow(item.getProduct() == null ? null : item.getProduct().getStore()));
        row.put("name", item.getName());
        row.put("quantity", item.getAmount());
        row.put("customization_value", item.getCustomizationValue());
        row.put("original_unit_price", item.getOriginalUnitPrice());
        row.put("unit_price", item.getUnitPrice());
        row.put("full_price", item.getFullPrice());
        row.put("final_price", item.getFinalPrice());
        row.put("unit_discount", item.getUnitDiscount());
        row.put("total_discount", item.getTotalDiscount());
        row.put("download_urls", item.getUrls());
        row.put("review", item.getProduct() == null ? null : engagementService.userRating(email, "product", item.getProduct().getId()));
        return row;
    }

    private Map<String, Object> addressRow(com.shoptopus.core.user.dto.AddressResponse address) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", address.id());
        row.put("name", address.name());
        row.put("address_line_1", address.addressLine1());
        row.put("address_line_2", address.addressLine2());
        row.put("note", address.note());
        row.put("town", address.town());
        row.put("post_code", address.postCode());
        row.put("country", address.country());
        row.put("lat", address.lat());
        row.put("lon", address.lon());
        return row;
    }

    private AddressRequest addressRequest(Map<String, Object> body) {
        return new AddressRequest(
                str(body, "address_line_1", str(body, "addressLine1", null)),
                str(body, "address_line_2", str(body, "addressLine2", null)),
                str(body, "note", null),
                str(body, "town", null),
                str(body, "post_code", str(body, "postCode", null)),
                str(body, "country", null),
                str(body, "name", null),
                str(body, "lat", null),
                str(body, "lon", null)
        );
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        Object value = body.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> storeRow(Store store) {
        if (store == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("slug", store.getSlug());
        row.put("tagline", store.getTagline());
        row.put("logo", store.getLogo());
        row.put("rating_average", store.getRatingAverage());
        row.put("rating_count", store.getRatingCount());
        return row;
    }
}
