package com.shoptopus.core.invoice;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.storefront.StorefrontCheckoutService;
import com.shoptopus.core.user.Address;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InvoiceSnapshotService {

    private final ManagedFileService managedFileService;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final Path storageRoot;
    private final String publicBaseUrl;

    public InvoiceSnapshotService(
            ManagedFileService managedFileService,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            @Value("${app.media.storage-path}") String storagePath,
            @Value("${app.media.public-base-url:/media}") String publicBaseUrl
    ) {
        this.managedFileService = managedFileService;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
    }

    public Invoice buildInvoice(
            Order order,
            Address address,
            Payment payment,
            String paymentProvider,
            Map<String, Object> paymentDetails,
            StorefrontCheckoutService.CheckoutPricingView pricing,
            Object voucherCode,
            DeliveryType deliveryType,
            BigDecimal deliveryCost,
            String invoiceSlug,
            Map<String, Object> stripeSnapshot
    ) {
        Invoice invoice = new Invoice();
        invoice.setUser(order.getUser());
        invoice.setOrder(order);
        invoice.setSlug(invoiceSlug);
        invoice.setAddress(addressSnapshot(address));
        invoice.setPayment(paymentSnapshot(payment, paymentProvider, paymentDetails, stripeSnapshot));
        invoice.setVoucherCode(voucherSnapshot(voucherCode, pricing.voucherDiscount()));
        invoice.setProducts(Map.of("items", order.getItems().stream().map(this::itemSnapshot).toList()));
        invoice.setDeliveryType(deliverySnapshot(deliveryType, deliveryCost));
        invoice.setTotals(totalsSnapshot(pricing));
        invoice.setValid(true);
        return invoice;
    }

    private Map<String, Object> addressSnapshot(Address address) {
        Map<String, Object> invoiceAddress = new LinkedHashMap<>();
        invoiceAddress.put("id", address.getId());
        invoiceAddress.put("name", address.getName());
        invoiceAddress.put("address_line_1", address.getAddressLine1());
        invoiceAddress.put("address_line_2", address.getAddressLine2());
        invoiceAddress.put("note", address.getNote());
        invoiceAddress.put("town", address.getTown());
        invoiceAddress.put("post_code", address.getPostCode());
        invoiceAddress.put("country", address.getCountry());
        return invoiceAddress;
    }

    private Map<String, Object> paymentSnapshot(Payment payment, String paymentProvider, Map<String, Object> paymentDetails, Map<String, Object> stripeSnapshot) {
        Map<String, Object> invoicePayment = new LinkedHashMap<>();
        invoicePayment.put("provider", paymentProvider);
        invoicePayment.put("payment_id", payment.getId());
        invoicePayment.put("payment_ref", payment.getPaymentRef());
        invoicePayment.put("status", payment.getStatus());
        invoicePayment.put("amount", payment.getAmount());
        invoicePayment.put("currency", "GBP");
        Map<String, Object> invoicePaymentDetails = new LinkedHashMap<>(paymentDetails == null ? Map.of() : paymentDetails);
        if (stripeSnapshot != null && !stripeSnapshot.isEmpty()) {
            invoicePaymentDetails.putAll(stripeSnapshot);
        }
        invoicePayment.put("details", invoicePaymentDetails);
        return invoicePayment;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> voucherSnapshot(Object voucherCode, BigDecimal voucherDiscount) {
        Map<String, Object> invoiceVoucher = new LinkedHashMap<>();
        if (!(voucherCode instanceof com.shoptopus.core.voucher.VoucherCode voucher)) {
            return invoiceVoucher;
        }
        invoiceVoucher.put("id", voucher.getId());
        invoiceVoucher.put("code", voucher.getCode());
        invoiceVoucher.put("name", voucher.getName());
        invoiceVoucher.put("type", voucher.getType());
        invoiceVoucher.put("amount", voucher.getAmount());
        invoiceVoucher.put("discount_amount", voucherDiscount);
        invoiceVoucher.put("discount", money(voucherDiscount));
        return invoiceVoucher;
    }

    private Map<String, Object> deliverySnapshot(DeliveryType deliveryType, BigDecimal deliveryCost) {
        Map<String, Object> invoiceDelivery = new LinkedHashMap<>();
        invoiceDelivery.put("id", deliveryType.getId());
        invoiceDelivery.put("slug", deliveryType.getSlug());
        invoiceDelivery.put("name", deliveryType.getName());
        invoiceDelivery.put("description", deliveryType.getDescription());
        invoiceDelivery.put("price", deliveryCost);
        invoiceDelivery.put("price_display", money(deliveryCost));
        return invoiceDelivery;
    }

    private Map<String, Object> totalsSnapshot(StorefrontCheckoutService.CheckoutPricingView pricing) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("currency", "GBP");
        row.put("item_count", pricing.itemCount());
        row.put("original_amount", pricing.originalSubtotal());
        row.put("items_amount", pricing.itemsSubtotal());
        row.put("item_discount_amount", pricing.itemDiscountTotal());
        row.put("voucher_discount_amount", pricing.voucherDiscount());
        row.put("discount_amount", pricing.totalSaved());
        row.put("delivery_amount", pricing.deliveryAmount());
        row.put("subtotal_amount", pricing.itemsSubtotal());
        row.put("total_amount", pricing.total());
        row.put("original", money(pricing.originalSubtotal()));
        row.put("items", money(pricing.itemsSubtotal()));
        row.put("item_discount", money(pricing.itemDiscountTotal()));
        row.put("voucher_discount", money(pricing.voucherDiscount()));
        row.put("discount", money(pricing.totalSaved()));
        row.put("delivery", money(pricing.deliveryAmount()));
        row.put("subtotal", money(pricing.itemsSubtotal()));
        row.put("total", money(pricing.total()));
        return row;
    }

    private Map<String, Object> itemSnapshot(OrderItem item) {
        Map<String, Object> value = new LinkedHashMap<>();
        Product product = item.getProduct();
        ProductVariant variant = item.getProductVariant();
        value.put("product_id", product == null ? null : product.getId());
        value.put("product_variant_id", variant == null ? null : variant.getId());
        value.put("name", item.getName());
        value.put("quantity", item.getAmount());
        value.put("customization_value", item.getCustomizationValue());
        value.put("currency", "GBP");
        value.put("original_unit_price", item.getOriginalUnitPrice());
        value.put("unit_price", item.getUnitPrice());
        value.put("unit_discount", item.getUnitDiscount());
        value.put("line_original_amount", item.getFullPrice());
        value.put("line_discount_amount", item.getTotalDiscount());
        value.put("line_total_amount", item.getFinalPrice());
        value.put("original_unit_price_display", money(item.getOriginalUnitPrice()));
        value.put("unit_price_display", money(item.getUnitPrice()));
        value.put("unit_discount_display", money(item.getUnitDiscount()));
        value.put("line_original_display", money(item.getFullPrice()));
        value.put("line_discount_display", money(item.getTotalDiscount()));
        value.put("line_total_display", money(item.getFinalPrice()));
        value.put("variant", variantSnapshot(variant));
        Map<String, Object> cover = coverSnapshot(product);
        value.put("cover_image", cover);
        value.put("cover_thumbnail_base64", cover.get("thumbnail_base64"));
        return value;
    }

    private Map<String, Object> variantSnapshot(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("slug", variant.getSlug());
        row.put("sku", variant.getSku());
        row.put("description", TranslatedText.resolve(variant.getDescription(), Locale.ENGLISH));
        row.put("attributes", variant.attributeOptionsAsList().stream()
                .map(this::variantAttributeRow)
                .filter(Objects::nonNull)
                .toList());
        return row;
    }

    private Map<String, Object> variantAttributeRow(Object rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            UUID optionId = UUID.fromString(String.valueOf(rawId));
            ProductAttributeOption option = productAttributeOptionRepository.findById(optionId).orElse(null);
            if (option == null) {
                return null;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attribute_id", option.getProductAttribute() == null ? null : option.getProductAttribute().getId());
            row.put("attribute_name", option.getProductAttribute() == null ? null : TranslatedText.resolve(option.getProductAttribute().getName(), Locale.ENGLISH));
            row.put("option_id", option.getId());
            row.put("option_name", TranslatedText.resolve(option.getName(), Locale.ENGLISH));
            row.put("option_value", option.getValue());
            return row;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> coverSnapshot(Product product) {
        Map<String, Object> cover = new LinkedHashMap<>();
        if (product == null) {
            return cover;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = managedFileService.findAttachedSingle("product", product.getId(), "cover_photo");
        if (payload == null || payload.isEmpty()) {
            payload = product.getCoverPhoto() == null ? Map.of() : new LinkedHashMap<>(product.getCoverPhoto());
        }
        if (payload.isEmpty()) {
            return cover;
        }
        cover.putAll(payload);
        cover.put("thumbnail_base64", thumbnailBase64(payload));
        return cover;
    }

    private String thumbnailBase64(Map<String, Object> media) {
        try {
            Path imagePath = resolveMediaPath(media);
            if (imagePath == null || !Files.exists(imagePath)) {
                return null;
            }
            byte[] sourceBytes = Files.readAllBytes(imagePath);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null) {
                return null;
            }
            BufferedImage target = new BufferedImage(70, 70, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, 70, 70);
                double scale = Math.min(70d / source.getWidth(), 70d / source.getHeight());
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (70 - width) / 2;
                int y = (70 - height) / 2;
                graphics.drawImage(source, x, y, width, height, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private Path resolveMediaPath(Map<String, Object> media) {
        String path = string(media.get("path"));
        String url = string(media.get("url"));
        String candidate = path;
        if (candidate == null || candidate.isBlank()) {
            candidate = url;
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String normalizedBase = normalizedPublicBase();
        String relative = candidate;
        if (relative.startsWith(normalizedBase + "/")) {
            relative = relative.substring(normalizedBase.length());
        }
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path resolved = Path.of(relative);
        if (resolved.isAbsolute()) {
            return resolved.normalize();
        }
        return storageRoot.resolve(relative).normalize();
    }

    private String normalizedPublicBase() {
        String base = publicBaseUrl == null ? "/media" : publicBaseUrl.trim();
        String normalizedBase = base.startsWith("/") ? base : "/" + base;
        return normalizedBase.replaceAll("/+$", "");
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }
}
