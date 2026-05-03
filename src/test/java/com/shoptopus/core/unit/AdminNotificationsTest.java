package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.engagement.Notification;
import com.shoptopus.core.engagement.repository.NotificationRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminNotificationsTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void product_running_low_notification_is_sent() {
        User user = createUser("notif-low");
        createNotification(user, "admin.product.running_low", "Product running low");
        List<Notification> rows = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(rows).extracting(Notification::getType).contains("admin.product.running_low");
    }

    @Test
    void product_out_of_stock_notification_is_sent() {
        User user = createUser("notif-out");
        createNotification(user, "admin.product.out_of_stock", "Product out of stock");
        List<Notification> rows = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(rows).extracting(Notification::getType).contains("admin.product.out_of_stock");
    }

    @Test
    void new_order_notification_is_sent() {
        User user = createUser("notif-order");
        createNotification(user, "admin.order.new", "New order");
        List<Notification> rows = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(rows).extracting(Notification::getType).contains("admin.order.new");
    }

    @Test
    void user_signup_notification_is_sent() {
        User user = createUser("notif-signup");
        createNotification(user, "admin.user.signup", "User signed up");
        List<Notification> rows = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(rows).extracting(Notification::getType).contains("admin.user.signup");
    }

    private void createNotification(User user, String type, String title) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setData(Map.of("id", UUID.randomUUID().toString()));
        notificationRepository.save(notification);
    }

    private User createUser(String prefix) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setFirstName(prefix);
        user.setLastName("test");
        user.setName(prefix + " test");
        user.setEmail(prefix + "-" + suffix + "@example.com");
        user.setSlug("user-" + UUID.randomUUID());
        user.setTemporary(false);
        return userRepository.save(user);
    }
}
