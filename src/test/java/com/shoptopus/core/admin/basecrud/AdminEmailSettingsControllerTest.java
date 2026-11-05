package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.mail.EmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;

class AdminEmailSettingsControllerTest extends AdminControllerTestBase {

    @MockBean
    private EmailDeliveryService emailDeliveryService;

    @Autowired
    @Qualifier("mainTransactionManager")
    private PlatformTransactionManager mainTransactionManager;

    @Test
    void admin_can_manage_email_settings_authorize_gmail_and_send_test_email() {
        String token = token();

        var initial = authed(token, HttpMethod.GET, "/api/admin-ui/shop-settings", null);
        assert2xx(initial);
        Map<?, ?> initialData = (Map<?, ?>) initial.getBody().get("data");
        Map<?, ?> initialEmail = (Map<?, ?>) initialData.get("email");
        assertThat(initialEmail.get("provider")).isEqualTo("smtp");

        var update = authed(token, HttpMethod.PATCH, "/api/admin-ui/shop-settings", Map.of(
                "email", Map.of(
                        "provider", "gmail",
                        "from_address", "owner@example.com",
                        "from_name", "Demo Shop",
                        "smtp", Map.of(
                                "host", "smtp.example.com",
                                "port", 587,
                                "username", "mailer",
                                "password", "smtp-secret",
                                "auth", true,
                                "starttls", true
                        ),
                        "gmail", Map.of(
                                "client_id", "gmail-client-id",
                                "client_secret", "gmail-client-secret"
                        )
                )
        ));
        assert2xx(update);
        Map<?, ?> updatedEmail = (Map<?, ?>) ((Map<?, ?>) update.getBody().get("data")).get("email");
        assertThat(updatedEmail.get("provider")).isEqualTo("gmail");
        Map<?, ?> updatedSmtp = (Map<?, ?>) updatedEmail.get("smtp");
        Map<?, ?> updatedGmail = (Map<?, ?>) updatedEmail.get("gmail");
        assertThat(updatedSmtp.get("password")).isEqualTo("__UNCHANGED__");
        assertThat(updatedGmail.get("client_secret")).isEqualTo("__UNCHANGED__");

        assertThat(jdbc.queryForObject("select value from system_settings where key = 'email.provider' limit 1", String.class))
                .isEqualTo("gmail");
        assertThat(jdbc.queryForObject("select value from system_settings where key = 'email.from_address' limit 1", String.class))
                .isEqualTo("owner@example.com");

        var authorize = authed(token, HttpMethod.POST, "/api/admin-ui/shop-settings/email/gmail/authorize", Map.of(
                "return_url", "http://localhost:8081/studio/shop-settings",
                "email", Map.of(
                        "provider", "gmail",
                        "from_address", "owner@example.com",
                        "from_name", "Demo Shop",
                        "smtp", Map.of(
                                "host", "smtp.example.com",
                                "port", 587,
                                "username", "mailer",
                                "password", "__UNCHANGED__",
                                "auth", true,
                                "starttls", true
                        ),
                        "gmail", Map.of(
                                "client_id", "gmail-client-id",
                                "client_secret", "__UNCHANGED__"
                        )
                )
        ));
        assert2xx(authorize);
        String targetUrl = String.valueOf(((Map<?, ?>) authorize.getBody().get("data")).get("target_url"));
        assertThat(targetUrl).contains("accounts.google.com");
        assertThat(targetUrl).contains("client_id=gmail-client-id");
        Integer oauthStateRows = jdbc.queryForObject("select count(*) from access_tokens where type = 6", Integer.class);
        assertThat(oauthStateRows).isGreaterThan(0);

        // Real Gmail refresh uses a separate transaction. It must see committed settings,
        // and its new token must survive after the test endpoint returns.
        doAnswer(invocation -> {
            TransactionTemplate refresh = new TransactionTemplate(mainTransactionManager);
            refresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            refresh.executeWithoutResult(status -> {
                jdbc.execute("set local lock_timeout = '750ms'");
                String committed = jdbc.queryForObject(
                        "select value from system_settings where key = 'email.gmail' and deleted_at is null", String.class);
                assertThat(committed).contains("updated-gmail-client-secret");
                jdbc.update("""
                        update system_settings
                        set value = jsonb_set(value::jsonb, '{access_token}', to_jsonb('fresh-test-token'::text))::text
                        where key = 'email.gmail' and deleted_at is null
                        """);
            });
            return null;
        }).when(emailDeliveryService).sendHtml(eq("test-recipient@example.com"), eq("Shoptopus email test"), contains("Shoptopus email provider configuration"));

        var sendTest = authed(token, HttpMethod.POST, "/api/admin-ui/shop-settings/email/test", Map.of(
                "recipient", "test-recipient@example.com",
                "email", Map.of(
                        "provider", "gmail",
                        "from_address", "owner@example.com",
                        "from_name", "Demo Shop",
                        "smtp", Map.of(
                                "host", "smtp.example.com",
                                "port", 587,
                                "username", "mailer",
                                "password", "__UNCHANGED__",
                                "auth", true,
                                "starttls", true
                        ),
                        "gmail", Map.of(
                                "client_id", "gmail-client-id",
                                "client_secret", "updated-gmail-client-secret"
                        )
                )
        ));
        assert2xx(sendTest);
        verify(emailDeliveryService).sendHtml(eq("test-recipient@example.com"), eq("Shoptopus email test"), contains("Shoptopus email provider configuration"));

        assertThat(jdbc.queryForObject("select value from system_settings where key = 'email.gmail' and deleted_at is null", String.class))
                .contains("fresh-test-token");

        var disconnect = authed(token, HttpMethod.POST, "/api/admin-ui/shop-settings/email/gmail/disconnect", Map.of());
        assert2xx(disconnect);
        String gmailRaw = jdbc.queryForObject("select value from system_settings where key = 'email.gmail' limit 1", String.class);
        assertThat(gmailRaw).contains("\"refresh_token\":null");
        assertThat(gmailRaw).contains("\"access_token\":null");
    }

    @Test
    void gmail_callback_is_public_and_redirects_without_authentication() {
        var response = restTemplate.getForEntity(
                "/api/admin-ui/shop-settings/email/gmail/callback?error=access_denied",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).contains("gmail=error");
    }
}
