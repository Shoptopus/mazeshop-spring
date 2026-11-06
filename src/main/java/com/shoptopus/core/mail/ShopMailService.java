package com.shoptopus.core.mail;

import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.store.StoreStatus;
import com.shoptopus.core.storefront.StorefrontOrderConfirmationService;
import com.shoptopus.core.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopMailService {

    private static final Logger log = LoggerFactory.getLogger(ShopMailService.class);

    private final EmailDeliveryService emailDeliveryService;
    private final SystemMailOutbox outbox;
    private final ShopEmailTemplateService templateService;
    private final JdbcTemplate jdbc;
    private final StorefrontOrderConfirmationService storefrontOrderConfirmationService;
    private final String adminUiBaseUrl;
    private final String storefrontBaseUrl;

    public ShopMailService(
            EmailDeliveryService emailDeliveryService,
            SystemMailOutbox outbox,
            ShopEmailTemplateService templateService,
            JdbcTemplate jdbc,
            StorefrontOrderConfirmationService storefrontOrderConfirmationService,
            @Value("${app.admin-ui.public-base-url:http://localhost:8080/studio}")
                    String adminUiBaseUrl,
            @Value("${app.storefront.public-base-url:http://localhost:8080}")
                    String storefrontBaseUrl) {
        this.emailDeliveryService = emailDeliveryService;
        this.outbox = outbox;
        this.templateService = templateService;
        this.jdbc = jdbc;
        this.storefrontOrderConfirmationService = storefrontOrderConfirmationService;
        this.adminUiBaseUrl = adminUiBaseUrl;
        this.storefrontBaseUrl = storefrontBaseUrl;
    }

    public void sendNewMessageToCustomer(
            String recipientEmail, String orderSlug, UUID threadId, String senderName) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        String url = storefrontBaseUrl.replaceAll("/+$", "") + "/account/messages/" + threadId;
        sendHtml(
                recipientEmail,
                "New message about " + orderSlug,
                templateService.wrap(
                        "New message",
                        "A new message is waiting for you.",
                        templateService.paragraph(
                                        "You have a new message from "
                                                + senderName
                                                + " about order "
                                                + orderSlug
                                                + ".")
                                + templateService.cta("Open conversation", url)
                                + templateService.note("Reply from your account messages page.")));
    }

    public void sendNewMessageToAdmins(Order order, UUID threadId, String senderName) {
        String url = adminUiBaseUrl.replaceAll("/+$", "") + "/messages/" + threadId;
        for (String recipient : adminRecipients()) {
            sendHtml(
                    recipient,
                    "Customer message for " + order.getSlug(),
                    templateService.wrap(
                            "Customer message",
                            "A buyer sent a new message.",
                            templateService.paragraph(
                                            senderName
                                                    + " sent a new message about order "
                                                    + order.getSlug()
                                                    + ".")
                                    + templateService.cta("Open thread", url)
                                    + templateService.note(
                                            "This notification was sent to an admin account with"
                                                + " message access.")));
        }
    }

    public void sendNewOrderEmails(Order order) {
        sendNewOrderAdminEmails(order);
    }

    public void sendCustomerReceiptEmail(Order order, String invoiceToken) {
        User user = order.getUser();
        String customerEmail = user == null ? order.getContactEmail() : user.getEmail();
        if (customerEmail == null || customerEmail.isBlank()) {
            return;
        }
        String storefrontUrl =
                storefrontBaseUrl.replaceAll("/+$", "")
                        + "/order/"
                        + order.getId()
                        + "/confirmation";
        var guestAccess =
                jdbc.queryForList(
                        "select access_token from storefront_checkout_attempts where order_id=?",
                        String.class,
                        order.getId());
        if (!guestAccess.isEmpty()) storefrontUrl += "?access_token=" + guestAccess.get(0);
        String invoiceUrl =
                storefrontBaseUrl.replaceAll("/+$", "") + "/api/storefront/invoice/" + invoiceToken;
        String orderSummary = orderSummaryHtml(order);
        outbox.enqueue(
                customerEmail,
                "Receipt for " + order.getSlug(),
                templateService.wrap(
                        "Thank you for your order",
                        "Your receipt is ready.",
                        templateService.paragraph(
                                        "Thank you for your order. Your receipt for "
                                                + order.getSlug()
                                                + " is ready.")
                                + orderSummary
                                + templateService.cta("Download receipt", invoiceUrl)
                                + templateService.note(
                                        "You can use this receipt link again until it expires. You"
                                            + " can still view the order summary from your"
                                            + " account.")
                                + templateService.cta("View order", storefrontUrl)));
    }

    public void sendNewOrderAdminEmails(Order order) {
        String adminUrl = adminUiBaseUrl.replaceAll("/+$", "") + "/orders/" + order.getId();
        for (String recipient : adminRecipients()) {
            outbox.enqueue(
                    recipient,
                    "New order " + order.getSlug(),
                    templateService.wrap(
                            "New order",
                            "A new order was placed.",
                            templateService.paragraph(
                                            "A new order was placed: " + order.getSlug() + ".")
                                    + templateService.cta("Open order", adminUrl)));
        }
    }

    public void sendOrderStatusUpdated(
            String recipientEmail, UUID orderId, String orderSlug, String statusLabel) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        String url = storefrontBaseUrl.replaceAll("/+$", "") + "/account/orders/" + orderId;
        sendHtml(
                recipientEmail,
                "Order " + orderSlug + " updated",
                templateService.wrap(
                        "Order status updated",
                        "Your order status changed.",
                        templateService.paragraph(
                                        "Your order "
                                                + orderSlug
                                                + " is now marked as "
                                                + statusLabel.toLowerCase(Locale.ROOT)
                                                + ".")
                                + templateService.cta("View order", url)));
    }

    public void sendStoreApplicationReceived(Store store) {
        String recipientEmail = storeOwnerRecipient(store);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        sendHtml(
                recipientEmail,
                "We received your store application for " + store.getName(),
                templateService.wrap(
                        "Store application received",
                        "Your store is now pending review.",
                        templateService.paragraph(
                                        "Thanks for creating "
                                                + escape(store.getName())
                                                + ". Your store is now in a pending review state.")
                                + templateService.paragraph(
                                        "We are reviewing the submission and will notify you as"
                                            + " soon as the status changes.")
                                + templateService.definitionList(
                                        Map.of(
                                                "Store name", store.getName(),
                                                "Namespace", "/" + store.getSlug(),
                                                "Status", store.getStatus().label()))
                                + templateService.note(
                                        "You can keep using your customer account while the review"
                                            + " is in progress.")));
    }

    public void sendStoreStatusUpdated(Store store) {
        String recipientEmail = storeOwnerRecipient(store);
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        if (store.getStatus() == StoreStatus.ACTIVE) {
            sendHtml(
                    recipientEmail,
                    store.getName() + " is now active",
                    templateService.wrap(
                            "Store approved",
                            "Your store is now live in the marketplace.",
                            templateService.paragraph(
                                            "Good news. "
                                                    + escape(store.getName())
                                                    + " has been approved and is now active.")
                                    + templateService.paragraph(
                                            "You can now sign in to the admin area and manage your"
                                                + " storefront.")
                                    + optionalReason(store)
                                    + templateService.cta("Open admin area", adminUiBaseUrl)
                                    + templateService.definitionList(
                                            Map.of(
                                                    "Store name", store.getName(),
                                                    "Namespace", "/" + store.getSlug(),
                                                    "Status", store.getStatus().label()))));
            return;
        }
        if (store.getStatus() == StoreStatus.SUSPENDED
                || store.getStatus() == StoreStatus.DEACTIVATED) {
            sendHtml(
                    recipientEmail,
                    store.getName() + " status updated",
                    templateService.wrap(
                            "Store status updated",
                            "Your store status has changed.",
                            templateService.paragraph(
                                            escape(store.getName())
                                                    + " is now marked as "
                                                    + store.getStatus()
                                                            .label()
                                                            .toLowerCase(Locale.ROOT)
                                                    + ".")
                                    + templateService.paragraph(
                                            "This usually means the store is unavailable because of"
                                                + " a policy or compliance issue that needs to be"
                                                + " addressed.")
                                    + optionalReason(store)
                                    + templateService.definitionList(
                                            Map.of(
                                                    "Store name", store.getName(),
                                                    "Namespace", "/" + store.getSlug(),
                                                    "Status", store.getStatus().label()))
                                    + templateService.note(
                                            "Please contact support or review the relevant policy"
                                                + " guidance before requesting reactivation.")));
            return;
        }
        sendHtml(
                recipientEmail,
                store.getName() + " is pending review",
                templateService.wrap(
                        "Store pending review",
                        "Your store is being reviewed.",
                        templateService.paragraph(
                                        escape(store.getName()) + " is currently pending review.")
                                + optionalReason(store)
                                + templateService.definitionList(
                                        Map.of(
                                                "Store name", store.getName(),
                                                "Namespace", "/" + store.getSlug(),
                                                "Status", store.getStatus().label()))));
    }

    private String orderSummaryHtml(Order order) {
        Map<String, Object> confirmation =
                storefrontOrderConfirmationService.confirmation(order.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) confirmation.getOrDefault("items", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments =
                (List<Map<String, Object>>) confirmation.getOrDefault("payments", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> totals =
                (Map<String, Object>) confirmation.getOrDefault("totals", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> voucherCode =
                (Map<String, Object>) confirmation.getOrDefault("voucher_code", Map.of());

        StringBuilder html = new StringBuilder();
        html.append(
                templateService.definitionList(
                        Map.of(
                                "Order ID", String.valueOf(confirmation.getOrDefault("slug", "")),
                                "Payment ID",
                                        payments.isEmpty()
                                                ? "—"
                                                : String.valueOf(
                                                        payments.get(0).getOrDefault("id", "—")),
                                "Payment method",
                                        payments.isEmpty()
                                                ? "—"
                                                : String.valueOf(
                                                        payments.get(0)
                                                                .getOrDefault(
                                                                        "payment_method", "—")))));
        html.append("<div style=\"margin:24px 0 16px;\">");
        html.append("<h2 style=\"margin:0 0 12px;font-size:18px;\">Products</h2>");
        for (Map<String, Object> item : items) {
            html.append("<div style=\"padding:12px 0;border-top:1px solid rgba(35,26,19,0.12);\">");
            html.append("<p style=\"margin:0 0 6px;font-weight:600;\">")
                    .append(escape(String.valueOf(item.getOrDefault("name", ""))))
                    .append("</p>");
            @SuppressWarnings("unchecked")
            Map<String, Object> variant = (Map<String, Object>) item.get("variant");
            if (variant != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> attributes =
                        (List<Map<String, Object>>) variant.getOrDefault("attributes", List.of());
                if (!attributes.isEmpty()) {
                    html.append("<p style=\"margin:0 0 6px;color:#6f635b;\">");
                    html.append(
                            attributes.stream()
                                    .map(
                                            attribute ->
                                                    escape(
                                                                    String.valueOf(
                                                                            attribute.getOrDefault(
                                                                                    "attribute_name",
                                                                                    "")))
                                                            + ": "
                                                            + escape(
                                                                    String.valueOf(
                                                                            attribute.getOrDefault(
                                                                                    "option_name",
                                                                                    ""))))
                                    .reduce((left, right) -> left + " · " + right)
                                    .orElse(""));
                    html.append("</p>");
                }
            }
            html.append("<p style=\"margin:0;color:#6f635b;\">");
            html.append("Count: ").append(escape(String.valueOf(item.getOrDefault("quantity", 0))));
            html.append(" · Unit price: ").append(escape(money(item.get("unit_price"))));
            Object unitDiscount = item.get("unit_discount");
            if (unitDiscount != null
                    && money(unitDiscount) != null
                    && !money(unitDiscount).endsWith("0.00")) {
                html.append(" · Original price: ")
                        .append(escape(money(item.get("original_unit_price"))));
                html.append(" · Discounted price: ").append(escape(money(item.get("unit_price"))));
            }
            html.append(" · Total: ").append(escape(money(item.get("final_price"))));
            html.append("</p></div>");
        }
        html.append("</div>");
        html.append(
                templateService.definitionList(
                        Map.of(
                                "Total saved", money(totals.get("discount_amount")),
                                "Delivery", money(totals.get("delivery_amount")),
                                "Voucher code value applied",
                                        voucherCode.isEmpty()
                                                ? "GBP 0.00"
                                                : money(voucherCode.get("discount_amount")),
                                "Total", money(totals.get("total_amount")))));
        return html.toString();
    }

    private String money(Object raw) {
        if (raw == null) {
            return "GBP 0.00";
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(String.valueOf(raw));
            return "GBP " + value.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return String.valueOf(raw);
        }
    }

    private String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    private Set<String> adminRecipients() {
        List<String> emails =
                jdbc.query(
                        """
select distinct u.email
from users u
join model_has_roles mr on mr.user_id = u.id
join roles r on r.id = mr.role_id
left join role_has_permissions rp on rp.role_id = r.id
left join permissions p on p.id = rp.permission_id
where u.deleted_at is null
  and lower(coalesce(u.email, '')) <> ''
  and (
    r.name = 'super_admin'
    or p.name in ('messages.view', 'messages.manage', 'orders.view', 'orders.manage')
  )
order by u.email
""",
                        (rs, rowNum) -> rs.getString(1));
        return new LinkedHashSet<>(emails);
    }

    private String optionalReason(Store store) {
        if (store == null || store.getStatusReason() == null || store.getStatusReason().isBlank()) {
            return "";
        }
        return templateService.note("Review note: " + escape(store.getStatusReason().trim()));
    }

    private String storeOwnerRecipient(Store store) {
        if (store == null) {
            return null;
        }
        if (store.getOwnerEmail() != null && !store.getOwnerEmail().isBlank()) {
            return store.getOwnerEmail();
        }
        if (store.getOwnerUser() != null
                && store.getOwnerUser().getEmail() != null
                && !store.getOwnerUser().getEmail().isBlank()) {
            return store.getOwnerUser().getEmail();
        }
        return null;
    }

    private void sendHtml(String recipient, String subject, String html) {
        try {
            emailDeliveryService.sendHtml(recipient, subject, html);
        } catch (Exception ex) {
            log.warn("Unable to send email '{}' to {}: {}", subject, recipient, ex.getMessage());
        }
    }
}
