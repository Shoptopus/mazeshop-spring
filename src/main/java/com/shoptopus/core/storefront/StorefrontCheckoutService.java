package com.shoptopus.core.storefront;

import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.delivery.DeliveryRule;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.delivery.repository.DeliveryTypeRepository;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.invoice.Invoice;
import com.shoptopus.core.invoice.InvoiceAccessService;
import com.shoptopus.core.invoice.InvoiceSnapshotService;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.mail.ShopMailService;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.PaymentProviderConfig;
import com.shoptopus.core.payment.stripe.StripePaymentGateway;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import com.shoptopus.core.voucher.VoucherCode;
import com.shoptopus.core.voucher.repository.VoucherCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class StorefrontCheckoutService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final DeliveryTypeRepository deliveryTypeRepository;
    private final PaymentProviderRepository paymentProviderRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceSnapshotService invoiceSnapshotService;
    private final InvoiceAccessService invoiceAccessService;
    private final VoucherCodeRepository voucherCodeRepository;
    private final StorefrontCartService storefrontCartService;
    private final StorefrontPricingService storefrontPricingService;
    private final StorefrontStoreResolver storefrontStoreResolver;
    private final ShopMailService shopMailService;
    private final EngagementService engagementService;
    private final StripePaymentGateway stripePaymentGateway;

    public StorefrontCheckoutService(
            CartRepository cartRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            DeliveryTypeRepository deliveryTypeRepository,
            PaymentProviderRepository paymentProviderRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            InvoiceSnapshotService invoiceSnapshotService,
            InvoiceAccessService invoiceAccessService,
            VoucherCodeRepository voucherCodeRepository,
            StorefrontCartService storefrontCartService,
            StorefrontPricingService storefrontPricingService,
            StorefrontStoreResolver storefrontStoreResolver,
            ShopMailService shopMailService,
            EngagementService engagementService,
            StripePaymentGateway stripePaymentGateway
    ) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.deliveryTypeRepository = deliveryTypeRepository;
        this.paymentProviderRepository = paymentProviderRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceSnapshotService = invoiceSnapshotService;
        this.invoiceAccessService = invoiceAccessService;
        this.voucherCodeRepository = voucherCodeRepository;
        this.storefrontCartService = storefrontCartService;
        this.storefrontPricingService = storefrontPricingService;
        this.storefrontStoreResolver = storefrontStoreResolver;
        this.shopMailService = shopMailService;
        this.engagementService = engagementService;
        this.stripePaymentGateway = stripePaymentGateway;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID cartId, String storeSlug, Locale locale, String authenticatedEmail) {
        var cartRow = storefrontCartService.cart(cartId, locale, authenticatedEmail);
        User user = resolveUser(authenticatedEmail).orElse(null);
        Store store = requireCheckoutStore(storeSlug);
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cart", cartRow);
        data.put("cart_group", resolvedStore.multiStoreEnabled() ? findCartGroup(cartRow, store) : wholeCartGroup(cartRow));
        data.put("selected_store", resolvedStore.multiStoreEnabled() ? storefrontStoreResolver.storeRow(store) : null);
        data.put("addresses", user == null ? List.of() : addressRepository.findByUserIdAndDeletedAtIsNull(user.getId()).stream()
                .map(this::addressRow)
                .toList());
        data.put("payment_providers", paymentProviderRepository.findAll().stream()
                .filter(provider -> provider.isEnabled())
                .map(this::paymentProviderRow)
                .toList());
        return data;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deliveryOptions(Map<String, Object> body, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body == null ? null : body.get("cart_id"));
        Store store = requireCheckoutStore(str(body == null ? null : body.get("store_slug")));
        if (cartId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart id is required.");
        }
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        storefrontCartService.cart(cartId, locale, authenticatedEmail);
        Address address = resolveAddressForDelivery(resolveUser(authenticatedEmail).orElse(null), body);
        return deliveryOptions(storefrontCartService.itemsForStore(cart, store), store, address, locale);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> paymentIntent(Map<String, Object> body, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body == null ? null : body.get("cart_id"));
        UUID deliveryTypeId = uuid(body == null ? null : body.get("delivery_type_id"));
        Store store = requireCheckoutStore(str(body == null ? null : body.get("store_slug")));
        if (cartId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart id is required.");
        }
        if (deliveryTypeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Delivery type id is required.");
        }
        CheckoutContext context = checkoutContext(body, locale, authenticatedEmail, cartId, store, deliveryTypeId, false);
        CheckoutPricing pricing = pricing(context.cartItems(), context.deliveryCost(), context.voucherCode());
        String currency = context.currencyCode() == null || context.currencyCode().isBlank() ? "GBP" : context.currencyCode();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("cart_id", cartId.toString());
        metadata.put("store_id", store.getId().toString());
        metadata.put("store_slug", store.getSlug());
        metadata.put("delivery_type_id", context.deliveryType().getId().toString());
        if (context.address().getId() != null) {
            metadata.put("address_id", context.address().getId().toString());
        }
        if (context.user() != null && context.user().getEmail() != null) {
            metadata.put("email", context.user().getEmail());
        }
        if (context.voucherCode() != null) {
            metadata.put("voucher_code", context.voucherCode().getCode());
        }

        Map<String, Object> row = new LinkedHashMap<>(stripePaymentGateway.createPaymentIntent(pricing.total(), currency, metadata));
        row.put("amount", pricing.total());
        row.put("currency", currency.toLowerCase(Locale.ROOT));
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateVoucher(Map<String, Object> body, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body == null ? null : body.get("cart_id"));
        Store store = requireCheckoutStore(str(body == null ? null : body.get("store_slug")));
        if (cartId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart id is required.");
        }
        String code = str(body == null ? null : body.get("voucher_code"));
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code is required.");
        }
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        storefrontCartService.cart(cartId, locale, authenticatedEmail);
        VoucherCode voucherCode = requireVoucherCode(code, store);
        CheckoutPricing pricing = pricing(storefrontCartService.itemsForStore(cart, store), BigDecimal.ZERO, voucherCode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("valid", true);
        row.put("code", voucherCode.getCode());
        row.put("name", voucherCode.getName());
        row.put("type", voucherCode.getType());
        row.put("amount", voucherCode.getAmount());
        row.put("discount_amount", pricing.voucherDiscount());
        row.put("discount", money(pricing.voucherDiscount()));
        row.put("pricing", pricingRow(pricing));
        return row;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> submit(Map<String, Object> body, Locale locale, String authenticatedEmail) {
        UUID cartId = uuid(body == null ? null : body.get("cart_id"));
        UUID deliveryTypeId = uuid(body == null ? null : body.get("delivery_type_id"));
        Store store = requireCheckoutStore(str(body == null ? null : body.get("store_slug")));
        String paymentProvider = str(body == null ? null : body.get("payment_provider"));
        Map<String, Object> paymentDetails = map(body == null ? null : body.get("payment"));
        if (cartId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cart id is required.");
        }
        if (deliveryTypeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Delivery type id is required.");
        }
        if (paymentProvider == null || paymentProvider.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Payment provider is required.");
        }
        String paymentIntentId = str(body == null ? null : body.get("payment_intent_id"));
        boolean stripePayment = "stripe".equalsIgnoreCase(paymentProvider);
        if (!stripePayment && paymentDetails.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Payment details are required.");
        }

        CheckoutContext context = checkoutContext(body, locale, authenticatedEmail, cartId, store, deliveryTypeId, true);
        Cart cart = context.cart();
        User user = context.user();
        Address address = context.address();
        DeliveryType deliveryType = context.deliveryType();
        BigDecimal deliveryCost = context.deliveryCost();
        CheckoutPricing pricing = pricing(context.cartItems(), deliveryCost, context.voucherCode());

        StripePaymentGateway.StripePaymentIntentSnapshot stripeIntent = null;
        if (stripePayment) {
            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Stripe payment_intent_id is required.");
            }
            stripeIntent = stripePaymentGateway.paymentIntent(paymentIntentId);
            if (!"succeeded".equalsIgnoreCase(stripeIntent.status())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Stripe payment has not succeeded.");
            }
            if (stripeIntent.amount().compareTo(pricing.total()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Stripe payment amount does not match checkout total.");
            }
        }

        Order order = new Order();
        order.setStore(store);
        order.setUser(user);
        order.setAddress(address);
        order.setDeliveryTypeId(deliveryType.getId());
        order.setSlug("order-" + UUID.randomUUID());
        order.setStatus(1);

        for (CartItem cartItem : context.cartItems()) {
            var itemPricing = storefrontPricingService.pricing(cartItem.getProduct());
            BigDecimal originalUnit = cartItem.getProductVariant() != null && cartItem.getProductVariant().getPrice() != null
                    ? cartItem.getProductVariant().getPrice()
                    : itemPricing.original();
            BigDecimal finalUnit = itemPricing.discounted()
                    ? applyDiscountToVariantPrice(itemPricing, originalUnit)
                    : originalUnit;
            BigDecimal fullPrice = originalUnit.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            BigDecimal finalPrice = finalUnit.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            BigDecimal unitDiscount = originalUnit.subtract(finalUnit);
            BigDecimal lineDiscount = fullPrice.subtract(finalPrice);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(cartItem.getProduct());
            item.setProductVariant(cartItem.getProductVariant());
            item.setAmount(Math.toIntExact(cartItem.getQuantity()));
            item.setName(TranslatedText.resolve(cartItem.getProduct().getName(), locale));
            item.setOriginalUnitPrice(originalUnit);
            item.setUnitPrice(finalUnit);
            item.setFullPrice(fullPrice);
            item.setFinalPrice(finalPrice);
            item.setUnitDiscount(unitDiscount);
            item.setTotalDiscount(lineDiscount);
            item.setSlug("order-item-" + UUID.randomUUID());
            item.setCustomizationValue(cartItem.getCustomizationValue());
            order.getItems().add(item);
        }

        order.setVoucherCodeId(context.voucherCode() == null ? null : context.voucherCode().getId());
        order.setOriginalPrice(pricing.originalSubtotal());
        order.setSubtotal(pricing.itemsSubtotal());
        order.setDeliveryCost(deliveryCost);
        order.setTotalDiscount(pricing.totalSaved());
        order.setTotalPrice(pricing.total());
        order.setCurrencyCode(context.currencyCode());
        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setPayableType("App\\Models\\Order");
        payment.setPayableId(order.getId().toString());
        payment.setStore(store);
        payment.setAmount(order.getTotalPrice());
        payment.setSlug("payment-" + UUID.randomUUID());
        payment.setUser(user);
        payment.setPaymentSource(null);
        payment.setStatus(stripePayment ? 1 : 0);
        payment.setPaymentRef(stripeIntent == null ? "pay-" + UUID.randomUUID() : stripeIntent.id());
        payment.setType(0);
        payment.setDescription("Checkout payment for " + order.getSlug());
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("provider", paymentProvider);
        if (stripeIntent != null) {
            Map<String, Object> stripeProof = new LinkedHashMap<>();
            stripeProof.put("payment_intent_id", stripeIntent.id());
            stripeProof.put("payment_method_id", stripeIntent.paymentMethodId());
            stripeProof.put("charge_id", stripeIntent.chargeId());
            stripeProof.put("status", stripeIntent.status());
            stripeProof.put("amount", stripeIntent.amount());
            stripeProof.put("currency", stripeIntent.currency());
            proof.put("stripe", stripeProof);
            payment.setMethodRef(stripeIntent.paymentMethodId());
        }
        proof.putAll(paymentDetails);
        payment.setProof(proof);
        paymentRepository.save(payment);

        Map<String, Object> stripeSnapshot = new LinkedHashMap<>();
        if (stripeIntent != null) {
            stripeSnapshot.put("payment_intent_id", stripeIntent.id());
            stripeSnapshot.put("payment_method_id", stripeIntent.paymentMethodId());
            stripeSnapshot.put("charge_id", stripeIntent.chargeId());
            stripeSnapshot.put("status", stripeIntent.status());
        }
        Invoice invoice = invoiceSnapshotService.buildInvoice(
                order,
                address,
                payment,
                paymentProvider,
                paymentDetails,
                checkoutPricingView(pricing),
                context.voucherCode(),
                deliveryType,
                deliveryCost,
                "invoice-" + UUID.randomUUID(),
                stripeSnapshot
        );
        invoiceRepository.save(invoice);
        var invoiceAccessToken = invoiceAccessService.issueInvoiceToken(invoice, user);
        shopMailService.sendNewOrderAdminEmails(order);
        engagementService.notifyAdmins(
                "new_order",
                "New order placed",
                Map.of(
                        "message", "A new order was placed: " + order.getSlug() + ".",
                        "order_id", order.getId(),
                        "order_slug", order.getSlug(),
                        "total_price", order.getTotalPrice()
                )
        );

        clearCheckedOutCart(cart, store);
        if (stripePayment) {
            shopMailService.sendCustomerReceiptEmail(order, invoiceAccessToken.getToken());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("order_id", order.getId());
        response.put("payment_id", payment.getId());
        response.put("status", stripePayment ? "confirmed" : "pending");
        response.put("confirmation_url", "/api/storefront/orders/" + order.getId() + "/confirmation");
        return response;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> confirm(Map<String, Object> body) {
        UUID orderId = uuid(body == null ? null : body.get("order_id"));
        UUID paymentId = uuid(body == null ? null : body.get("payment_id"));
        String paymentRef = str(body == null ? null : body.get("payment_ref"));
        if (orderId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Order id is required.");
        }

        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found"));
        Payment payment = paymentId == null
                ? paymentRepository.findByPayableTypeAndPayableIdAndDeletedAtIsNull("App\\Models\\Order", orderId.toString()).stream().findFirst().orElse(null)
                : paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found", "Payment not found");
        }

            payment.setStatus(1);
        if (paymentRef != null && !paymentRef.isBlank()) {
            payment.setPaymentRef(paymentRef);
        }
        paymentRepository.save(payment);

        order.setStatus(1);
        orderRepository.save(order);

        invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(orderId).ifPresent(invoice -> {
            invoice.getPayment().put("status", payment.getStatus());
            invoice.setValid(true);
            invoiceRepository.save(invoice);
        });

        invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(orderId)
                .flatMap(invoice -> invoiceAccessService.findLatestActiveTokenForInvoice(invoice.getId()))
                .ifPresent(token -> shopMailService.sendCustomerReceiptEmail(order, token.getToken()));

        return Map.of(
                "order_id", order.getId(),
                "payment_id", payment.getId(),
                "status", "confirmed",
                "confirmation_url", "/api/storefront/orders/" + order.getId() + "/confirmation"
        );
    }

    private Store requireCheckoutStore(String storeSlug) {
        StorefrontStoreResolver.ResolvedStore resolvedStore = storefrontStoreResolver.resolve(storeSlug);
        if (resolvedStore.multiStoreEnabled() && (resolvedStore.superstore() || resolvedStore.store() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Checkout must target an active store.");
        }
        if (resolvedStore.store() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Store not found for checkout.");
        }
        return resolvedStore.store();
    }

    private Map<String, Object> findCartGroup(Map<String, Object> cartRow, Store store) {
        return listOfMaps(cartRow.get("groups")).stream()
                .filter(group -> {
                    Map<String, Object> storeRow = map(group.get("store"));
                    return Objects.equals(String.valueOf(store.getId()), String.valueOf(storeRow.get("id")));
                })
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "This cart does not contain items for the selected store."));
    }

    private Map<String, Object> wholeCartGroup(Map<String, Object> cartRow) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("store", null);
        row.put("items", listOfMaps(cartRow.get("items")));
        row.put("item_count", listOfMaps(cartRow.get("items")).stream()
                .map(item -> item.get("quantity"))
                .mapToLong(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    try {
                        return value == null ? 0L : Long.parseLong(String.valueOf(value));
                    } catch (NumberFormatException ignored) {
                        return 0L;
                    }
                })
                .sum());
        row.put("totals", map(cartRow.get("totals")));
        return row;
    }

    private Map<String, Object> paymentProviderRow(com.shoptopus.core.payment.PaymentProvider provider) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("provider", provider.getName());
        row.put("enabled", true);
        row.put("test_mode", provider.isTestMode());
        Map<String, Object> settings = new LinkedHashMap<>();
        for (PaymentProviderConfig config : provider.getConfigs()) {
            if (!config.isPublicValue()) {
                continue;
            }
            String value = provider.isTestMode() && config.getTestValue() != null && !config.getTestValue().isBlank()
                    ? config.getTestValue()
                    : config.getValue();
            if (value != null && !value.isBlank()) {
                settings.put(config.getSetting(), value);
            }
        }
        if ("stripe".equalsIgnoreCase(provider.getName())) {
            try {
                settings.putAll(stripePaymentGateway.publicConfig());
            } catch (ApiException ignored) {
                settings.put("publishable_key", "");
            }
        }
        row.put("settings", settings);
        return row;
    }

    private CheckoutContext checkoutContext(
            Map<String, Object> body,
            Locale locale,
            String authenticatedEmail,
            UUID cartId,
            Store store,
            UUID deliveryTypeId,
            boolean persistGuestData
    ) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
        List<CartItem> cartItems = storefrontCartService.itemsForStore(cart, store);
        if (cartItems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Cannot checkout an empty cart.");
        }

        User user = resolveUser(authenticatedEmail).orElseGet(() -> resolveGuestCheckoutUser(body, persistGuestData));
        Address address = resolveCheckoutAddress(user, body, persistGuestData);
        VoucherCode voucherCode = resolveVoucherCode(body, store);
        DeliveryType deliveryType = deliveryTypeRepository.findByIdAndDeletedAtIsNull(deliveryTypeId)
                .filter(candidate -> Objects.equals(candidate.getStore() == null ? null : candidate.getStore().getId(), store.getId()))
                .filter(DeliveryType::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Delivery type not found."));
        Map<String, Object> selectedDelivery = deliveryOptions(cartItems, store, address, locale).stream()
                .filter(option -> deliveryTypeId.equals(uuid(option.get("id"))))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Delivery type is not available for this address."));

        BigDecimal deliveryCost = decimal(selectedDelivery.get("price_amount"));
        String currency = "GBP";

        CheckoutPricing pricing = pricing(cartItems, deliveryCost, voucherCode);
        return new CheckoutContext(cart, cartItems, store, user, address, deliveryType, deliveryCost, pricing.total(), currency, voucherCode);
    }

    private Map<String, Object> deliveryOption(DeliveryType type, DeliveryRule rule, Locale locale) {
        BigDecimal price = type.getPrice() == null ? BigDecimal.ZERO : type.getPrice();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", type.getId());
        row.put("slug", type.getSlug());
        row.put("name", TranslatedText.resolve(type.getName(), locale));
        row.put("description", TranslatedText.resolve(type.getDescription(), locale));
        row.put("price_amount", price);
        row.put("price", money(price));
        row.put("rule_id", rule.getId());
        return row;
    }

    private List<Map<String, Object>> deliveryOptions(List<CartItem> cartItems, Store store, Address address, Locale locale) {
        BigDecimal subtotal = cartSubtotal(cartItems);
        int totalWeight = cartWeight(cartItems);

        return deliveryTypesFor(store).stream()
                .filter(DeliveryType::isEnabled)
                .flatMap(type -> type.getDeliveryRules().stream()
                        .filter(rule -> rule.getDeletedAt() == null)
                        .filter(DeliveryRule::isEnabled)
                        .filter(rule -> Objects.equals(rule.getStore() == null ? null : rule.getStore().getId(), store.getId()))
                        .filter(rule -> matches(rule, address, subtotal, totalWeight))
                        .sorted(Comparator.comparing(DeliveryRule::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(DeliveryRule::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .findFirst()
                        .stream()
                        .map(rule -> deliveryOption(type, rule, locale)))
                .toList();
    }

    private boolean matches(DeliveryRule rule, Address address, BigDecimal subtotal, int totalWeight) {
        if (rule.getCountry() != null && !rule.getCountry().isBlank() && !rule.getCountry().equalsIgnoreCase(address.getCountry())) {
            return false;
        }
        if (rule.getMinCartPrice() != null && subtotal.compareTo(rule.getMinCartPrice()) < 0) {
            return false;
        }
        if (rule.getMaxCartPrice() != null && subtotal.compareTo(rule.getMaxCartPrice()) > 0) {
            return false;
        }
        if (rule.getMinWeight() != null && totalWeight < rule.getMinWeight()) {
            return false;
        }
        if (rule.getMaxWeight() != null && totalWeight > rule.getMaxWeight()) {
            return false;
        }
        if (rule.getPostcodes() != null && !rule.getPostcodes().isEmpty()) {
            String postcode = address.getPostCode() == null ? "" : address.getPostCode().trim().toLowerCase(Locale.ROOT);
            boolean match = rule.getPostcodes().stream()
                    .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .anyMatch(postcode::startsWith);
            if (!match) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal cartSubtotal(List<CartItem> cartItems) {
        return cartItems.stream()
                .map(item -> {
                    BigDecimal unit = item.getProductVariant() != null && item.getProductVariant().getPrice() != null
                            ? item.getProductVariant().getPrice()
                            : item.getProduct().getPrice();
                    return unit.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int cartWeight(List<CartItem> cartItems) {
        return cartItems.stream()
                .mapToInt(item -> {
                    Integer weight = item.getProduct().getWeight();
                    if (weight == null) {
                        return 0;
                    }
                    return Math.toIntExact(weight.longValue() * item.getQuantity());
                })
                .sum();
    }

    private Map<String, Object> addressRow(Address address) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", address.getId());
        row.put("name", address.getName());
        row.put("address_line_1", address.getAddressLine1());
        row.put("address_line_2", address.getAddressLine2());
        row.put("note", address.getNote());
        row.put("town", address.getTown());
        row.put("post_code", address.getPostCode());
        row.put("country", address.getCountry());
        row.put("lat", address.getLat());
        row.put("lon", address.getLon());
        return row;
    }

    private Address requireAddress(UUID addressId, String authenticatedEmail) {
        if (addressId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Address id is required.");
        }
        User user = resolveUser(authenticatedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required"));
        return addressRepository.findById(addressId)
                .filter(address -> address.getDeletedAt() == null)
                .filter(address -> address.getUser() != null && Objects.equals(address.getUser().getId(), user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Address not found", "Address not found"));
    }

    private Address resolveAddressForDelivery(User user, Map<String, Object> body) {
        UUID addressId = uuid(body == null ? null : body.get("address_id"));
        if (addressId != null) {
            if (user == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Guest checkout requires an inline address.");
            }
            return requireAddress(addressId, user.getEmail());
        }
        return buildAddress(user, map(body == null ? null : body.get("address")), false);
    }

    private Optional<User> resolveUser(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    private VoucherCode resolveVoucherCode(Map<String, Object> body, Store store) {
        String voucherCode = str(body == null ? null : body.get("voucher_code"));
        if (voucherCode == null || voucherCode.isBlank()) {
            return null;
        }
        return requireVoucherCode(voucherCode, store);
    }

    private VoucherCode requireVoucherCode(String code, Store store) {
        VoucherCode voucherCode = (store == null || store.getId() == null
                ? voucherCodeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(code.trim())
                : voucherCodeRepository.findByCodeIgnoreCaseAndStoreIdAndDeletedAtIsNull(code.trim(), store.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code is invalid."));
        if (!voucherCode.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code is disabled.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (voucherCode.getValidFrom() != null && voucherCode.getValidFrom().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code is not active yet.");
        }
        if (voucherCode.getValidUntil() != null && voucherCode.getValidUntil().isBefore(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Voucher code has expired.");
        }
        return voucherCode;
    }

    private List<DeliveryType> deliveryTypesFor(Store store) {
        if (store == null || store.getId() == null) {
            return deliveryTypeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return deliveryTypeRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(store.getId());
    }

    private CheckoutPricing pricing(List<CartItem> cartItems, BigDecimal deliveryCost, VoucherCode voucherCode) {
        BigDecimal originalSubtotal = BigDecimal.ZERO;
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountTotal = BigDecimal.ZERO;
        long itemCount = 0L;
        for (CartItem cartItem : cartItems) {
            var pricing = storefrontPricingService.pricing(cartItem.getProduct());
            BigDecimal originalUnit = cartItem.getProductVariant() != null && cartItem.getProductVariant().getPrice() != null
                    ? cartItem.getProductVariant().getPrice()
                    : pricing.original();
            BigDecimal finalUnit = pricing.discounted()
                    ? applyDiscountToVariantPrice(pricing, originalUnit)
                    : originalUnit;
            BigDecimal originalLine = originalUnit.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            BigDecimal finalLine = finalUnit.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            originalSubtotal = originalSubtotal.add(originalLine);
            itemsSubtotal = itemsSubtotal.add(finalLine);
            itemDiscountTotal = itemDiscountTotal.add(originalLine.subtract(finalLine));
            itemCount += cartItem.getQuantity();
        }
        BigDecimal voucherDiscount = voucherDiscount(voucherCode, itemsSubtotal);
        BigDecimal totalSaved = itemDiscountTotal.add(voucherDiscount);
        BigDecimal total = itemsSubtotal.add(deliveryCost == null ? BigDecimal.ZERO : deliveryCost).subtract(voucherDiscount).max(BigDecimal.ZERO);
        return new CheckoutPricing(originalSubtotal, itemsSubtotal, itemDiscountTotal, voucherDiscount, deliveryCost == null ? BigDecimal.ZERO : deliveryCost, totalSaved, total, itemCount);
    }

    private BigDecimal voucherDiscount(VoucherCode voucherCode, BigDecimal itemsSubtotal) {
        if (voucherCode == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotal = itemsSubtotal == null ? BigDecimal.ZERO : itemsSubtotal;
        BigDecimal discount = switch (voucherCode.getType() == null ? 0 : voucherCode.getType()) {
            case 1 -> subtotal.multiply(voucherCode.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case 2 -> voucherCode.getAmount();
            default -> BigDecimal.ZERO;
        };
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return discount.min(subtotal);
    }

    private Map<String, Object> pricingRow(CheckoutPricing pricing) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("currency", "GBP");
        row.put("item_count", pricing.itemCount());
        row.put("original_amount", pricing.originalSubtotal());
        row.put("items_amount", pricing.itemsSubtotal());
        row.put("item_discount_amount", pricing.itemDiscountTotal());
        row.put("voucher_discount_amount", pricing.voucherDiscount());
        row.put("discount_amount", pricing.totalSaved());
        row.put("delivery_amount", pricing.deliveryAmount());
        row.put("total_amount", pricing.total());
        row.put("original", money(pricing.originalSubtotal()));
        row.put("items", money(pricing.itemsSubtotal()));
        row.put("item_discount", money(pricing.itemDiscountTotal()));
        row.put("voucher_discount", money(pricing.voucherDiscount()));
        row.put("discount", money(pricing.totalSaved()));
        row.put("delivery", money(pricing.deliveryAmount()));
        row.put("total", money(pricing.total()));
        return row;
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private User resolveGuestCheckoutUser(Map<String, Object> body, boolean persist) {
        Map<String, Object> contact = map(body == null ? null : body.get("contact"));
        String email = str(contact.get("email"));
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Contact email is required for guest checkout.");
        }
        Optional<User> existing = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        User created = new User();
        created.setEmail(email.toLowerCase(Locale.ROOT));
        created.setFirstName(defaultString(str(contact.get("first_name")), "Guest"));
        created.setLastName(defaultString(str(contact.get("last_name")), "Customer"));
        created.setName(created.getFirstName() + " " + created.getLastName());
        created.setTemporary(true);
        created.setEmailVerifiedAt(OffsetDateTime.now());
        created.setSlug("user-" + UUID.randomUUID());
        return persist ? userRepository.save(created) : created;
    }

    private Address resolveCheckoutAddress(User user, Map<String, Object> body, boolean persist) {
        UUID addressId = uuid(body == null ? null : body.get("address_id"));
        if (addressId != null && user != null) {
            return requireAddress(addressId, user.getEmail());
        }
        return buildAddress(user, map(body == null ? null : body.get("address")), persist);
    }

    private Address buildAddress(User user, Map<String, Object> raw, boolean persist) {
        if (raw.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Address is required.");
        }
        Address address = new Address();
        address.setUser(user);
        address.setSlug("address-" + UUID.randomUUID());
        address.setName(str(raw.get("name")));
        address.setAddressLine1(requiredString(str(raw.get("address_line_1")), "Address line 1 is required."));
        address.setAddressLine2(str(raw.get("address_line_2")));
        address.setNote(str(raw.get("note")));
        address.setTown(requiredString(str(raw.get("town")), "Town is required."));
        address.setPostCode(requiredString(str(raw.get("post_code")), "Post code is required."));
        address.setCountry(defaultString(str(raw.get("country")), "UK"));
        address.setLat(str(raw.get("lat")));
        address.setLon(str(raw.get("lon")));
        return persist ? addressRepository.save(address) : address;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .toList();
    }

    private String str(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private String requiredString(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
        return raw;
    }

    private String defaultString(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private UUID uuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal decimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        return raw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
    }

    private record CheckoutContext(
            Cart cart,
            List<CartItem> cartItems,
            Store store,
            User user,
            Address address,
            DeliveryType deliveryType,
            BigDecimal deliveryCost,
            BigDecimal total,
            String currencyCode,
            VoucherCode voucherCode
    ) {}

    private record CheckoutPricing(
            BigDecimal originalSubtotal,
            BigDecimal itemsSubtotal,
            BigDecimal itemDiscountTotal,
            BigDecimal voucherDiscount,
            BigDecimal deliveryAmount,
            BigDecimal totalSaved,
            BigDecimal total,
            long itemCount
    ) {}

    private BigDecimal applyDiscountToVariantPrice(StorefrontPricingService.Pricing pricing, BigDecimal variantPrice) {
        if (!pricing.discounted()) {
            return variantPrice;
        }
        return switch (pricing.discountType() == null ? 0 : pricing.discountType()) {
            case 1 -> variantPrice.subtract(variantPrice.multiply(pricing.discountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
            case 2 -> variantPrice.subtract(pricing.discountValue()).max(BigDecimal.ZERO);
            default -> variantPrice;
        };
    }

    private void clearCheckedOutCart(Cart cart, Store store) {
        if (cart == null) {
            return;
        }
        Cart managed = cartRepository.findById(cart.getId()).orElse(cart);
        managed.getItems().removeIf(item -> Objects.equals(
                item.getProduct() == null || item.getProduct().getStore() == null ? null : item.getProduct().getStore().getId(),
                store == null ? null : store.getId()
        ));
        cartRepository.save(managed);
    }

    public record CheckoutPricingView(
            BigDecimal originalSubtotal,
            BigDecimal itemsSubtotal,
            BigDecimal itemDiscountTotal,
            BigDecimal voucherDiscount,
            BigDecimal deliveryAmount,
            BigDecimal totalSaved,
            BigDecimal total,
            long itemCount
    ) {}

    private CheckoutPricingView checkoutPricingView(CheckoutPricing pricing) {
        return new CheckoutPricingView(
                pricing.originalSubtotal(),
                pricing.itemsSubtotal(),
                pricing.itemDiscountTotal(),
                pricing.voucherDiscount(),
                pricing.deliveryAmount(),
                pricing.totalSaved(),
                pricing.total(),
                pricing.itemCount()
        );
    }
}
