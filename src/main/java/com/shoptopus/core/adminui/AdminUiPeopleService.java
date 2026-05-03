package com.shoptopus.core.adminui;

import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.admin.AdminCartService;
import com.shoptopus.core.admin.AdminUserService;
import com.shoptopus.core.note.Note;
import com.shoptopus.core.note.repository.NoteRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.repository.StoreRepository;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiPeopleService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final NoteRepository noteRepository;
    private final JdbcTemplate jdbc;
    private final AdminUserService adminUserService;
    private final AdminCartService adminCartService;
    private final AuthService authService;
    private final AdminStoreContext adminStoreContext;
    private final AdminRoleScopeService adminRoleScopeService;
    private final StoreRepository storeRepository;

    public AdminUiPeopleService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            OrderRepository orderRepository,
            CartRepository cartRepository,
            NoteRepository noteRepository,
            JdbcTemplate jdbc,
            AdminUserService adminUserService,
            AdminCartService adminCartService,
            AuthService authService,
            AdminStoreContext adminStoreContext,
            AdminRoleScopeService adminRoleScopeService,
            StoreRepository storeRepository
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.noteRepository = noteRepository;
        this.jdbc = jdbc;
        this.adminUserService = adminUserService;
        this.adminCartService = adminCartService;
        this.authService = authService;
        this.adminStoreContext = adminStoreContext;
        this.adminRoleScopeService = adminRoleScopeService;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customers(String search, Integer page, Integer perPage) {
        return listPeople(search, page, perPage, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> users(String search, String role, Integer page, Integer perPage) {
        return listPeople(search, page, perPage, true, role);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customer(String id) {
        User user = findUser(id);
        return detail(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> user(String id) {
        User user = findUser(id, true);
        return detail(user);
    }

    public Map<String, Object> inviteOptions(String requesterEmail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roles", availableRoles(isSuperAdmin(requesterEmail)));
        payload.put("store", activeStoreRow());
        return payload;
    }

    public Map<String, Object> inviteUser(String requesterEmail, Map<String, Object> body) {
        String email = body.get("email") == null ? null : String.valueOf(body.get("email"));
        String role = body.get("role") == null ? null : String.valueOf(body.get("role"));
        if (!availableRoleNames(isSuperAdmin(requesterEmail)).contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Role is invalid.");
        }
        return authService.inviteSystemUser(requesterEmail, email, role);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> assignStoreMembership(String id, Map<String, Object> body) {
        User user = findUser(id, true);
        UUID storeId = uuid(str(body, "store_id", null)).orElse(null);
        String role = str(body, "role", null);
        if (storeId == null || role == null || role.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Store and role are required.");
        }
        if (!availableRoleNames(false).contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Role is invalid.");
        }
        Store store = storeRepository.findByIdAndDeletedAtIsNull(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Store is invalid."));
        if (!membershipStoreIds().contains(store.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "You cannot assign users to this store.");
        }
        Integer roleId = jdbc.query(
                "select id from roles where name = ? limit 1",
                rs -> rs.next() ? rs.getInt(1) : null,
                role
        );
        if (roleId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Role is invalid.");
        }
        jdbc.update("""
                insert into user_store_roles(role_id, user_id, store_id)
                values (?, ?::uuid, ?::uuid)
                on conflict do nothing
                """, roleId, user.getId().toString(), store.getId().toString());
        if (user.getStore() == null) {
            user.setStore(store);
            userRepository.save(user);
        }
        return user(id);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> removeStoreMembership(String id, Map<String, Object> body) {
        User user = findUser(id, true);
        UUID storeId = uuid(str(body, "store_id", null)).orElse(null);
        String role = str(body, "role", null);
        if (storeId == null || role == null || role.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Store and role are required.");
        }
        if (!membershipStoreIds().contains(storeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "You cannot manage this store membership.");
        }
        Integer roleId = jdbc.query(
                "select id from roles where name = ? limit 1",
                rs -> rs.next() ? rs.getInt(1) : null,
                role
        );
        if (roleId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Role is invalid.");
        }
        jdbc.update("delete from user_store_roles where role_id = ? and user_id = ?::uuid and store_id = ?::uuid", roleId, user.getId().toString(), storeId.toString());
        if (user.getStore() != null && user.getStore().getId().equals(storeId) && !assignedStoreIds(user.getId()).contains(storeId)) {
            UUID nextStoreId = assignedStoreIds(user.getId()).stream().findFirst().orElse(null);
            user.setStore(nextStoreId == null ? null : storeRepository.findByIdAndDeletedAtIsNull(nextStoreId).orElse(null));
            userRepository.save(user);
        }
        return user(id);
    }

    public Map<String, Object> updateUser(String id, Map<String, Object> body) {
        findUser(id, true);
        adminUserService.update(id, body);
        return user(id);
    }

    public Map<String, Object> updateCustomer(String id, Map<String, Object> body) {
        adminUserService.update(id, body);
        return customer(id);
    }

    public Map<String, Object> updateCustomerFavorite(String id, Map<String, Object> body) {
        adminUserService.update(id, body);
        return customer(id);
    }

    public Map<String, Object> deleteUser(String id) {
        findUser(id, true);
        adminUserService.delete(id);
        return Map.of("id", id);
    }

    public Map<String, Object> deleteCustomer(String id) {
        adminUserService.delete(id);
        return Map.of("id", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> carts(String search, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<Cart> filtered = cartRepository.findAll().stream()
                .filter(cart -> matchesCartSearch(cart, search))
                .sorted(Comparator.comparing(Cart::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search == null ? "" : search);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::cartRow).toList(),
                "meta", meta,
                "filters", filters
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cart(String id) {
        Cart cart = findCart(id);

        Map<String, Object> row = new LinkedHashMap<>(cartRow(cart));
        row.put("created_at", format(cart.getCreatedAt()));
        row.put("updated_at", format(cart.getUpdatedAt()));
        row.put("items", cart.getItems().stream().map(this::cartItemRow).toList());
        row.put("form_options", Map.of(
                "users", userRepository.findAll().stream()
                        .filter(user -> user.getDeletedAt() == null)
                        .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                        .limit(100)
                        .map(user -> Map.<String, Object>of(
                                "id", user.getId(),
                                "name", user.getName(),
                                "email", user.getEmail()
                        ))
                        .toList()
        ));
        return row;
    }

    public Map<String, Object> createCart(Map<String, Object> body) {
        Map<String, Object> created = adminCartService.create(body);
        return cart(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateCart(String id, Map<String, Object> body) {
        adminCartService.update(id, body);
        return cart(id);
    }

    public Map<String, Object> deleteCart(String id) {
        adminCartService.delete(id);
        return Map.of("id", id);
    }

    private Map<String, Object> listPeople(String search, Integer page, Integer perPage, boolean includeRoles) {
        return listPeople(search, page, perPage, includeRoles, null);
    }

    private Map<String, Object> listPeople(String search, Integer page, Integer perPage, boolean includeRoles, String role) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<User> filtered = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !includeRoles || matchesActiveStore(user))
                .filter(user -> matchesSearch(user, search))
                .filter(user -> role == null || role.isBlank() || "all".equalsIgnoreCase(role) || userRoles(user.getId()).contains(role))
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search == null ? "" : search);
        if (includeRoles) {
            filters.put("role", role == null ? "all" : role);
            filters.put("roles", availableRoles(true));
        }

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(user -> listItem(user, includeRoles)).toList(),
                "meta", meta,
                "filters", filters
        );
    }

    private User findUser(String id) {
        return findUser(id, false);
    }

    private User findUser(String id, boolean restrictToActiveStore) {
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found", "User not found");
        }
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found", "User not found"));
        if (restrictToActiveStore && adminStoreContext.multiStoreEnabled() && !matchesActiveStore(user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found", "User not found");
        }
        return user;
    }

    private Map<String, Object> listItem(User user, boolean includeRoles) {
        List<String> roles = userRoles(user.getId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("name", user.getName());
        row.put("email", user.getEmail());
        row.put("first_name", user.getFirstName());
        row.put("last_name", user.getLastName());
        row.put("phone", user.getPhone());
        row.put("temporary", user.isTemporary());
        row.put("favorite", user.isFavorite());
        row.put("store", user.getStore() == null ? null : Map.of(
                "id", user.getStore().getId(),
                "name", user.getStore().getName(),
                "slug", user.getStore().getSlug()
        ));
        row.put("stores", assignedStores(user));
        row.put("store_memberships", storeMemberships(user));
        row.put("created_at", format(user.getCreatedAt()));
        row.put("last_seen", format(user.getLastSeen()));
        row.put("orders_count", orderRepository.findByUserIdAndDeletedAtIsNull(user.getId()).size());
        row.put("roles", includeRoles ? roles : List.of());
        row.put("cart", cartRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).map(Cart::getSlug).orElse(null));
        return row;
    }

    private Map<String, Object> detail(User user) {
        List<Order> orders = orderRepository.findByUserIdAndDeletedAtIsNull(user.getId());
        List<Address> addresses = addressRepository.findByUserIdAndDeletedAtIsNull(user.getId());
        Cart cart = cartRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        List<Note> notes = noteRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(note -> "user".equalsIgnoreCase(note.getNoteableType()) && user.getId().equals(note.getNoteableId()))
                .limit(5)
                .toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("name", user.getName());
        row.put("email", user.getEmail());
        row.put("first_name", user.getFirstName());
        row.put("last_name", user.getLastName());
        row.put("prefix", user.getPrefix());
        row.put("phone", user.getPhone());
        row.put("slug", user.getSlug());
        row.put("avatar", user.getAvatar());
        row.put("store", user.getStore() == null ? null : Map.of(
                "id", user.getStore().getId(),
                "name", user.getStore().getName(),
                "slug", user.getStore().getSlug()
        ));
        row.put("stores", assignedStores(user));
        row.put("store_memberships", storeMemberships(user));
        row.put("temporary", user.isTemporary());
        row.put("favorite", user.isFavorite());
        row.put("email_verified_at", user.getEmailVerifiedAt() == null ? null : format(user.getEmailVerifiedAt()));
        row.put("created_at", format(user.getCreatedAt()));
        row.put("updated_at", format(user.getUpdatedAt()));
        row.put("last_seen", format(user.getLastSeen()));
        row.put("roles", userRoles(user.getId()));
        row.put("metrics", Map.of(
                "orders", orders.size(),
                "addresses", addresses.size(),
                "notes", notes.size(),
                "cart_items", cart == null ? 0 : cart.getItems().size(),
                "favorite_products", favoriteProducts(user.getId()).size()
        ));
        row.put("recent_orders", orders.stream().limit(5).map(order -> Map.of(
                "id", order.getId(),
                "slug", order.getSlug(),
                "status", order.getStatus(),
                "created_at", format(order.getCreatedAt()),
                "total_price", order.getTotalPrice()
        )).toList());
        row.put("addresses", addresses.stream().map(address -> {
            Map<String, Object> addressRow = new LinkedHashMap<>();
            addressRow.put("id", address.getId());
            addressRow.put("address_line_1", address.getAddressLine1());
            addressRow.put("address_line_2", address.getAddressLine2());
            addressRow.put("note", address.getNote());
            addressRow.put("town", address.getTown());
            addressRow.put("post_code", address.getPostCode());
            addressRow.put("country", address.getCountry());
            return addressRow;
        }).toList());
        row.put("cart", cart == null ? null : Map.of(
                "id", cart.getId(),
                "slug", cart.getSlug(),
                "items_count", cart.getItems().size()
        ));
        row.put("notes", notes.stream().map(note -> Map.of(
                "id", note.getId(),
                "text", note.getText(),
                "created_at", format(note.getCreatedAt())
        )).toList());
        row.put("favorite_products", favoriteProducts(user.getId()));
        row.put("form_options", Map.of(
                "stores", membershipStoreOptions(),
                "roles", availableRoles(false)
        ));
        return row;
    }

    private boolean matchesSearch(User user, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase();
        return (user.getEmail() != null && user.getEmail().toLowerCase().contains(term))
                || (user.getName() != null && user.getName().toLowerCase().contains(term))
                || (user.getFirstName() != null && user.getFirstName().toLowerCase().contains(term))
                || (user.getLastName() != null && user.getLastName().toLowerCase().contains(term));
    }

    private boolean matchesCartSearch(Cart cart, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase();
        return (cart.getSlug() != null && cart.getSlug().toLowerCase().contains(term))
                || (cart.getIpAddress() != null && cart.getIpAddress().toLowerCase().contains(term))
                || (cart.getUser() != null && cart.getUser().getEmail() != null && cart.getUser().getEmail().toLowerCase().contains(term))
                || (cart.getUser() != null && cart.getUser().getName() != null && cart.getUser().getName().toLowerCase().contains(term));
    }

    private Cart findCart(String id) {
        UUID cartId;
        try {
            cartId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found");
        }
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cart not found", "Cart not found"));
    }

    private Map<String, Object> cartRow(Cart cart) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", cart.getId());
        row.put("slug", cart.getSlug());
        row.put("ip_address", cart.getIpAddress());
        row.put("items_count", cart.getItems().size());
        row.put("user_id", cart.getUser() == null ? null : cart.getUser().getId());
        row.put("user", cart.getUser() == null ? null : Map.of(
                "id", cart.getUser().getId(),
                "name", cart.getUser().getName(),
                "email", cart.getUser().getEmail()
        ));
        row.put("created_at", format(cart.getCreatedAt()));
        return row;
    }

    private Map<String, Object> cartItemRow(CartItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("quantity", item.getQuantity());
        row.put("product", item.getProduct() == null ? null : Map.of(
                "id", item.getProduct().getId(),
                "slug", item.getProduct().getSlug()
        ));
        row.put("product_variant", item.getProductVariant() == null ? null : Map.of(
                "id", item.getProductVariant().getId(),
                "slug", item.getProductVariant().getSlug(),
                "sku", item.getProductVariant().getSku()
        ));
        return row;
    }

    private List<String> userRoles(UUID userId) {
        UUID activeStoreId = adminStoreContext.currentStoreIdOrNull();
        return adminRoleScopeService.userRoles(userId, activeStoreId);
    }

    private List<Map<String, Object>> availableRoles(boolean includeSuperAdmin) {
        return jdbc.query("""
                select name from roles order by name
                """, (rs, rowNum) -> rs.getString("name")).stream()
                .filter(role -> includeSuperAdmin || !"super_admin".equalsIgnoreCase(role))
                .map(role -> Map.<String, Object>of(
                        "value", role,
                        "label", role
                ))
                .toList();
    }

    private List<String> availableRoleNames(boolean includeSuperAdmin) {
        return jdbc.query("""
                select name from roles order by name
                """, (rs, rowNum) -> rs.getString("name")).stream()
                .filter(role -> includeSuperAdmin || !"super_admin".equalsIgnoreCase(role))
                .filter(role -> !"customer".equalsIgnoreCase(role))
                .toList();
    }

    private List<Map<String, Object>> favoriteProducts(UUID userId) {
        return jdbc.query("""
                select p.id, coalesce(p.name->>'en', '') as name, p.slug
                from favorited_products fp
                join products p on p.id = fp.product_id
                where fp.user_id = ?::uuid
                  and p.deleted_at is null
                order by lower(coalesce(p.name->>'en', '')), p.created_at desc
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("name", rs.getString("name"));
            row.put("slug", rs.getString("slug"));
            return row;
        }, userId.toString());
    }

    private boolean isSuperAdmin(String email) {
        return adminRoleScopeService.globalRoles(email).contains("super_admin");
    }

    private boolean matchesActiveStore(User user) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return false;
        }
        return assignedStoreIds(user.getId()).contains(activeStore.getId());
    }

    private List<Map<String, Object>> assignedStores(User user) {
        return assignedStoreIds(user.getId()).stream()
                .map(storeId -> storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(store -> Map.<String, Object>of(
                        "id", store.getId(),
                        "name", store.getName(),
                        "slug", store.getSlug()
                ))
                .toList();
    }

    private java.util.Set<UUID> assignedStoreIds(UUID userId) {
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(jdbc.query("""
                select distinct usr.store_id
                from user_store_roles usr
                where usr.user_id = ?::uuid
                """, (rs, rowNum) -> UUID.fromString(rs.getString(1)), userId.toString()));
        userRepository.findById(userId)
                .filter(candidate -> candidate.getDeletedAt() == null && candidate.getStore() != null)
                .map(User::getStore)
                .map(Store::getId)
                .ifPresent(ids::add);
        return ids;
    }

    private List<Map<String, Object>> storeMemberships(User user) {
        return jdbc.query("""
                select s.id, s.name, s.slug, r.name as role_name
                from user_store_roles usr
                join roles r on r.id = usr.role_id
                join stores s on s.id = usr.store_id
                where usr.user_id = ?::uuid
                order by s.name, r.name
                """, (rs, rowNum) -> Map.<String, Object>of(
                "store", Map.of(
                        "id", UUID.fromString(rs.getString("id")),
                        "name", rs.getString("name"),
                        "slug", rs.getString("slug")
                ),
                "role", rs.getString("role_name")
        ), user.getId().toString());
    }

    private Map<String, Object> activeStoreRow() {
        Store store = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (store == null) {
            return null;
        }
        return Map.of(
                "id", store.getId(),
                "name", store.getName(),
                "slug", store.getSlug()
        );
    }

    private List<Map<String, Object>> membershipStoreOptions() {
        return membershipStoreIds().stream()
                .map(storeId -> storeRepository.findByIdAndDeletedAtIsNull(storeId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(store -> Map.<String, Object>of(
                        "value", store.getId(),
                        "label", store.getName(),
                        "slug", store.getSlug()
                ))
                .toList();
    }

    private java.util.Set<UUID> membershipStoreIds() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return java.util.Set.of();
        }
        String requesterEmail = currentRequesterEmail();
        if (requesterEmail == null) {
            return java.util.Set.of();
        }
        if (adminRoleScopeService.hasGlobalStoreManagementAccess(requesterEmail)) {
            return new java.util.LinkedHashSet<>(storeRepository.findByDeletedAtIsNullOrderByNameAsc().stream().map(Store::getId).toList());
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore == null ? java.util.Set.of() : java.util.Set.of(activeStore.getId());
    }

    private String currentRequesterEmail() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String email = authentication.getName();
        return email == null || email.isBlank() || "anonymousUser".equals(email) ? null : email;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private java.util.Optional<UUID> uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }
}
