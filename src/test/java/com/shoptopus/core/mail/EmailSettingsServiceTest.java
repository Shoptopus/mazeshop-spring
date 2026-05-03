package com.shoptopus.core.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailSettingsServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SystemSetting> settings = new LinkedHashMap<>();
    private final Map<String, AccessToken> tokens = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        when(systemSettingRepository.findByKeyAndDeletedAtIsNull(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(settings.get(invocation.getArgument(0, String.class))));
        when(systemSettingRepository.save(any(SystemSetting.class)))
                .thenAnswer(invocation -> {
                    SystemSetting setting = invocation.getArgument(0, SystemSetting.class);
                    if (setting.getId() == null) {
                        setting.setId(UUID.randomUUID());
                    }
                    settings.put(setting.getKey(), setting);
                    return setting;
                });

        when(accessTokenRepository.findByToken(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(tokens.get(invocation.getArgument(0, String.class))));
        when(accessTokenRepository.save(any(AccessToken.class)))
                .thenAnswer(invocation -> {
                    AccessToken token = invocation.getArgument(0, AccessToken.class);
                    if (token.getId() == null) {
                        token.setId(UUID.randomUUID());
                    }
                    tokens.put(token.getToken(), token);
                    return token;
                });
        doAnswer(invocation -> {
            AccessToken token = invocation.getArgument(0, AccessToken.class);
            tokens.remove(token.getToken());
            return null;
        }).when(accessTokenRepository).delete(any(AccessToken.class));
        doAnswer(invocation -> {
            String token = invocation.getArgument(0, String.class);
            tokens.remove(token);
            return null;
        }).when(accessTokenRepository).deleteByToken(anyString());

    }

    @Test
    void admin_settings_masks_secrets_and_keeps_provider() {
        putSetting("email.provider", "gmail");
        putSetting("email.from_address", "hello@example.com");
        putSetting("email.from_name", "Demo Shop");
        putSetting("email.smtp", """
                {"host":"smtp.example.com","port":587,"username":"mailer","password":"super-secret","auth":true,"starttls":true}
                """);
        putSetting("email.gmail", """
                {"client_id":"client-id","client_secret":"client-secret","refresh_token":"refresh-token","connected_email":"owner@gmail.com"}
                """);

        EmailSettingsService service = service(RestClient.builder());

        Map<String, Object> row = service.adminSettings();

        assertThat(row.get("provider")).isEqualTo("gmail");
        @SuppressWarnings("unchecked")
        Map<String, Object> smtp = (Map<String, Object>) row.get("smtp");
        @SuppressWarnings("unchecked")
        Map<String, Object> gmail = (Map<String, Object>) row.get("gmail");
        assertThat(smtp.get("password")).isEqualTo(EmailSettingsService.MASKED_SECRET);
        assertThat(gmail.get("client_secret")).isEqualTo(EmailSettingsService.MASKED_SECRET);
        assertThat(gmail.get("connected")).isEqualTo(true);
        assertThat(gmail.get("connected_email")).isEqualTo("owner@gmail.com");
    }

    @Test
    void gmail_callback_saves_tokens_and_connected_email() throws Exception {
        putSetting("email.provider", "gmail");
        putSetting("email.from_address", "owner@gmail.com");
        putSetting("email.from_name", "Demo Shop");
        putSetting("email.gmail", """
                {"client_id":"client-id","client_secret":"client-secret"}
                """);

        AccessToken stateToken = new AccessToken();
        stateToken.setId(UUID.randomUUID());
        stateToken.setType(6);
        stateToken.setToken("gmail-state");
        stateToken.setAccessableType("EmailProvider");
        stateToken.setAccessableId("gmail");
        stateToken.setExpiry(OffsetDateTime.now().plusMinutes(5));
        Map<String, Object> stateContent = new LinkedHashMap<>();
        stateContent.put("provider", "gmail");
        stateContent.put("return_url", "http://localhost:8081/studio/shop-settings");
        stateToken.setContent(stateContent);
        tokens.put(stateToken.getToken(), stateToken);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andRespond(withSuccess("""
                        {"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"email":"owner@gmail.com"}
                        """, MediaType.APPLICATION_JSON));

        EmailSettingsService service = service(builder);
        java.net.URI redirect = service.gmailCallbackRedirect("auth-code", "gmail-state", null);

        assertThat(redirect.toString()).contains("gmail=connected");
        Map<String, Object> stored = objectMapper.readValue(settings.get("email.gmail").getValue(), new TypeReference<>() {});
        assertThat(stored.get("access_token")).isEqualTo("access-token");
        assertThat(stored.get("refresh_token")).isEqualTo("refresh-token");
        assertThat(stored.get("connected_email")).isEqualTo("owner@gmail.com");
        assertThat(tokens).doesNotContainKey("gmail-state");
        server.verify();
    }

    @Test
    void gmail_refresh_updates_access_token_when_expired() throws Exception {
        putSetting("email.provider", "gmail");
        putSetting("email.from_address", "owner@gmail.com");
        putSetting("email.from_name", "Demo Shop");
        putSetting("email.gmail", """
                {"client_id":"client-id","client_secret":"client-secret","access_token":"expired-token","refresh_token":"refresh-token","access_token_expires_at":"%s","connected_email":"owner@gmail.com"}
                """.formatted(OffsetDateTime.now().minusMinutes(10)));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("""
                        {"access_token":"fresh-token","expires_in":1800}
                        """, MediaType.APPLICATION_JSON));

        EmailSettingsService service = service(builder);
        EmailSettingsService.GmailSettings gmail = service.gmailSettingsWithFreshAccessToken();

        assertThat(gmail.accessToken()).isEqualTo("fresh-token");
        Map<String, Object> stored = objectMapper.readValue(settings.get("email.gmail").getValue(), new TypeReference<>() {});
        assertThat(stored.get("access_token")).isEqualTo("fresh-token");
        assertThat(stored.get("refresh_token")).isEqualTo("refresh-token");
        server.verify();
    }

    private EmailSettingsService service(RestClient.Builder builder) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public <T> T query(String sql, ResultSetExtractor<T> rse, Object... args) {
                if (sql.contains("from system_settings")) {
                    String key = (String) args[0];
                    SystemSetting setting = settings.get(key);
                    @SuppressWarnings("unchecked")
                    T value = (T) (setting == null ? null : setting.getId());
                    return value;
                }
                return null;
            }

            @Override
            public int update(String sql, Object... args) {
                if (sql.startsWith("delete from access_tokens")) {
                    tokens.remove(args[0]);
                    return 1;
                }
                if (sql.contains("insert into system_settings")) {
                    SystemSetting setting = new SystemSetting();
                    setting.setId((UUID) args[0]);
                    setting.setKey((String) args[1]);
                    setting.setValue((String) args[2]);
                    settings.put(setting.getKey(), setting);
                    return 1;
                }
                if (sql.startsWith("update system_settings set value")) {
                    String value = (String) args[0];
                    UUID id = (UUID) args[2];
                    settings.values().stream()
                            .filter(setting -> id.equals(setting.getId()))
                            .findFirst()
                            .ifPresent(setting -> setting.setValue(value));
                    return 1;
                }
                return 0;
            }
        };
        return new EmailSettingsService(
                systemSettingRepository,
                accessTokenRepository,
                jdbcTemplate,
                objectMapper,
                builder,
                "hello@example.com",
                "Shoptopus",
                "localhost",
                1025,
                "",
                "",
                false,
                false,
                "http://localhost:8081/studio",
                "http://localhost:8080"
        );
    }

    private void putSetting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setId(UUID.randomUUID());
        setting.setKey(key);
        setting.setValue(value);
        settings.put(key, setting);
    }
}
