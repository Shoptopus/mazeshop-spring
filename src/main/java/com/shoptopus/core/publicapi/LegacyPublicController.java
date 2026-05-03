package com.shoptopus.core.publicapi;

import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.auth.dto.RegisterRequest;
import com.shoptopus.core.auth.security.JwtService;
import com.shoptopus.core.cart.CartService;
import com.shoptopus.core.common.api.ApiResponse;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.ordering.OrderService;
import com.shoptopus.core.payment.PaymentService;
import com.shoptopus.core.catalog.CatalogService;
import com.shoptopus.core.catalog.dto.ProductFilterRequest;
import com.shoptopus.core.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class LegacyPublicController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final CatalogService catalogService;
    private final UserService userService;
    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public LegacyPublicController(
            AuthService authService,
            JwtService jwtService,
            CatalogService catalogService,
            UserService userService,
            CartService cartService,
            OrderService orderService,
            PaymentService paymentService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.catalogService = catalogService;
        this.userService = userService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping("/signup")
    public ApiResponse<?> signup(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        String password = val(req, "password", "");
        String confirmation = val(req, "password_confirmation", password);
        var auth = authService.register(new RegisterRequest(
                val(req, "first_name", val(req, "firstName", "")),
                val(req, "last_name", val(req, "lastName", "")),
                val(req, "email", ""),
                password,
                confirmation
        ));
        return ApiResponse.ok("Registered", Map.of("auth", auth));
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        var auth = authService.login(new LoginRequest(val(req, "email", ""), val(req, "password", ""), null));
        return ApiResponse.ok("Logged in", Map.of("auth", auth));
    }

    @PostMapping("/password-recovery")
    public ApiResponse<?> passwordRecovery(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok("Password reset email sent", Map.of("email", body == null ? null : body.get("email")));
    }

    @PostMapping("/password-reset")
    public ApiResponse<?> passwordReset(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok("Password updated", null);
    }

    @GetMapping("/products")
    public ApiResponse<?> products(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filters[name->en]") String filterName,
            @RequestParam(required = false, name = "paginate") Integer paginate,
            @RequestParam(required = false, name = "page") Integer page,
            @RequestParam(required = false, name = "store") String store,
            Locale locale
    ) {
        String resolvedSearch = search;
        if ((resolvedSearch == null || resolvedSearch.isBlank()) && filterName != null && !filterName.isBlank()) {
            resolvedSearch = filterName;
        }
        int size = paginate == null || paginate <= 0 ? 20 : paginate;
        int p = page == null || page <= 0 ? 0 : page - 1;
        var filter = new ProductFilterRequest(resolvedSearch, null, null, null, p, size);
        var rows = catalogService.getProducts(filter, locale, store);
        var total = catalogService.countProducts(filter, locale, store);
        return ApiResponse.paged("Products", rows, rows.size(), total, null);
    }

    @GetMapping("/product/{id}")
    public ApiResponse<?> product(
            @PathVariable UUID id,
            @RequestParam(required = false, name = "store") String store,
            Locale locale
    ) {
        return ApiResponse.ok("Product", catalogService.getProduct(id, locale, store));
    }

    @GetMapping("/account/addresses")
    public ApiResponse<?> addresses(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String email = requireEmail(authorization);
        var rows = userService.getAddresses(email);
        return ApiResponse.paged("Addresses", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/account/address")
    public ApiResponse<?> createAddress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String email = requireEmail(authorization);
        var created = userService.createAddress(email, new com.shoptopus.core.user.dto.AddressRequest(
                read(request, body, "address_line_1", "addressLine1"),
                read(request, body, "address_line_2", "addressLine2"),
                read(request, body, "note"),
                read(request, body, "town"),
                read(request, body, "post_code", "postCode"),
                read(request, body, "country"),
                read(request, body, "name"),
                read(request, body, "lat"),
                read(request, body, "lon")
        ));
        return ApiResponse.ok("Address created", created);
    }

    @PostMapping(value = "/account/address/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<?> updateAddress(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String email = requireEmail(authorization);
        var updated = userService.updateAddress(email, id, new com.shoptopus.core.user.dto.AddressRequest(
                read(request, body, "address_line_1", "addressLine1"),
                read(request, body, "address_line_2", "addressLine2"),
                read(request, body, "note"),
                read(request, body, "town"),
                read(request, body, "post_code", "postCode"),
                read(request, body, "country"),
                read(request, body, "name"),
                read(request, body, "lat"),
                read(request, body, "lon")
        ));
        return ApiResponse.ok("Address updated", updated);
    }

    @PostMapping(value = "/account/address/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ApiResponse<?> updateAddressForm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        return updateAddress(authorization, id, request, null);
    }

    @DeleteMapping("/account/address/{id}")
    public ApiResponse<?> deleteAddress(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        String email = requireEmail(authorization);
        userService.deleteAddress(email, id);
        return ApiResponse.ok("Address deleted", null);
    }

    @GetMapping("/account/orders")
    public ApiResponse<?> orders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String email = requireEmail(authorization);
        var rows = orderService.getAll(email);
        return ApiResponse.paged("Orders", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/account/order/{id}")
    public ApiResponse<?> order(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        String email = requireEmail(authorization);
        return ApiResponse.ok("Order", orderService.get(id, email));
    }

    @GetMapping("/account/payment-sources")
    public ApiResponse<?> paymentSources(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String email = requireEmail(authorization);
        var rows = userService.getPaymentSources(email);
        return ApiResponse.paged("Payment sources", rows, rows.size(), (long) rows.size(), null);
    }

    @PostMapping("/account/payment-source/{id}")
    public ApiResponse<?> upsertPaymentSource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String email = requireEmail(authorization);
        var merged = mergedBody(request, body);
        var source = userService.upsertPaymentSource(email, id, merged);
        return ApiResponse.ok("Payment source saved", source);
    }

    @DeleteMapping("/account/payment-source/{id}")
    public ApiResponse<?> deletePaymentSource(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        String email = requireEmail(authorization);
        userService.deletePaymentSource(email, id);
        return ApiResponse.ok("Payment source deleted", null);
    }

    @GetMapping("/account/cart")
    public ApiResponse<?> accountCart(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request,
            Locale locale
    ) {
        String email = requireEmail(authorization);
        return ApiResponse.ok("Cart", cartService.getOrCreateUserCart(email, request.getRemoteAddr(), locale));
    }

    @PostMapping("/account/cart/product/{id}")
    public ApiResponse<?> accountCartAdd(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID id,
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> body,
            Locale locale
    ) {
        String email = requireEmail(authorization);
        Long quantity = parseLong(read(request, body, "quantity"), 1L);
        try {
            var updated = cartService.updateCartItemById(email, id, quantity, locale);
            return ApiResponse.ok("Cart updated", updated);
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
            var cart = cartService.addItemToUserCart(email, id, quantity, request.getRemoteAddr(), locale);
            return ApiResponse.ok("Cart updated", cart);
        }
    }

    @DeleteMapping("/account/cart/product/{id}")
    public ApiResponse<?> accountCartDelete(@RequestHeader(value = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        String email = requireEmail(authorization);
        cartService.deleteCartItemById(email, id);
        return ApiResponse.ok("Cart updated", null);
    }

    @GetMapping("/account/dashboard")
    public ApiResponse<?> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String email = requireEmail(authorization);
        return ApiResponse.ok("Dashboard", userService.dashboard(email));
    }

    @GetMapping("/account/checkout")
    public ApiResponse<?> checkoutPage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request,
            Locale locale
    ) {
        String email = requireEmail(authorization);
        var cart = cartService.getOrCreateUserCart(email, request.getRemoteAddr(), locale);
        return ApiResponse.ok("Checkout", Map.of(
                "cart", cart,
                "delivery_types", java.util.List.of(
                        Map.of("id", "standard", "name", "Standard", "cost", 4.99),
                        Map.of("id", "express", "name", "Express", "cost", 9.99)
                )
        ));
    }

    @PostMapping("/account/cart/checkout/{orderId}")
    public ApiResponse<?> processPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        requireEmail(authorization);
        String provider = val(body == null ? Map.of() : body, "provider", "stripe");
        String paymentRef = val(body == null ? Map.of() : body, "payment_ref", "order-" + orderId);
        var result = paymentService.execute(provider, paymentRef);
        return ApiResponse.ok("Payment processed", Map.of("order_id", orderId, "payment", result));
    }

    private String requireEmail(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required", 1101);
        }
        String token = authorization.substring("Bearer ".length());
        try {
            var claims = jwtService.parse(token);
            Object emailClaim = claims.get("email");
            if (emailClaim == null) {
                throw new IllegalStateException("Missing email claim");
            }
            return String.valueOf(emailClaim);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Invalid token", 1101);
        }
    }

    private Map<String, Object> mergedBody(HttpServletRequest request, Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (body != null) {
            out.putAll(body);
        }
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                out.putIfAbsent(k, v[0]);
            }
        });
        return out;
    }

    private String read(HttpServletRequest request, Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String requestVal = request.getParameter(key);
            if (requestVal != null && !requestVal.isBlank()) {
                return requestVal;
            }
            if (body != null && body.get(key) != null && !String.valueOf(body.get(key)).isBlank()) {
                return String.valueOf(body.get(key));
            }
        }
        return "";
    }

    private String val(Map<String, Object> body, String key, String fallback) {
        Object v = body.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private Long parseLong(String v, Long fallback) {
        try {
            return v == null || v.isBlank() ? fallback : Long.parseLong(v);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
