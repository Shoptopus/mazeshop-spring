package com.shoptopus.core.admin;

import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.catalog.*;
import com.shoptopus.core.engagement.Banner;
import com.shoptopus.core.engagement.Rating;
import com.shoptopus.core.catalog.repository.*;
import com.shoptopus.core.engagement.repository.BannerRepository;
import com.shoptopus.core.engagement.repository.RatingRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.*;

@Service
public class AdminSupportService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductTagRepository tagRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeOptionRepository attributeOptionRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final RatingRepository ratingRepository;
    private final BannerRepository bannerRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AdminSupportService(
            ProductRepository productRepository,
            ProductCategoryRepository categoryRepository,
            ProductTagRepository tagRepository,
            ProductAttributeRepository attributeRepository,
            ProductAttributeOptionRepository attributeOptionRepository,
            ProductVariantRepository variantRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            CartRepository cartRepository,
            RatingRepository ratingRepository,
            BannerRepository bannerRepository,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.attributeRepository = attributeRepository;
        this.attributeOptionRepository = attributeOptionRepository;
        this.variantRepository = variantRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.ratingRepository = ratingRepository;
        this.bannerRepository = bannerRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> list(String resource) {
        List<?> data = switch (resource) {
            case "products" -> productRepository.findAll().stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .map(p -> row(
                            "id", p.getId(),
                            "name", p.getName(),
                            "slug", p.getSlug(),
                            "price", p.getPrice(),
                            "final_price", p.getPrice(),
                            "status", p.getStatus(),
                            "stock", p.getStock()
                    ))
                    .toList();
            case "product-categories" -> categoryRepository.findAll().stream()
                    .filter(c -> c.getDeletedAt() == null)
                    .map(c -> row(
                            "id", c.getId(),
                            "name", c.getName(),
                            "slug", c.getSlug(),
                            "enabled", c.isEnabled()
                    ))
                    .toList();
            case "product-tags" -> tagRepository.findAll().stream()
                    .filter(t -> t.getDeletedAt() == null)
                    .map(t -> row(
                            "id", t.getId(),
                            "name", t.getName(),
                            "slug", t.getSlug(),
                            "enabled", t.isEnabled()
                    ))
                    .toList();
            case "product-attributes" -> attributeRepository.findAll().stream()
                    .filter(a -> a.getDeletedAt() == null)
                    .map(a -> row(
                            "id", a.getId(),
                            "name", a.getName(),
                            "slug", a.getSlug(),
                            "type", a.getType(),
                            "enabled", a.isEnabled()
                    ))
                    .toList();
            case "orders" -> orderRepository.findByDeletedAtIsNull().stream()
                    .map(o -> row(
                            "id", o.getId(),
                            "slug", o.getSlug(),
                            "status", o.getStatus(),
                            "total_price", o.getTotalPrice()
                    ))
                    .toList();
            case "payments" -> paymentRepository.findByDeletedAtIsNull().stream()
                    .map(p -> row(
                            "id", p.getId(),
                            "slug", p.getSlug(),
                            "amount", p.getAmount(),
                            "status", p.getStatus()
                    ))
                    .toList();
            case "users", "customers" -> userRepository.findAll().stream()
                    .filter(u -> u.getDeletedAt() == null)
                    .map(u -> row(
                            "id", u.getId(),
                            "email", u.getEmail(),
                            "name", u.getName(),
                            "avatar", u.getAvatar(),
                            "first_name", u.getFirstName(),
                            "last_name", u.getLastName()
                    ))
                    .toList();
            case "carts" -> cartRepository.findAll().stream()
                    .map(c -> row(
                            "id", c.getId(),
                            "slug", c.getSlug(),
                            "user_id", c.getUser() == null ? null : c.getUser().getId()
                    ))
                    .toList();
            case "ratings" -> ratingRepository.findAll().stream()
                    .filter(r -> r.getDeletedAt() == null)
                    .map(r -> row(
                            "id", r.getId(),
                            "rating", r.getRating(),
                            "verified", r.isVerified()
                    ))
                    .toList();
            case "banners" -> bannerRepository.findAll().stream()
                    .filter(b -> b.getDeletedAt() == null)
                    .map(b -> row(
                            "id", b.getId(),
                            "name", b.getName(),
                            "slug", b.getSlug(),
                            "enabled", b.isEnabled(),
                            "click_count", b.getClickCount()
                    ))
                    .toList();
            case "voucher-codes" -> jdbc.queryForList("select id, code, name, amount, enabled, valid_from, valid_until, slug from voucher_codes where deleted_at is null order by created_at desc");
            case "delivery-types" -> jdbc.queryForList("select id, name, slug, enabled from delivery_types where deleted_at is null order by created_at desc");
            case "delivery-rules" -> jdbc.queryForList("select id, delivery_type_id, name, slug, country, enabled from delivery_rules where deleted_at is null order by created_at desc");
            case "discount-rules" -> jdbc.queryForList("select id, name, type, amount, enabled, valid_from, valid_until, slug from discount_rules where deleted_at is null order by created_at desc");
            case "files" -> jdbc.queryForList("select id, name, mime_type, size, path, slug from file_contents where deleted_at is null order by created_at desc");
            case "notes" -> jdbc.queryForList("select id, user_id, noteable_type, noteable_id, text, slug from notes where deleted_at is null order by created_at desc");
            default -> List.of();
        };
        return row(
                "message", resource,
                "data", data,
                "records", data.size(),
                "total_records", data.size(),
                "next", null
        );
    }

    public Map<String, Object> show(String resource, String id) {
        if (id != null && uuid(id).isPresent()) {
            UUID uid = uuid(id).orElseThrow();
            List<Map<String, Object>> rows = switch (resource) {
                case "voucher-code" -> jdbc.queryForList("select id, code, name, amount, enabled, valid_from, valid_until, slug from voucher_codes where id = ? and deleted_at is null", uid);
                case "delivery-type" -> jdbc.queryForList("select id, name, slug, description, enabled from delivery_types where id = ? and deleted_at is null", uid);
                case "delivery-rule" -> jdbc.queryForList("select id, delivery_type_id, name, slug, country, min_cart_price, max_cart_price, enabled from delivery_rules where id = ? and deleted_at is null", uid);
                case "discount-rule" -> jdbc.queryForList("select id, name, type, amount, enabled, valid_from, valid_until, slug from discount_rules where id = ? and deleted_at is null", uid);
                case "file" -> jdbc.queryForList("select id, name, mime_type, size, path, slug from file_contents where id = ? and deleted_at is null", uid);
                case "note" -> jdbc.queryForList("select id, user_id, noteable_type, noteable_id, text, slug from notes where id = ? and deleted_at is null", uid);
                default -> List.of();
            };
            if (!rows.isEmpty()) {
                return row("message", resource + " show", "data", rows.get(0));
            }
        }
        return row("message", resource + " show", "data", row("id", id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> mutate(String resource, String action, String id, Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        Object resultId = id;

        switch (resource) {
            case "products" -> {
                if ("bulk-archive".equals(action)) {
                    for (UUID pid : ids(payload)) {
                        productRepository.findById(pid).ifPresent(p -> {
                            p.setStatus(2);
                            productRepository.save(p);
                        });
                    }
                } else if ("bulk-delete".equals(action)) {
                    for (UUID pid : ids(payload)) {
                        productRepository.findById(pid).ifPresent(p -> {
                            p.setDeletedAt(OffsetDateTime.now());
                            productRepository.save(p);
                        });
                    }
                }
            }
            case "product" -> {
                if ("create".equals(action)) {
                    Map<String, String> name = nameMap(payload, "name", null);
                    if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
                    }
                    Product p = new Product();
                    p.setName(name);
                    p.setSlug(str(payload, "slug", "product-" + UUID.randomUUID()));
                    p.setShortDescription(nameMap(payload, "short_description", "Short"));
                    p.setDescription(nameMap(payload, "description", "Description"));
                    p.setPrice(decimal(payload, "price", BigDecimal.ZERO));
                    p.setStock(longVal(payload, "stock", 0L));
                    p.setVirtual(bool(payload, "virtual", false));
                    productRepository.save(p);
                    resultId = p.getId();
                }
                if ("update".equals(action) && id != null) {
                    uuid(id).flatMap(productRepository::findById).ifPresent(p -> {
                        if (payload.containsKey("name")) p.setName(nameMap(payload, "name", "Product"));
                        if (payload.containsKey("short_description")) p.setShortDescription(nameMap(payload, "short_description", "Short"));
                        if (payload.containsKey("description")) p.setDescription(nameMap(payload, "description", "Description"));
                        if (payload.containsKey("price")) p.setPrice(decimal(payload, "price", p.getPrice()));
                        if (payload.containsKey("stock")) p.setStock(longVal(payload, "stock", p.getStock()));
                        if (payload.containsKey("status")) p.setStatus(intVal(payload, "status", p.getStatus()));
                        productRepository.save(p);
                    });
                }
                if ("delete".equals(action) && id != null) {
                    uuid(id).flatMap(productRepository::findById).ifPresent(p -> {
                        p.setDeletedAt(OffsetDateTime.now());
                        productRepository.save(p);
                    });
                }
            }
            case "product-categories" -> {
                if ("bulk-availability".equals(action)) {
                    boolean enabled = bool(payload, "enabled", bool(payload, "status", true));
                    for (UUID cid : ids(payload)) {
                        categoryRepository.findById(cid).ifPresent(c -> {
                            c.setEnabled(enabled);
                            categoryRepository.save(c);
                        });
                    }
                }
                if ("bulk-delete".equals(action)) {
                    for (UUID cid : ids(payload)) {
                        categoryRepository.findById(cid).ifPresent(c -> {
                            c.setDeletedAt(OffsetDateTime.now());
                            categoryRepository.save(c);
                        });
                    }
                }
            }
            case "product-category" -> {
                Object createdId = mutateCategory(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "product-tags" -> {
                if ("bulk-availability".equals(action)) {
                    boolean enabled = bool(payload, "enabled", bool(payload, "status", true));
                    for (UUID tid : ids(payload)) {
                        tagRepository.findById(tid).ifPresent(t -> {
                            t.setEnabled(enabled);
                            tagRepository.save(t);
                        });
                    }
                }
                if ("bulk-delete".equals(action)) {
                    for (UUID tid : ids(payload)) {
                        tagRepository.findById(tid).ifPresent(t -> {
                            t.setDeletedAt(OffsetDateTime.now());
                            tagRepository.save(t);
                        });
                    }
                }
            }
            case "product-tag" -> {
                Object createdId = mutateTag(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "product-attributes" -> {
                if ("bulk-availability".equals(action)) {
                    boolean enabled = bool(payload, "enabled", bool(payload, "status", true));
                    for (UUID aid : ids(payload)) {
                        attributeRepository.findById(aid).ifPresent(a -> {
                            a.setEnabled(enabled);
                            attributeRepository.save(a);
                        });
                    }
                }
                if ("bulk-delete".equals(action)) {
                    for (UUID aid : ids(payload)) {
                        attributeRepository.findById(aid).ifPresent(a -> {
                            a.setDeletedAt(OffsetDateTime.now());
                            attributeRepository.save(a);
                        });
                    }
                }
            }
            case "product-attribute" -> {
                Object createdId = mutateAttribute(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "product-attribute-option" -> {
                Object createdId = mutateAttributeOption(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "variant" -> mutateVariant(action, id, payload);
            case "orders" -> {
                if ("bulk-status".equals(action)) {
                    int status = intVal(payload, "status", 0);
                    for (UUID oid : ids(payload)) {
                        orderRepository.findById(oid).ifPresent(o -> {
                            o.setStatus(status);
                            orderRepository.save(o);
                        });
                    }
                }
            }
            case "order" -> {
                if ("update-status".equals(action) && id != null) {
                    uuid(id).flatMap(orderRepository::findById).ifPresent(o -> {
                        o.setStatus(intVal(payload, "status", o.getStatus()));
                        orderRepository.save(o);
                    });
                }
                if ("delete".equals(action) && id != null) {
                    uuid(id).flatMap(orderRepository::findById).ifPresent(o -> {
                        o.setDeletedAt(OffsetDateTime.now());
                        orderRepository.save(o);
                    });
                }
            }
            case "payments" -> {
                if ("bulk-status".equals(action)) {
                    int status = intVal(payload, "status", 0);
                    for (UUID pid : ids(payload)) {
                        paymentRepository.findById(pid).ifPresent(p -> {
                            p.setStatus(status);
                            paymentRepository.save(p);
                        });
                    }
                }
            }
            case "payment" -> {
                Object createdId = mutatePayment(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "user" -> {
                Object createdId = mutateUser(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "ratings" -> {
                if ("bulk-availability".equals(action)) {
                    boolean enabled = bool(payload, "enabled", true);
                    for (UUID rid : ids(payload)) {
                        ratingRepository.findById(rid).ifPresent(r -> {
                            r.setEnabled(enabled);
                            ratingRepository.save(r);
                        });
                    }
                }
                if ("bulk-verified-status".equals(action)) {
                    boolean verified = bool(payload, "verified", true);
                    for (UUID rid : ids(payload)) {
                        ratingRepository.findById(rid).ifPresent(r -> {
                            r.setVerified(verified);
                            ratingRepository.save(r);
                        });
                    }
                }
            }
            case "rating" -> {
                if ("delete".equals(action) && id != null) {
                    uuid(id).flatMap(ratingRepository::findById).ifPresent(r -> {
                        r.setDeletedAt(OffsetDateTime.now());
                        ratingRepository.save(r);
                    });
                }
            }
            case "banners" -> {
                if ("bulk-availability".equals(action)) {
                    boolean enabled = bool(payload, "enabled", true);
                    for (UUID bid : ids(payload)) {
                        bannerRepository.findById(bid).ifPresent(b -> {
                            b.setEnabled(enabled);
                            bannerRepository.save(b);
                        });
                    }
                }
                if ("bulk-delete".equals(action)) {
                    for (UUID bid : ids(payload)) {
                        bannerRepository.findById(bid).ifPresent(b -> {
                            b.setDeletedAt(OffsetDateTime.now());
                            bannerRepository.save(b);
                        });
                    }
                }
            }
            case "banner" -> {
                Object createdId = mutateBanner(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "voucher-codes" -> mutateVoucherCodes(action, payload);
            case "voucher-code" -> {
                Object createdId = mutateVoucherCode(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "delivery-types" -> mutateDeliveryTypes(action, payload);
            case "delivery-type" -> {
                Object createdId = mutateDeliveryType(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "delivery-rule" -> {
                Object createdId = mutateDeliveryRule(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "discount-rules" -> mutateDiscountRules(action, payload);
            case "discount-rule" -> {
                Object createdId = mutateDiscountRule(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "discount-rule-product" -> mutateDiscountRuleProduct(action, id);
            case "discount-rule-category" -> mutateDiscountRuleCategory(action, id);
            case "file" -> {
                Object createdId = mutateFile(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "files" -> mutateFiles(action, payload);
            case "note" -> {
                Object createdId = mutateNote(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "role" -> {
                Object createdId = mutateRole(action, id, payload);
                if (createdId != null) {
                    resultId = createdId;
                }
            }
            case "role-permission" -> mutateRolePermission(action, id);
            case "role-user" -> mutateRoleUser(action, id);
            default -> {
            }
        }

        return row("id", resultId, "payload", payload);
    }

    public Map<String, Object> dashboard() {
        return Map.of(
                "message", "dashboard",
                "data", Map.of(
                        "products", productRepository.count(),
                        "orders", orderRepository.count(),
                        "users", userRepository.count(),
                        "payments", paymentRepository.count()
                )
        );
    }

    public Map<String, Object> reports(String type) {
        return Map.of("message", "report", "data", Map.of("type", type, "series", List.of()));
    }

    public Map<String, Object> roles() {
        var data = jdbc.queryForList("select id, name from roles order by id");
        return row(
                "message", "roles",
                "data", data,
                "records", data.size(),
                "total_records", data.size(),
                "next", null
        );
    }

    public Map<String, Object> permissions() {
        var data = jdbc.queryForList("select id, name from permissions order by id");
        return row(
                "message", "permissions",
                "data", data,
                "records", data.size(),
                "total_records", data.size(),
                "next", null
        );
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private UUID mutateCategory(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            ProductCategory c = new ProductCategory();
            c.setName(name);
            c.setSlug(str(payload, "slug", "category-" + UUID.randomUUID()));
            c.setDescription(nameMap(payload, "description", "Description"));
            c.setEnabled(bool(payload, "enabled", true));
            categoryRepository.save(c);
            return c.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(categoryRepository::findById).ifPresent(c -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) c.setName(nameMap(payload, "name", "Category"));
                if (payload.containsKey("description")) c.setDescription(nameMap(payload, "description", "Description"));
                if (payload.containsKey("enabled")) c.setEnabled(bool(payload, "enabled", c.isEnabled()));
                categoryRepository.save(c);
            } else if ("delete".equals(action)) {
                c.setDeletedAt(OffsetDateTime.now());
                categoryRepository.save(c);
            }
        });
        return null;
    }

    private UUID mutateTag(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            ProductTag t = new ProductTag();
            t.setName(name);
            t.setSlug(str(payload, "slug", "tag-" + UUID.randomUUID()));
            t.setDescription(nameMap(payload, "description", "Description"));
            t.setEnabled(bool(payload, "enabled", true));
            tagRepository.save(t);
            return t.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(tagRepository::findById).ifPresent(t -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) t.setName(nameMap(payload, "name", "Tag"));
                if (payload.containsKey("description")) t.setDescription(nameMap(payload, "description", "Description"));
                if (payload.containsKey("enabled")) t.setEnabled(bool(payload, "enabled", t.isEnabled()));
                tagRepository.save(t);
            } else if ("delete".equals(action)) {
                t.setDeletedAt(OffsetDateTime.now());
                tagRepository.save(t);
            }
        });
        return null;
    }

    private UUID mutateAttribute(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            ProductAttribute a = new ProductAttribute();
            a.setName(name);
            a.setSlug(str(payload, "slug", "attribute-" + UUID.randomUUID()));
            a.setType(intVal(payload, "type", 0));
            a.setEnabled(bool(payload, "enabled", true));
            attributeRepository.save(a);
            return a.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(attributeRepository::findById).ifPresent(a -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) a.setName(nameMap(payload, "name", "Attribute"));
                if (payload.containsKey("type")) a.setType(intVal(payload, "type", a.getType()));
                if (payload.containsKey("enabled")) a.setEnabled(bool(payload, "enabled", a.isEnabled()));
                attributeRepository.save(a);
            } else if ("delete".equals(action)) {
                a.setDeletedAt(OffsetDateTime.now());
                attributeRepository.save(a);
            }
        });
        return null;
    }

    private UUID mutateAttributeOption(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action) && id != null) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            final UUID[] createdId = new UUID[1];
            uuid(id).flatMap(attributeRepository::findById).ifPresent(attribute -> {
                ProductAttributeOption option = new ProductAttributeOption();
                option.setProductAttribute(attribute);
                option.setName(name);
                option.setSlug(str(payload, "slug", "option-" + UUID.randomUUID()));
                option.setValue(str(payload, "value", "value"));
                option.setEnabled(bool(payload, "enabled", true));
                attributeOptionRepository.save(option);
                createdId[0] = option.getId();
            });
            return createdId[0];
        }
        if (id == null) return null;
        uuid(id).flatMap(attributeOptionRepository::findById).ifPresent(option -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) option.setName(nameMap(payload, "name", "Option"));
                if (payload.containsKey("value")) option.setValue(str(payload, "value", option.getValue()));
                if (payload.containsKey("enabled")) option.setEnabled(bool(payload, "enabled", option.isEnabled()));
                attributeOptionRepository.save(option);
            } else if ("delete".equals(action)) {
                option.setDeletedAt(OffsetDateTime.now());
                attributeOptionRepository.save(option);
            }
        });
        return null;
    }

    private void mutateVariant(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action) && id != null) {
            uuid(id).flatMap(productRepository::findById).ifPresent(product -> {
                ProductVariant variant = new ProductVariant();
                variant.setProduct(product);
                variant.setDescription(nameMap(payload, "description", str(payload, "name", "Variant")));
                variant.setSlug(str(payload, "slug", "variant-" + UUID.randomUUID()));
                variant.setPrice(decimal(payload, "price", BigDecimal.ZERO));
                variant.setStock(longVal(payload, "stock", 0L));
                variant.setEnabled(bool(payload, "enabled", true));
                variantRepository.save(variant);
            });
            return;
        }
        if (id == null) return;
        uuid(id).flatMap(variantRepository::findById).ifPresent(variant -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) variant.setDescription(nameMap(payload, "name", "Variant"));
                if (payload.containsKey("description")) variant.setDescription(nameMap(payload, "description", "Variant"));
                if (payload.containsKey("price")) variant.setPrice(decimal(payload, "price", variant.getPrice()));
                if (payload.containsKey("stock")) variant.setStock(longVal(payload, "stock", variant.getStock()));
                if (payload.containsKey("enabled")) variant.setEnabled(bool(payload, "enabled", variant.isEnabled()));
                variantRepository.save(variant);
            } else if ("delete".equals(action)) {
                variant.setDeletedAt(OffsetDateTime.now());
                variantRepository.save(variant);
            }
        });
    }

    private UUID mutatePayment(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Payment payment = new Payment();
            payment.setPayableType(str(payload, "payable_type", "order"));
            payment.setPayableId(str(payload, "payable_id", UUID.randomUUID().toString()));
            payment.setAmount(decimal(payload, "amount", BigDecimal.ZERO));
            payment.setSlug(str(payload, "slug", "payment-" + UUID.randomUUID()));
            payment.setStatus(intVal(payload, "status", 0));
            payment.setType(intVal(payload, "type", 0));
            payment.setDescription(str(payload, "description", null));
            paymentRepository.save(payment);
            return payment.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(paymentRepository::findById).ifPresent(payment -> {
            if ("update".equals(action)) {
                if (payload.containsKey("status")) payment.setStatus(intVal(payload, "status", payment.getStatus()));
                if (payload.containsKey("amount")) payment.setAmount(decimal(payload, "amount", payment.getAmount()));
                paymentRepository.save(payment);
            } else if ("delete".equals(action)) {
                payment.setDeletedAt(OffsetDateTime.now());
                paymentRepository.save(payment);
            }
        });
        return null;
    }

    private UUID mutateUser(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            User user = new User();
            user.setFirstName(str(payload, "first_name", "User"));
            user.setLastName(str(payload, "last_name", "Created"));
            String email = str(payload, "email", "user-" + UUID.randomUUID() + "@example.com");
            user.setEmail(email.toLowerCase());
            user.setName((str(payload, "prefix", "").trim() + " " + user.getFirstName() + " " + user.getLastName()).trim().replaceAll("\\s+", " "));
            user.setSlug("user-" + UUID.randomUUID());
            user.setTemporary(false);
            user.setFavorite(false);
            userRepository.save(user);
            return user.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(userRepository::findById).ifPresent(user -> {
            if ("update".equals(action)) {
                if (payload.containsKey("first_name")) user.setFirstName(str(payload, "first_name", user.getFirstName()));
                if (payload.containsKey("last_name")) user.setLastName(str(payload, "last_name", user.getLastName()));
                if (payload.containsKey("email")) user.setEmail(str(payload, "email", user.getEmail()).toLowerCase());
                String prefix = str(payload, "prefix", "");
                user.setName((prefix.trim() + " " + user.getFirstName() + " " + user.getLastName()).trim().replaceAll("\\s+", " "));
                userRepository.save(user);
            } else if ("delete".equals(action)) {
                user.setDeletedAt(OffsetDateTime.now());
                userRepository.save(user);
            }
        });
        return null;
    }

    private UUID mutateBanner(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            Banner banner = new Banner();
            banner.setName(name);
            banner.setSlug(str(payload, "slug", "banner-" + UUID.randomUUID()));
            banner.setEnabled(bool(payload, "enabled", true));
            bannerRepository.save(banner);
            return banner.getId();
        }
        if (id == null) return null;
        uuid(id).flatMap(bannerRepository::findById).ifPresent(banner -> {
            if ("update".equals(action)) {
                if (payload.containsKey("name")) banner.setName(nameMap(payload, "name", "Banner"));
                if (payload.containsKey("enabled")) banner.setEnabled(bool(payload, "enabled", banner.isEnabled()));
                bannerRepository.save(banner);
            } else if ("delete".equals(action)) {
                banner.setDeletedAt(OffsetDateTime.now());
                bannerRepository.save(banner);
            }
        });
        return null;
    }

    private void mutateVoucherCodes(String action, Map<String, Object> payload) {
        if ("bulk-delete".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update voucher_codes set deleted_at = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-expire".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update voucher_codes set valid_until = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-start".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update voucher_codes set valid_from = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-activate".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update voucher_codes set enabled = true, valid_from = now(), valid_until = now() + interval '1 day', updated_at = now() where id = ?", id);
            }
        }
    }

    private UUID mutateVoucherCode(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            String code = str(payload, "code", null);
            if (code == null || code.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The code field is required.");
            }
            UUID entityId = UUID.randomUUID();
            jdbc.update("""
                            insert into voucher_codes(id, code, name, discount_type, amount, enabled, valid_from, valid_until, slug, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, now(), null, ?, now(), now())
                            """,
                    entityId, code, str(payload, "name", code), intVal(payload, "discount_type", 0),
                    decimal(payload, "amount", BigDecimal.ZERO), bool(payload, "enabled", true),
                    str(payload, "slug", "voucher-" + entityId));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            jdbc.update("""
                            update voucher_codes
                            set code = coalesce(?, code), name = coalesce(?, name), amount = coalesce(?, amount),
                                discount_type = coalesce(?, discount_type), enabled = coalesce(?, enabled), updated_at = now()
                            where id = ?
                            """,
                    str(payload, "code", null),
                    str(payload, "name", null),
                    payload.containsKey("amount") ? decimal(payload, "amount", BigDecimal.ZERO) : null,
                    payload.containsKey("discount_type") ? intVal(payload, "discount_type", 0) : null,
                    payload.containsKey("enabled") ? bool(payload, "enabled", true) : null,
                    entityId);
        } else if ("delete".equals(action)) {
            jdbc.update("update voucher_codes set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private void mutateDeliveryTypes(String action, Map<String, Object> payload) {
        if ("bulk-delete".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update delivery_types set deleted_at = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-availability".equals(action)) {
            boolean enabled = bool(payload, "enabled", bool(payload, "status", true));
            for (UUID id : ids(payload)) {
                jdbc.update("update delivery_types set enabled = ?, updated_at = now() where id = ?", enabled, id);
            }
        }
    }

    private UUID mutateDeliveryType(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            UUID entityId = UUID.randomUUID();
            jdbc.update("""
                            insert into delivery_types(id, name, slug, description, enabled, created_at, updated_at)
                            values (?, cast(? as jsonb), ?, cast(? as jsonb), ?, now(), now())
                            """,
                    entityId, toJson(name), str(payload, "slug", "delivery-type-" + entityId),
                    toJson(nameMap(payload, "description", "")), bool(payload, "enabled", true));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            if (payload.containsKey("name")) {
                jdbc.update("update delivery_types set name = cast(? as jsonb), updated_at = now() where id = ?", toJson(nameMap(payload, "name", "Delivery type")), entityId);
            }
            if (payload.containsKey("description")) {
                jdbc.update("update delivery_types set description = cast(? as jsonb), updated_at = now() where id = ?", toJson(nameMap(payload, "description", "")), entityId);
            }
            if (payload.containsKey("enabled")) {
                jdbc.update("update delivery_types set enabled = ?, updated_at = now() where id = ?", bool(payload, "enabled", true), entityId);
            }
        } else if ("delete".equals(action)) {
            jdbc.update("update delivery_types set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private UUID mutateDeliveryRule(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action) && id != null && uuid(id).isPresent()) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            UUID entityId = UUID.randomUUID();
            jdbc.update("""
                            insert into delivery_rules(id, delivery_type_id, name, slug, country, min_cart_price, max_cart_price, price, enabled, created_at, updated_at)
                            values (?, ?, cast(? as jsonb), ?, ?, ?, ?, 0, ?, now(), now())
                            """,
                    entityId, uuid(id).orElseThrow(), toJson(name),
                    str(payload, "slug", "delivery-rule-" + entityId),
                    str(payload, "country", null),
                    payload.containsKey("min_cart_price") ? decimal(payload, "min_cart_price", null) : null,
                    payload.containsKey("max_cart_price") ? decimal(payload, "max_cart_price", null) : null,
                    bool(payload, "enabled", true));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            if (payload.containsKey("name")) {
                jdbc.update("update delivery_rules set name = cast(? as jsonb), updated_at = now() where id = ?", toJson(nameMap(payload, "name", "Delivery rule")), entityId);
            }
            if (payload.containsKey("enabled")) {
                jdbc.update("update delivery_rules set enabled = ?, updated_at = now() where id = ?", bool(payload, "enabled", true), entityId);
            }
        } else if ("delete".equals(action)) {
            jdbc.update("update delivery_rules set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private void mutateDiscountRules(String action, Map<String, Object> payload) {
        if ("bulk-delete".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update discount_rules set deleted_at = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-expire".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update discount_rules set valid_until = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-start".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update discount_rules set valid_from = now(), updated_at = now() where id = ?", id);
            }
        } else if ("bulk-activate".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update discount_rules set enabled = true, valid_from = now(), valid_until = now() + interval '1 day', updated_at = now() where id = ?", id);
            }
        }
    }

    private UUID mutateDiscountRule(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            Map<String, String> name = nameMap(payload, "name", null);
            if (!name.containsKey("en") || name.get("en") == null || name.get("en").isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            UUID entityId = UUID.randomUUID();
            jdbc.update("""
                            insert into discount_rules(id, type, name, amount, valid_from, valid_until, enabled, slug, created_at, updated_at)
                            values (?, ?, cast(? as jsonb), ?, now(), null, ?, ?, now(), now())
                            """,
                    entityId, intVal(payload, "type", 0), toJson(name), decimal(payload, "amount", BigDecimal.ZERO),
                    bool(payload, "enabled", true), str(payload, "slug", "discount-rule-" + entityId));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            if (payload.containsKey("name")) {
                jdbc.update("update discount_rules set name = cast(? as jsonb), updated_at = now() where id = ?", toJson(nameMap(payload, "name", "Discount")), entityId);
            }
            if (payload.containsKey("amount")) {
                jdbc.update("update discount_rules set amount = ?, updated_at = now() where id = ?", decimal(payload, "amount", BigDecimal.ZERO), entityId);
            }
            if (payload.containsKey("enabled")) {
                jdbc.update("update discount_rules set enabled = ?, updated_at = now() where id = ?", bool(payload, "enabled", true), entityId);
            }
        } else if ("delete".equals(action)) {
            jdbc.update("update discount_rules set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private void mutateDiscountRuleProduct(String action, String id) {
        String[] parts = id == null ? new String[0] : id.split(":");
        if (parts.length != 2 || uuid(parts[0]).isEmpty() || uuid(parts[1]).isEmpty()) return;
        UUID ruleId = uuid(parts[0]).orElseThrow();
        UUID productId = uuid(parts[1]).orElseThrow();
        if ("attach".equals(action)) {
            Integer ruleExists = jdbc.queryForObject("select count(*) from discount_rules where id = ? and deleted_at is null", Integer.class, ruleId);
            Integer productExists = jdbc.queryForObject("select count(*) from products where id = ? and deleted_at is null", Integer.class, productId);
            if (ruleExists == null || productExists == null || ruleExists == 0 || productExists == 0) {
                return;
            }
            jdbc.update("insert into discount_rule_product(discount_rule_id, product_id) values (?, ?) on conflict do nothing", ruleId, productId);
        } else if ("detach".equals(action)) {
            jdbc.update("delete from discount_rule_product where discount_rule_id = ? and product_id = ?", ruleId, productId);
        }
    }

    private void mutateDiscountRuleCategory(String action, String id) {
        String[] parts = id == null ? new String[0] : id.split(":");
        if (parts.length != 2 || uuid(parts[0]).isEmpty() || uuid(parts[1]).isEmpty()) return;
        UUID ruleId = uuid(parts[0]).orElseThrow();
        UUID categoryId = uuid(parts[1]).orElseThrow();
        if ("attach".equals(action)) {
            Integer ruleExists = jdbc.queryForObject("select count(*) from discount_rules where id = ? and deleted_at is null", Integer.class, ruleId);
            Integer categoryExists = jdbc.queryForObject("select count(*) from product_categories where id = ? and deleted_at is null", Integer.class, categoryId);
            if (ruleExists == null || categoryExists == null || ruleExists == 0 || categoryExists == 0) {
                return;
            }
            jdbc.update("insert into discount_rule_product_category(discount_rule_id, product_category_id) values (?, ?) on conflict do nothing", ruleId, categoryId);
        } else if ("detach".equals(action)) {
            jdbc.update("delete from discount_rule_product_category where discount_rule_id = ? and product_category_id = ?", ruleId, categoryId);
        }
    }

    private UUID mutateFile(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            String name = str(payload, "name", null);
            if (name == null || name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            UUID entityId = UUID.randomUUID();
            jdbc.update("""
                            insert into file_contents(id, name, mime_type, size, path, slug, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, now(), now())
                            """,
                    entityId, name, str(payload, "mime_type", "application/octet-stream"),
                    longVal(payload, "size", 0), str(payload, "path", "/tmp/" + name), str(payload, "slug", "file-" + entityId));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            jdbc.update("update file_contents set name = coalesce(?, name), path = coalesce(?, path), updated_at = now() where id = ?",
                    str(payload, "name", null), str(payload, "path", null), entityId);
        } else if ("delete".equals(action)) {
            jdbc.update("update file_contents set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private void mutateFiles(String action, Map<String, Object> payload) {
        if ("bulk-delete".equals(action)) {
            for (UUID id : ids(payload)) {
                jdbc.update("update file_contents set deleted_at = now(), updated_at = now() where id = ?", id);
            }
        }
    }

    private UUID mutateNote(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            String text = str(payload, "text", str(payload, "content", null));
            if (text == null || text.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The text field is required.");
            }
            UUID entityId = UUID.randomUUID();
            UUID userId = uuid(str(payload, "user_id", "")).orElse(null);
            UUID noteableId = uuid(str(payload, "noteable_id", "")).orElse(null);
            jdbc.update("""
                            insert into notes(id, user_id, noteable_type, noteable_id, text, slug, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, now(), now())
                            """,
                    entityId, userId, str(payload, "noteable_type", null), noteableId, text, str(payload, "slug", "note-" + entityId));
            return entityId;
        }
        if (id == null || uuid(id).isEmpty()) return null;
        UUID entityId = uuid(id).orElseThrow();
        if ("update".equals(action)) {
            String text = str(payload, "text", str(payload, "content", null));
            if (text == null || text.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The text field is required.");
            }
            jdbc.update("update notes set text = ?, updated_at = now() where id = ?", text, entityId);
        } else if ("delete".equals(action)) {
            jdbc.update("update notes set deleted_at = now(), updated_at = now() where id = ?", entityId);
        }
        return null;
    }

    private Long mutateRole(String action, String id, Map<String, Object> payload) {
        if ("create".equals(action)) {
            String name = str(payload, "name", null);
            if (name == null || name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", name);
            return jdbc.queryForObject("select id from roles where name = ?", Long.class, name);
        }
        if (id == null) return null;
        Long roleId = longId(id).orElse(null);
        if (roleId == null) return null;
        if ("update".equals(action)) {
            String name = str(payload, "name", null);
            if (name == null || name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The name field is required.");
            }
            jdbc.update("update roles set name = ?, updated_at = now() where id = ?", name, roleId);
        } else if ("delete".equals(action)) {
            jdbc.update("delete from roles where id = ? and name <> 'super_admin'", roleId);
        }
        return null;
    }

    private void mutateRolePermission(String action, String id) {
        String[] parts = id == null ? new String[0] : id.split(":");
        if (parts.length != 2) return;
        Long roleId = longId(parts[0]).orElse(null);
        Long permissionId = longId(parts[1]).orElse(null);
        if (roleId == null || permissionId == null) return;
        if ("attach".equals(action)) {
            jdbc.update("insert into role_has_permissions(role_id, permission_id) values (?, ?) on conflict do nothing", roleId, permissionId);
        } else if ("detach".equals(action)) {
            jdbc.update("delete from role_has_permissions where role_id = ? and permission_id = ?", roleId, permissionId);
        }
    }

    private void mutateRoleUser(String action, String id) {
        String[] parts = id == null ? new String[0] : id.split(":");
        if (parts.length != 2) return;
        Long roleId = longId(parts[0]).orElse(null);
        UUID userId = uuid(parts[1]).orElse(null);
        if (roleId == null || userId == null) return;
        if ("assign".equals(action)) {
            jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?) on conflict do nothing", roleId, userId);
        } else if ("remove".equals(action)) {
            jdbc.update("delete from model_has_roles where role_id = ? and user_id = ?", roleId, userId);
        }
    }

    private List<UUID> ids(Map<String, Object> payload) {
        Object ids = payload.get("ids");
        if (!(ids instanceof Collection<?> collection)) return List.of();
        List<UUID> out = new ArrayList<>();
        for (Object id : collection) {
            uuid(String.valueOf(id)).ifPresent(out::add);
        }
        return out;
    }

    private Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<Long> longId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> nameMap(Map<String, Object> body, String key, String fallbackEn) {
        Object value = body.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
            if (!out.containsKey("en")) out.put("en", fallbackEn);
            return out;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    Map<String, Object> parsed = objectMapper.readValue(trimmed, new TypeReference<>() {});
                    Map<String, String> out = new LinkedHashMap<>();
                    parsed.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
                    if (!out.isEmpty()) {
                        if (!out.containsKey("en")) out.put("en", fallbackEn);
                        return out;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (value != null) {
            out.put("en", String.valueOf(value));
        } else if (fallbackEn != null) {
            out.put("en", fallbackEn);
        }
        return out;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        String s = String.valueOf(value);
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "active".equalsIgnoreCase(s);
    }

    private int intVal(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longVal(Map<String, Object> body, String key, long fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        Object value = body.get(key);
        if (value == null) return fallback;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
