package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMessagingControllerTest extends AdminControllerTestBase {

    @Test
    void admin_can_open_order_message_thread_and_reply() {
        String token = token();
        UUID orderId = seedOrder();

        var ensure = authed(token, HttpMethod.POST, "/api/admin-ui/orders/" + orderId + "/message-thread", null);
        assert2xx(ensure);
        String threadId = String.valueOf(((Map<?, ?>) ensure.getBody().get("data")).get("id"));

        var reply = authed(token, HttpMethod.POST, "/api/admin-ui/messages/" + threadId + "/messages", Map.of(
                "body", "<p>Reply from admin</p>"
        ));
        assert2xx(reply);
        assertThat(String.valueOf(((Map<?, ?>) reply.getBody().get("data")).get("sender_role"))).isEqualTo("admin");

        var list = authed(token, HttpMethod.GET, "/api/admin-ui/messages?page=1&per_page=10", null);
        assert2xx(list);
        Map<?, ?> data = (Map<?, ?>) list.getBody().get("data");
        List<Map<?, ?>> items = (List<Map<?, ?>>) data.get("items");
        Map<?, ?> row = items.stream()
                .filter(item -> orderId.toString().equals(String.valueOf(item.get("order_id"))))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(row.get("most_recent_message"))).contains("Reply from admin");

        var detail = authed(token, HttpMethod.GET, "/api/admin-ui/messages/" + threadId, null);
        assert2xx(detail);
        List<Map<?, ?>> messages = (List<Map<?, ?>>) ((Map<?, ?>) detail.getBody().get("data")).get("messages");
        assertThat(messages).hasSize(1);
        assertThat(String.valueOf(messages.get(0).get("sender_role"))).isEqualTo("admin");
    }

    private UUID seedOrder() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into users(id, first_name, last_name, email, password, name, slug, temporary, is_favorite, created_at, updated_at)
                values (?::uuid, 'Order', 'Messaging', ?, '$2a$10$abcdefghijklmnopqrstuv', 'Order Messaging', ?, false, false, ?, ?)
                """, userId.toString(), "messaging-" + orderId + "@example.com", "user-" + orderId, now, now);
        jdbc.update("""
                insert into addresses(id, address_line_1, town, post_code, slug, user_id, country, name, created_at, updated_at)
                values (?::uuid, 'Order St', 'Cardiff', 'CF10 1AA', ?, ?::uuid, 'UK', 'Order Messaging', ?, ?)
                """, addressId.toString(), "address-" + addressId, userId.toString(), now, now);
        jdbc.update("""
                insert into orders(id, address_id, user_id, original_price, subtotal, total_price, total_discount, delivery_cost, currency_code, status, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, 10, 9, 12, 1, 3, 'GBP', 1, ?, ?, ?)
                """, orderId.toString(), addressId.toString(), userId.toString(), "order-" + orderId, now, now);
        return orderId;
    }
}
