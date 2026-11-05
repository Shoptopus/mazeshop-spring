package com.shoptopus.core.logging;

import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.settings.SystemSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditMailSettingsSnapshotTest {
    private final AuditTrailCaptureService capture = new AuditTrailCaptureService(
            mock(AuditTrailBuffer.class), mock(AdminStoreContext.class));

    @ParameterizedTest
    @ValueSource(strings = {"email.gmail", "email.smtp"})
    void mail_credentials_are_excluded_from_audit_snapshots(String key) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(key);
        setting.setValue("{\"client_secret\":\"oauth-secret\",\"refresh_token\":\"refresh-secret\",\"password\":\"smtp-secret\"}");

        var snapshot = capture.snapshot(setting);

        assertThat(snapshot).containsEntry("key", key).containsEntry("value", "[redacted]");
        assertThat(snapshot.toString()).doesNotContain("oauth-secret", "refresh-secret", "smtp-secret");
        assertThat(setting.getValue()).contains("oauth-secret");
    }

    @Test
    void gmail_oauth_state_is_excluded_from_audit_snapshots() {
        AccessToken state = new AccessToken();
        state.setType(6);
        state.setToken("one-use-oauth-state");

        assertThat(capture.snapshot(state)).containsEntry("token", "[redacted]");
        assertThat(state.getToken()).isEqualTo("one-use-oauth-state");
    }

    @Test
    void nonsecret_settings_still_have_auditable_values() {
        SystemSetting setting = new SystemSetting();
        setting.setKey("shop.name");
        setting.setValue("Mazeshop");

        assertThat(capture.snapshot(setting)).containsEntry("value", "Mazeshop");
    }
}
