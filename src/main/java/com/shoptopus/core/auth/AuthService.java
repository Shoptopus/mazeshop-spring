package com.shoptopus.core.auth;

import com.shoptopus.core.adminui.AdminRoleScopeService;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.auth.dto.AuthPayloadDto;
import com.shoptopus.core.auth.dto.AuthUserDto;
import com.shoptopus.core.auth.dto.LoginRequest;
import com.shoptopus.core.auth.dto.RegisterRequest;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.auth.security.JwtService;
import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.mail.AdminInviteMailService;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.UserService;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private static final int SIGNUP_REQUEST_TOKEN_TYPE = 3;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessTokenRepository accessTokenRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EngagementService engagementService;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;
    private final AdminInviteMailService adminInviteMailService;
    private final AdminStoreContext adminStoreContext;
    private final StoreRepository storeRepository;
    private final AdminRoleScopeService adminRoleScopeService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AccessTokenRepository accessTokenRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            EngagementService engagementService,
            UserService userService,
            JdbcTemplate jdbcTemplate,
            AdminInviteMailService adminInviteMailService,
            AdminStoreContext adminStoreContext,
            StoreRepository storeRepository,
            AdminRoleScopeService adminRoleScopeService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenRepository = accessTokenRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.engagementService = engagementService;
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
        this.adminInviteMailService = adminInviteMailService;
        this.adminStoreContext = adminStoreContext;
        this.storeRepository = storeRepository;
        this.adminRoleScopeService = adminRoleScopeService;
    }

    @Transactional("mainTransactionManager")
    public AuthPayloadDto register(RegisterRequest request) {
        if (request.passwordConfirmation() != null && !request.passwordConfirmation().equals(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Password confirmation does not match");
        }
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email already in use", "User exists");
        }

        var user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setName(request.firstName() + " " + request.lastName());
        user.setEmail(request.email().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setSlug(slugify(user.getName()));
        user.setEmailVerifiedAt(OffsetDateTime.now());
        user.setLastSeen(OffsetDateTime.now());
        userRepository.save(user);
        userRepository.flush();
        ensureCart(user);
        engagementService.notifyAdmins(
                "customer_signup",
                "New customer signup",
                Map.of(
                        "message", user.getName() + " created an account.",
                        "user_id", user.getId(),
                        "email", user.getEmail()
                )
        );

        return issueToken(user);
    }

    @Transactional("mainTransactionManager")
    public AuthPayloadDto login(LoginRequest request) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Incorrect email or password. Please try again.",
                        "User not found."));

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please reset your password.", "User has no password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Incorrect email or password. Please try again.",
                    "Hash check fail");
        }

        mergeLoginCart(user, request.cartId());
        user.setLastSeen(OffsetDateTime.now());
        userRepository.save(user);

        return issueToken(user);
    }

    @Transactional("mainTransactionManager")
    public AuthPayloadDto socialLogin(String email, String firstName, String lastName) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email is required.");
        }
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseGet(() -> {
            User created = new User();
            created.setFirstName(defaultName(firstName, "Customer"));
            created.setLastName(defaultName(lastName, "User"));
            created.setName(created.getFirstName() + " " + created.getLastName());
            created.setEmail(email.toLowerCase());
            created.setPassword("");
            created.setSlug(slugify(created.getName()));
            created.setEmailVerifiedAt(OffsetDateTime.now());
            created.setLastSeen(OffsetDateTime.now());
            return userRepository.save(created);
        });

        boolean changed = false;
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName(defaultName(firstName, "Customer"));
            changed = true;
        }
        if (user.getLastName() == null || user.getLastName().isBlank()) {
            user.setLastName(defaultName(lastName, "User"));
            changed = true;
        }
        String fullName = defaultName(user.getFirstName(), "Customer") + " " + defaultName(user.getLastName(), "User");
        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(fullName);
            changed = true;
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(OffsetDateTime.now());
            changed = true;
        }
        user.setLastSeen(OffsetDateTime.now());
        if (user.getSlug() == null || user.getSlug().isBlank()) {
            user.setSlug(slugify(fullName));
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
        ensureCart(user);
        return issueToken(user);
    }

    @Transactional("mainTransactionManager")
    public void logout(String token) {
        accessTokenRepository.deleteByToken(token);
    }

    public AuthUserDto me(String email) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found", "User not found"));
        return toAuthUser(user);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> resendVerification(String email, String authenticatedEmail) {
        if ((email == null || email.isBlank()) && authenticatedEmail != null && !authenticatedEmail.isBlank()) {
            return Map.of("message", "Verification email re-sent.");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email is required.");
        }
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email does not exist."));
        if (user.getEmailVerifiedAt() != null) {
            return Map.of("message", "Email has been verified.");
        }
        return Map.of("message", "Verification email re-sent.");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> verifyEmail(String id, String hash) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return Map.of("url", "/verify?message=Invalid/Expired url provided.&status=401");
        }
        var user = userRepository.findById(userId).orElse(null);
        if (user == null || hash == null || hash.isBlank()) {
            return Map.of("url", "/verify?message=Invalid/Expired url provided.&status=401");
        }
        if (!sha1(user.getEmail()).equalsIgnoreCase(hash)) {
            return Map.of("url", "/verify?message=Invalid/Expired url provided.&status=401");
        }
        user.setEmailVerifiedAt(OffsetDateTime.now());
        userRepository.save(user);
        return Map.of("url", "/verify?message=Email verified.&status=200");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email is required.");
        }
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
        if (user != null) {
            jdbcTemplate.update("delete from password_reset_tokens where lower(email) = lower(?)", user.getEmail());
            jdbcTemplate.update(
                    "insert into password_reset_tokens(email, token, created_at) values (?, ?, now())",
                    user.getEmail(),
                    UUID.randomUUID().toString()
            );
        }
        return Map.of("message", "If the email is in our system, we have e-mailed your password reset link!");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> inviteSystemUser(String requesterEmail, String invitedEmail, String role) {
        if (invitedEmail == null || invitedEmail.isBlank() || role == null || role.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email and role are required.");
        }

        var requester = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(requesterEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        Store inviteStore = adminStoreContext.activeStoreOptional(requesterEmail).orElse(requester.getStore());

        if (!hasRole(requesterEmail, "super_admin", inviteStore) && !hasPermission(requesterEmail, "users.can.invite", inviteStore)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "This action is unauthorized.");
        }

        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(invitedEmail).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email already in use.");
        }

        Integer roleCount = jdbcTemplate.queryForObject(
                "select count(*) from roles where name = ?",
                Integer.class,
                role
        );
        if (roleCount == null || roleCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Role is invalid.");
        }

        jdbcTemplate.update(
                "delete from access_tokens where type = ? and lower(content->>'email') = lower(?)",
                SIGNUP_REQUEST_TOKEN_TYPE,
                invitedEmail
        );

        var accessToken = new AccessToken();
        accessToken.setType(SIGNUP_REQUEST_TOKEN_TYPE);
        accessToken.setToken(UUID.randomUUID().toString());
        accessToken.setAccessableType("User");
        accessToken.setAccessableId("NEW USER");
        accessToken.setUser(requester);
        accessToken.setIssuerUser(requester);
        accessToken.setExpiry(OffsetDateTime.now().plusDays(1));
        var inviteContent = new java.util.LinkedHashMap<String, Object>();
        inviteContent.put("email", invitedEmail.toLowerCase());
        inviteContent.put("role", role);
        if (inviteStore != null) {
            inviteContent.put("store_id", inviteStore.getId().toString());
        }
        accessToken.setContent(inviteContent);
        accessTokenRepository.save(accessToken);

        adminInviteMailService.sendInvite(invitedEmail, role, accessToken.getToken());

        var response = new java.util.LinkedHashMap<String, Object>();
        response.put("email", invitedEmail);
        response.put("role", role);
        response.put("store", inviteStore == null ? null : Map.of(
                "id", inviteStore.getId(),
                "name", inviteStore.getName(),
                "slug", inviteStore.getSlug()
        ));
        response.put("invite_url", adminInviteMailService.inviteUrl(accessToken.getToken()));
        return response;
    }

    @Transactional(transactionManager = "mainTransactionManager", noRollbackFor = ApiException.class)
    public Map<String, Object> resetPassword(String email, String token, String password, String passwordConfirmation) {
        if (email == null || email.isBlank() || token == null || token.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email, token and password are required.");
        }
        if (passwordConfirmation != null && !passwordConfirmation.equals(password)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Password confirmation does not match.");
        }

        Integer tokenCount = jdbcTemplate.queryForObject(
                "select count(*) from password_reset_tokens where lower(email)=lower(?) and token=?",
                Integer.class,
                email,
                token
        );
        if (tokenCount == null || tokenCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid token", "This password reset token is invalid.");
        }

        Boolean expired = jdbcTemplate.queryForObject(
                "select created_at < (now() - interval '60 minutes') from password_reset_tokens where lower(email)=lower(?) and token=?",
                Boolean.class,
                email,
                token
        );

        if (Boolean.TRUE.equals(expired)) {
            jdbcTemplate.update("delete from password_reset_tokens where lower(email)=lower(?) and token=?", email, token);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token expired", "This password reset token has expired.");
        }

        int updated = jdbcTemplate.update(
                "update users set password = ?, updated_at = now() where lower(email) = lower(?) and deleted_at is null",
                passwordEncoder.encode(password),
                email
        );
        if (updated == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid token", "This password reset token is invalid.");
        }
        jdbcTemplate.update("delete from password_reset_tokens where lower(email)=lower(?) and token=?", email, token);

        return Map.of("message", "Password successfully updated!");
    }

    public AuthPayloadDto registerByInvite(String token, String firstName, String lastName, String password, String passwordConfirmation) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "First name, last name and password are required.");
        }
        if (passwordConfirmation != null && !passwordConfirmation.equals(password)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Password confirmation does not match.");
        }

        var accessToken = accessTokenRepository.findByToken(token).orElse(null);
        if (accessToken == null || accessToken.getType() != SIGNUP_REQUEST_TOKEN_TYPE || accessToken.getExpiry() == null || accessToken.getExpiry().isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid token", "Invalid or expired token.");
        }

        String invitedEmail = null;
        String role = null;
        String invitedStoreId = null;
        if (accessToken.getContent() != null) {
            Object emailVal = accessToken.getContent().get("email");
            Object roleVal = accessToken.getContent().get("role");
            Object storeVal = accessToken.getContent().get("store_id");
            invitedEmail = emailVal == null ? null : String.valueOf(emailVal);
            role = roleVal == null ? null : String.valueOf(roleVal);
            invitedStoreId = storeVal == null ? null : String.valueOf(storeVal);
        }
        if (invitedEmail == null || invitedEmail.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid token", "Invalid or expired token.");
        }
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(invitedEmail).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email already in use.");
        }

        var user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName(firstName + " " + lastName);
        user.setEmail(invitedEmail.toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setSlug(slugify(user.getName()));
        user.setEmailVerifiedAt(OffsetDateTime.now());
        user.setLastSeen(OffsetDateTime.now());
        if (invitedStoreId != null && !invitedStoreId.isBlank()) {
            try {
                UUID storeId = UUID.fromString(invitedStoreId);
                user.setStore(storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null));
            } catch (IllegalArgumentException ignored) {
                user.setStore(null);
            }
        }
        userRepository.save(user);
        ensureCart(user);

        if (role != null && !role.isBlank()) {
            Integer roleId = jdbcTemplate.query(
                    "select id from roles where name = ? limit 1",
                    rs -> rs.next() ? rs.getInt(1) : null,
                    role
            );
            if (roleId != null) {
                if (invitedStoreId != null && !invitedStoreId.isBlank()) {
                    jdbcTemplate.update("""
                                    insert into user_store_roles(role_id, user_id, store_id)
                                    select ?, id, ?::uuid
                                    from users
                                    where lower(email)=lower(?)
                                    on conflict do nothing
                                    """,
                            roleId,
                            invitedStoreId,
                            user.getEmail()
                    );
                } else {
                    jdbcTemplate.update(
                            "insert into model_has_roles(role_id, user_id) " +
                                    "select ?, id from users where lower(email)=lower(?) on conflict do nothing",
                            roleId,
                            user.getEmail()
                    );
                }
            }
        }

        accessTokenRepository.delete(accessToken);
        return issueToken(user);
    }

    private AuthPayloadDto issueToken(User user) {
        var token = jwtService.generateToken(user.getId(), user.getEmail());
        AuthUserDto authUser = toAuthUser(user);
        var accessToken = new AccessToken();
        accessToken.setToken(token);
        accessToken.setUser(user);
        accessToken.setAccessableId(user.getId().toString());
        accessTokenRepository.save(accessToken);

        return new AuthPayloadDto(token, "Bearer", authUser);
    }

    private AuthUserDto toAuthUser(User user) {
        return new AuthUserDto(
                user.getId(),
                user.getName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                immutableMap(user.getAvatar()),
                user.getEmailVerifiedAt() != null,
                cartPayload(user),
                unreadNotifications(user),
                favoriteProductIds(user)
        );
    }

    private Map<String, Object> cartPayload(User user) {
        Cart cart = ensureCart(user);
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("id", cart.getId());
        payload.put("user_id", user.getId());
        payload.put("ip_address", cart.getIpAddress());
        payload.put("user", user.getId());
        payload.put("products", jdbcTemplate.query(
                """
                        select product_id, product_variant_id, customization_value, quantity
                        from cart_product
                        where cart_id = ?
                        order by created_at asc
                        """,
                (rs, rowNum) -> {
                    var row = new java.util.LinkedHashMap<String, Object>();
                    row.put("product_id", rs.getObject("product_id", UUID.class));
                    row.put("product_variant_id", rs.getObject("product_variant_id", UUID.class));
                    row.put("customization_value", rs.getString("customization_value"));
                    row.put("quantity", rs.getLong("quantity"));
                    return row;
                },
                cart.getId()
        ));
        return payload;
    }

    private void mergeLoginCart(User user, UUID loginCartId) {
        Cart userCart = ensureCart(user);
        if (loginCartId == null || userCart.getId().equals(loginCartId)) {
            return;
        }
        Cart sourceCart = cartRepository.findById(loginCartId).orElse(null);
        if (sourceCart == null || sourceCart.getUser() != null) {
            return;
        }

        for (CartItem sourceItem : cartItemRepository.findByCartId(sourceCart.getId())) {
            UUID sourceProductId = sourceItem.getProduct().getId();
            UUID sourceVariantId = sourceItem.getProductVariant() == null ? null : sourceItem.getProductVariant().getId();

            var existing = cartItemRepository.findLineItem(userCart.getId(), sourceProductId, sourceVariantId, sourceItem.getCustomizationValue()).orElse(null);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + sourceItem.getQuantity());
                cartItemRepository.save(existing);
                continue;
            }

            CartItem newItem = new CartItem();
            newItem.setCart(userCart);
            newItem.setProduct(sourceItem.getProduct());
            newItem.setProductVariant(sourceItem.getProductVariant());
            newItem.setCustomizationValue(sourceItem.getCustomizationValue());
            newItem.setQuantity(sourceItem.getQuantity());
            cartItemRepository.save(newItem);
        }

        cartRepository.delete(sourceCart);
    }

    private java.util.List<Map<String, Object>> unreadNotifications(User user) {
        return jdbcTemplate.query(
                """
                        select id, type, title
                        from notifications
                        where user_id = ? and read_at is null
                        order by created_at desc
                        """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getObject("id", UUID.class));
                    row.put("type", rs.getString("type"));
                    row.put("title", rs.getString("title"));
                    row.put("data", Collections.emptyMap());
                    return row;
                },
                user.getId()
        );
    }

    private List<UUID> favoriteProductIds(User user) {
        return List.copyOf(jdbcTemplate.queryForList(
                "select product_id from favorited_products where user_id = ?",
                UUID.class,
                user.getId()
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> immutableMap(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : new LinkedHashMap<>(source).entrySet()) {
            copy.put(String.valueOf(entry.getKey()), immutableValue(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    @SuppressWarnings("unchecked")
    private Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap((Map<String, ?>) map);
        }
        if (value instanceof java.util.Collection<?> collection) {
            List<Object> copy = new ArrayList<>();
            for (Object item : new ArrayList<>(collection)) {
                copy.add(immutableValue(item));
            }
            return List.copyOf(copy);
        }
        if (value instanceof java.util.Set<?> set) {
            return Set.copyOf(new LinkedHashSet<>(set));
        }
        return value;
    }

    private Cart ensureCart(User user) {
        return cartRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setSlug("cart-" + UUID.randomUUID());
            return cartRepository.save(cart);
        });
    }

    private String slugify(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", ex.getMessage());
        }
    }

    private boolean hasRole(String email, String roleName, Store store) {
        UUID storeId = store == null ? null : store.getId();
        return adminRoleScopeService.effectiveRoles(email, storeId).contains(roleName);
    }

    private boolean hasPermission(String email, String permissionName, Store store) {
        UUID storeId = store == null ? null : store.getId();
        return adminRoleScopeService.effectivePermissions(email, storeId).contains(permissionName);
    }
}
