package com.shoptopus.core.storefront;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.discount.DiscountRule;
import com.shoptopus.core.discount.repository.DiscountRuleRepository;
import com.shoptopus.core.store.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StorefrontPricingService {

    private final DiscountRuleRepository discountRuleRepository;

    public StorefrontPricingService(DiscountRuleRepository discountRuleRepository) {
        this.discountRuleRepository = discountRuleRepository;
    }

    @Transactional(readOnly = true)
    public Pricing pricing(Product product) {
        BigDecimal base = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
        DiscountRule bestRule = bestDiscountRule(product).orElse(null);
        if (bestRule == null) {
            return new Pricing(base, base, false, null, null);
        }
        BigDecimal discounted = switch (bestRule.getType() == null ? 0 : bestRule.getType()) {
            case 1 -> base.subtract(base.multiply(bestRule.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            case 2 -> base.subtract(bestRule.getAmount());
            default -> base;
        };
        if (discounted.compareTo(BigDecimal.ZERO) < 0) {
            discounted = BigDecimal.ZERO;
        }
        return new Pricing(base, discounted, discounted.compareTo(base) < 0, bestRule.getType(), bestRule.getAmount());
    }

    @Transactional(readOnly = true)
    public Optional<DiscountRule> bestDiscountRule(Product product) {
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> categoryIds = product.getCategories().stream().map(ProductCategory::getId).collect(Collectors.toSet());
        return discountRulesFor(product.getStore()).stream()
                .filter(rule -> rule.isEnabled())
                .filter(rule -> rule.getValidFrom() == null || !rule.getValidFrom().isAfter(now))
                .filter(rule -> rule.getValidUntil() == null || !rule.getValidUntil().isBefore(now))
                .filter(rule -> rule.getProducts().stream().anyMatch(item -> item.getId().equals(product.getId()))
                        || rule.getCategories().stream().anyMatch(category -> categoryIds.contains(category.getId())))
                .max(Comparator.comparing(rule -> effectiveDiscountValue(rule, product.getPrice() == null ? BigDecimal.ZERO : product.getPrice())));
    }

    private java.util.List<DiscountRule> discountRulesFor(Store store) {
        if (store == null || store.getId() == null) {
            return discountRuleRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return discountRuleRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(store.getId());
    }

    public BigDecimal effectiveDiscountValue(DiscountRule rule, BigDecimal base) {
        return switch (rule.getType() == null ? 0 : rule.getType()) {
            case 1 -> base.multiply(rule.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case 2 -> rule.getAmount();
            default -> BigDecimal.ZERO;
        };
    }

    public record Pricing(BigDecimal original, BigDecimal finalAmount, boolean discounted, Integer discountType, BigDecimal discountValue) {}
}
