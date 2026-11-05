package com.shoptopus.core.mail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.settings.SystemSetting;
import com.shoptopus.core.settings.repository.SystemSettingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailSettingsService {

    private static final int GMAIL_OAUTH_STATE_TOKEN_TYPE = 6;
    public static final String MASKED_SECRET = "__UNCHANGED__";

    private static final String KEY_EMAIL_PROVIDER = "email.provider";
    private static final String KEY_EMAIL_FROM_ADDRESS = "email.from_address";
    private static final String KEY_EMAIL_FROM_NAME = "email.from_name";
    private static final String KEY_EMAIL_SMTP = "email.smtp";
    private static final String KEY_EMAIL_GMAIL = "email.gmail";

    private static final String GMAIL_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GMAIL_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final List<String> GMAIL_SCOPES = List.of(
            "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/userinfo.email",
            "openid"
    );

    private final SystemSettingRepository systemSettingRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String defaultFromAddress;
    private final String defaultFromName;
    private final String defaultMailHost;
    private final Integer defaultMailPort;
    private final String defaultMailUsername;
    private final String defaultMailPassword;
    private final boolean defaultMailAuth;
    private final boolean defaultMailStarttls;
    private final String defaultAdminUiBaseUrl;
    private final String defaultApiBaseUrl;

    public EmailSettingsService(
            SystemSettingRepository systemSettingRepository,
            AccessTokenRepository accessTokenRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.from-address:}") String defaultFromAddress,
            @Value("${app.mail.from-name:Shoptopus}") String defaultFromName,
            @Value("${spring.mail.host:localhost}") String defaultMailHost,
            @Value("${spring.mail.port:1025}") Integer defaultMailPort,
            @Value("${spring.mail.username:}") String defaultMailUsername,
            @Value("${spring.mail.password:}") String defaultMailPassword,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean defaultMailAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean defaultMailStarttls,
            @Value("${app.admin-ui.public-base-url:http://localhost:8080/studio}") String defaultAdminUiBaseUrl,
            @Value("${app.api.public-base-url:http://localhost:8080}") String defaultApiBaseUrl
    ) {
        this.systemSettingRepository = systemSettingRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.defaultFromAddress = defaultFromAddress;
        this.defaultFromName = defaultFromName;
        this.defaultMailHost = defaultMailHost;
        this.defaultMailPort = defaultMailPort == null ? 1025 : defaultMailPort;
        this.defaultMailUsername = defaultMailUsername;
        this.defaultMailPassword = defaultMailPassword;
        this.defaultMailAuth = defaultMailAuth;
        this.defaultMailStarttls = defaultMailStarttls;
        this.defaultAdminUiBaseUrl = defaultAdminUiBaseUrl == null || defaultAdminUiBaseUrl.isBlank()
                ? "http://localhost:8080/studio"
                : defaultAdminUiBaseUrl.replaceAll("/+$", "");
        this.defaultApiBaseUrl = defaultApiBaseUrl == null || defaultApiBaseUrl.isBlank()
                ? "http://localhost:8080"
                : defaultApiBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSettings() {
        EmailSettings settings = readSettings();
        Map<String, Object> smtp = new LinkedHashMap<>();
        smtp.put("host", settings.smtp().host());
        smtp.put("port", settings.smtp().port());
        smtp.put("username", settings.smtp().username());
        smtp.put("password", masked(settings.smtp().password()));
        smtp.put("auth", settings.smtp().auth());
        smtp.put("starttls", settings.smtp().starttls());

        Map<String, Object> gmail = new LinkedHashMap<>();
        gmail.put("client_id", settings.gmail().clientId());
        gmail.put("client_secret", masked(settings.gmail().clientSecret()));
        gmail.put("connected", settings.gmail().connected());
        gmail.put("connected_email", settings.gmail().connectedEmail());
        gmail.put("access_token_expires_at", settings.gmail().accessTokenExpiresAt() == null ? null : settings.gmail().accessTokenExpiresAt().toString());
        gmail.put("callback_url", callbackUrl());
        gmail.put("scopes", GMAIL_SCOPES);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("provider", settings.provider());
        row.put("from_address", settings.fromAddress());
        row.put("from_name", settings.fromName());
        row.put("smtp", smtp);
        row.put("gmail", gmail);
        return row;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> saveAdminSettings(Object rawValue) {
        EmailSettings current = readSettings();
        EmailSettings next = mergedSettings(current, rawValue, true);
        persist(next);
        return adminSettings();
    }

    @Transactional("mainTransactionManager")
    public URI gmailAuthorizationRedirect(Object rawEmailSettings, String returnUrl) {
        EmailSettings current = readSettings();
        EmailSettings next = mergedSettings(current, rawEmailSettings, true);
        persist(next);
        GmailSettings gmail = next.gmail();
        if (blank(gmail.clientId()) || blank(gmail.clientSecret())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Gmail client ID and client secret are required before connecting.");
        }
        AccessToken stateToken = new AccessToken();
        stateToken.setType(GMAIL_OAUTH_STATE_TOKEN_TYPE);
        stateToken.setToken(UUID.randomUUID().toString());
        stateToken.setAccessableType("EmailProvider");
        stateToken.setAccessableId("gmail");
        stateToken.setExpiry(OffsetDateTime.now().plusMinutes(10));
        Map<String, Object> stateContent = new LinkedHashMap<>();
        stateContent.put("provider", "gmail");
        stateContent.put("return_url", normalizedReturnUrl(returnUrl));
        stateToken.setContent(stateContent);
        accessTokenRepository.save(stateToken);
        String url = UriComponentsBuilder.fromUriString(GMAIL_AUTHORIZATION_URI)
                .queryParam("response_type", "code")
                .queryParam("client_id", gmail.clientId())
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("scope", String.join(" ", GMAIL_SCOPES))
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", stateToken.getToken())
                .build()
                .encode()
                .toUriString();
        return URI.create(url);
    }

    @Transactional("mainTransactionManager")
    public URI gmailCallbackRedirect(String code, String state, String error) {
        if (!blank(error)) {
            return adminRedirect(null, error);
        }
        if (blank(code) || blank(state)) {
            return adminRedirect(null, "The Gmail OAuth response was incomplete.");
        }
        AccessToken stateToken = accessTokenRepository.findByToken(state)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The Gmail OAuth state is invalid."));
        Map<String, Object> stateContent = stateToken.getContent() == null
                ? Map.of()
                : new LinkedHashMap<>(stateToken.getContent());
        String returnUrl = string(stateContent.get("return_url"));
        try {
            validateStateToken(stateContent, stateToken.getType(), stateToken.getExpiry());
            EmailSettings settings = readSettings();
            GmailSettings gmail = settings.gmail();
            if (blank(gmail.clientId()) || blank(gmail.clientSecret())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Gmail client ID and client secret are required before connecting.");
            }
            Map<String, Object> tokenResponse = requestAuthorizationCodeTokens(gmail, code);
            String accessToken = string(tokenResponse.get("access_token"));
            String refreshToken = string(tokenResponse.get("refresh_token"));
            Number expiresIn = number(tokenResponse.get("expires_in"));
            if (blank(accessToken)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Gmail connection failed", "The Gmail token response did not include an access token.");
            }
            Map<String, Object> profile = requestUserProfile(accessToken);
            GmailSettings updatedGmail = gmail.withTokens(
                    accessToken,
                    refreshToken == null ? gmail.refreshToken() : refreshToken,
                    expiresIn == null ? null : OffsetDateTime.now().plusSeconds(expiresIn.longValue()),
                    string(profile.get("email"))
            );
            persistDirect(settings.withGmail(updatedGmail));
            return adminRedirect(returnUrl, null);
        } catch (ApiException ex) {
            return adminRedirect(returnUrl, ex.getMessage() == null ? ex.getUserMessage() : ex.getMessage());
        } catch (Exception ex) {
            return adminRedirect(returnUrl, "Unable to complete Gmail authentication.");
        } finally {
            deleteAccessTokenDirect(stateToken.getToken());
        }
    }

    @Transactional("mainTransactionManager")
    public void disconnectGmail() {
        EmailSettings settings = readSettings();
        persist(settings.withGmail(settings.gmail().withoutTokens()));
    }

    public void sendTestEmail(String recipient, EmailDeliveryService emailDeliveryService) {
        if (blank(recipient)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "A recipient email is required for the test email.");
        }
        EmailSettings settings = readSettings();
        emailDeliveryService.sendHtml(
                recipient.trim(),
                "Shoptopus email test",
                """
                        <p style="margin:0 0 16px;line-height:1.6;">This is a test email from your Shoptopus email provider configuration.</p>
                        <p style="margin:0;line-height:1.6;">Provider: <strong>%s</strong></p>
                        """.formatted(escape(settings.provider()))
        );
    }

    @Transactional(readOnly = true)
    public EmailSettings readSettings() {
        return new EmailSettings(
                providerSetting(),
                stringSetting(KEY_EMAIL_FROM_ADDRESS, defaultFromAddress),
                stringSetting(KEY_EMAIL_FROM_NAME, defaultFromName),
                readSmtpSettings(),
                readGmailSettings()
        );
    }

    @Transactional(transactionManager = "mainTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public GmailSettings gmailSettingsWithFreshAccessToken() {
        EmailSettings settings = readSettings();
        GmailSettings gmail = settings.gmail();
        if (!"gmail".equalsIgnoreCase(settings.provider())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Gmail is not the active email provider.");
        }
        if (!gmail.connected()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Gmail has not been connected yet.");
        }
        if (!needsRefresh(gmail)) {
            return gmail;
        }
        if (blank(gmail.refreshToken())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gmail delivery failed", "The Gmail refresh token is missing. Reconnect the Gmail account.");
        }
        Map<String, Object> tokenResponse = requestRefreshTokens(gmail);
        String accessToken = string(tokenResponse.get("access_token"));
        Number expiresIn = number(tokenResponse.get("expires_in"));
        if (blank(accessToken)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gmail delivery failed", "The Gmail token refresh response did not include an access token.");
        }
        GmailSettings updated = gmail.withTokens(
                accessToken,
                gmail.refreshToken(),
                expiresIn == null ? OffsetDateTime.now().plusMinutes(50) : OffsetDateTime.now().plusSeconds(expiresIn.longValue()),
                gmail.connectedEmail()
        );
        persist(settings.withGmail(updated));
        return updated;
    }

    public String callbackUrl() {
        return defaultApiBaseUrl + "/api/admin-ui/shop-settings/email/gmail/callback";
    }

    private EmailSettings mergedSettings(EmailSettings current, Object rawValue, boolean preserveSecrets) {
        if (rawValue == null) {
            return current;
        }
        Map<String, Object> input = toMap(rawValue);
        String provider = string(input.get("provider"));
        provider = blank(provider) ? current.provider() : provider.trim().toLowerCase();
        if (!"smtp".equals(provider) && !"gmail".equals(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Email provider must be smtp or gmail.");
        }
        String fromAddress = string(input.get("from_address"));
        String fromName = string(input.get("from_name"));

        SmtpSettings smtp = current.smtp();
        if (input.get("smtp") instanceof Map<?, ?> rawSmtp) {
            Map<String, Object> smtpMap = normalizeMap(rawSmtp);
            smtp = new SmtpSettings(
                    orDefault(string(smtpMap.get("host")), current.smtp().host()),
                    intValue(smtpMap.get("port"), current.smtp().port()),
                    orDefault(string(smtpMap.get("username")), current.smtp().username()),
                    preserveSecretString(smtpMap.get("password"), current.smtp().password(), preserveSecrets),
                    boolValue(smtpMap.get("auth"), current.smtp().auth()),
                    boolValue(smtpMap.get("starttls"), current.smtp().starttls())
            );
        }

        GmailSettings gmail = current.gmail();
        if (input.get("gmail") instanceof Map<?, ?> rawGmail) {
            Map<String, Object> gmailMap = normalizeMap(rawGmail);
            String nextClientId = orDefault(string(gmailMap.get("client_id")), gmail.clientId());
            String nextClientSecret = preserveSecretString(gmailMap.get("client_secret"), gmail.clientSecret(), preserveSecrets);
            boolean credentialsChanged = !equalsSafe(gmail.clientId(), nextClientId) || !equalsSafe(gmail.clientSecret(), nextClientSecret);
            gmail = new GmailSettings(
                    nextClientId,
                    nextClientSecret,
                    credentialsChanged ? null : gmail.accessToken(),
                    credentialsChanged ? null : gmail.refreshToken(),
                    credentialsChanged ? null : gmail.accessTokenExpiresAt(),
                    credentialsChanged ? null : gmail.connectedEmail()
            );
        }

        return new EmailSettings(
                provider,
                blank(fromAddress) ? current.fromAddress() : fromAddress.trim(),
                fromName == null ? current.fromName() : fromName.trim(),
                smtp,
                gmail
        );
    }

    private void persist(EmailSettings settings) {
        writeSetting(KEY_EMAIL_PROVIDER, settings.provider());
        writeSetting(KEY_EMAIL_FROM_ADDRESS, settings.fromAddress());
        writeSetting(KEY_EMAIL_FROM_NAME, settings.fromName());
        Map<String, Object> smtp = new LinkedHashMap<>();
        smtp.put("host", settings.smtp().host());
        smtp.put("port", settings.smtp().port());
        smtp.put("username", settings.smtp().username());
        smtp.put("password", settings.smtp().password());
        smtp.put("auth", settings.smtp().auth());
        smtp.put("starttls", settings.smtp().starttls());
        writeSetting(KEY_EMAIL_SMTP, writeJson(smtp));

        Map<String, Object> gmail = new LinkedHashMap<>();
        gmail.put("client_id", settings.gmail().clientId());
        gmail.put("client_secret", settings.gmail().clientSecret());
        gmail.put("access_token", settings.gmail().accessToken());
        gmail.put("refresh_token", settings.gmail().refreshToken());
        gmail.put("access_token_expires_at", settings.gmail().accessTokenExpiresAt() == null ? null : settings.gmail().accessTokenExpiresAt().toString());
        gmail.put("connected_email", settings.gmail().connectedEmail());
        writeSetting(KEY_EMAIL_GMAIL, writeJson(gmail));
    }

    private void persistDirect(EmailSettings settings) {
        writeSettingDirect(KEY_EMAIL_PROVIDER, settings.provider());
        writeSettingDirect(KEY_EMAIL_FROM_ADDRESS, settings.fromAddress());
        writeSettingDirect(KEY_EMAIL_FROM_NAME, settings.fromName());
        Map<String, Object> smtp = new LinkedHashMap<>();
        smtp.put("host", settings.smtp().host());
        smtp.put("port", settings.smtp().port());
        smtp.put("username", settings.smtp().username());
        smtp.put("password", settings.smtp().password());
        smtp.put("auth", settings.smtp().auth());
        smtp.put("starttls", settings.smtp().starttls());
        writeSettingDirect(KEY_EMAIL_SMTP, writeJson(smtp));

        Map<String, Object> gmail = new LinkedHashMap<>();
        gmail.put("client_id", settings.gmail().clientId());
        gmail.put("client_secret", settings.gmail().clientSecret());
        gmail.put("access_token", settings.gmail().accessToken());
        gmail.put("refresh_token", settings.gmail().refreshToken());
        gmail.put("access_token_expires_at", settings.gmail().accessTokenExpiresAt() == null ? null : settings.gmail().accessTokenExpiresAt().toString());
        gmail.put("connected_email", settings.gmail().connectedEmail());
        writeSettingDirect(KEY_EMAIL_GMAIL, writeJson(gmail));
    }

    private String providerSetting() {
        String provider = stringSetting(KEY_EMAIL_PROVIDER, "smtp");
        if (!"smtp".equalsIgnoreCase(provider) && !"gmail".equalsIgnoreCase(provider)) {
            return "smtp";
        }
        return provider.toLowerCase();
    }

    private SmtpSettings readSmtpSettings() {
        Map<String, Object> raw = jsonObject(KEY_EMAIL_SMTP);
        return new SmtpSettings(
                orDefault(string(raw.get("host")), defaultMailHost),
                intValue(raw.get("port"), defaultMailPort),
                orDefault(string(raw.get("username")), defaultMailUsername),
                orDefault(string(raw.get("password")), defaultMailPassword),
                boolValue(raw.get("auth"), defaultMailAuth),
                boolValue(raw.get("starttls"), defaultMailStarttls)
        );
    }

    private GmailSettings readGmailSettings() {
        Map<String, Object> raw = jsonObject(KEY_EMAIL_GMAIL);
        return new GmailSettings(
                string(raw.get("client_id")),
                string(raw.get("client_secret")),
                string(raw.get("access_token")),
                string(raw.get("refresh_token")),
                parseDate(string(raw.get("access_token_expires_at"))),
                string(raw.get("connected_email"))
        );
    }

    private Map<String, Object> requestAuthorizationCodeTokens(GmailSettings gmail, String code) {
        String body = UriComponentsBuilder.newInstance()
                .queryParam("grant_type", "authorization_code")
                .queryParam("code", code)
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("client_id", gmail.clientId())
                .queryParam("client_secret", gmail.clientSecret())
                .build()
                .getQuery();
        return postForm(GMAIL_TOKEN_URI, body == null ? "" : body);
    }

    private Map<String, Object> requestRefreshTokens(GmailSettings gmail) {
        String body = UriComponentsBuilder.newInstance()
                .queryParam("grant_type", "refresh_token")
                .queryParam("refresh_token", gmail.refreshToken())
                .queryParam("client_id", gmail.clientId())
                .queryParam("client_secret", gmail.clientSecret())
                .build()
                .getQuery();
        return postForm(GMAIL_TOKEN_URI, body == null ? "" : body);
    }

    private Map<String, Object> requestUserProfile(String accessToken) {
        String raw = restClient.get()
                .uri(GMAIL_USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class);
        return readJson(raw, "Gmail returned an invalid user profile response.");
    }

    private Map<String, Object> postForm(String url, String body) {
        String raw = restClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .body(String.class);
        return readJson(raw, "Gmail returned an invalid token response.");
    }

    private Map<String, Object> readJson(String raw, String developerMessage) {
        try {
            return objectMapper.readValue(raw == null ? "{}" : raw, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Gmail request failed", developerMessage);
        }
    }

    private URI adminRedirect(String returnUrl, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(normalizedReturnUrl(returnUrl));
        if (blank(error)) {
            builder.replaceQueryParam("gmail", "connected");
            builder.replaceQueryParam("gmail_error");
        } else {
            builder.replaceQueryParam("gmail", "error");
            builder.replaceQueryParam("gmail_error", error);
        }
        return builder.build(true).toUri();
    }

    private String normalizedReturnUrl(String value) {
        if (!blank(value)) {
            return value;
        }
        return defaultAdminUiBaseUrl + "/shop-settings";
    }

    private void validateStateToken(Map<String, Object> tokenContent, Integer tokenType, OffsetDateTime tokenExpiry) {
        if (tokenType == null || tokenType != GMAIL_OAUTH_STATE_TOKEN_TYPE || tokenExpiry == null || tokenExpiry.isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The Gmail OAuth state has expired.");
        }
        String provider = string(tokenContent == null ? null : tokenContent.get("provider"));
        if (!"gmail".equalsIgnoreCase(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The Gmail OAuth state is invalid.");
        }
    }

    private boolean needsRefresh(GmailSettings gmail) {
        return blank(gmail.accessToken())
                || gmail.accessTokenExpiresAt() == null
                || gmail.accessTokenExpiresAt().minusMinutes(1).isBefore(OffsetDateTime.now());
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The email settings payload is invalid.");
    }

    private Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
        return result;
    }

    private Map<String, Object> jsonObject(String key) {
        String raw = stringSetting(key, "");
        if (blank(raw)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unable to store email settings.");
        }
    }

    private void writeSetting(String key, String value) {
        SystemSetting setting = systemSettingRepository.findByKeyAndDeletedAtIsNull(key).orElseGet(() -> {
            SystemSetting created = new SystemSetting();
            created.setId(UUID.randomUUID());
            created.setKey(key);
            return created;
        });
        setting.setValue(value == null ? "" : value);
        systemSettingRepository.save(setting);
    }

    private void writeSettingDirect(String key, String value) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID existingId = jdbcTemplate.query(
                """
                        select id
                        from system_settings
                        where key = ? and deleted_at is null
                        order by created_at desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                key
        );
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into system_settings (id, key, value, type, deleted_at, created_at, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    key,
                    value == null ? "" : value,
                    0,
                    null,
                    now,
                    now
            );
            return;
        }
        jdbcTemplate.update(
                "update system_settings set value = ?, updated_at = ? where id = ?",
                value == null ? "" : value,
                now,
                existingId
        );
    }

    private void deleteAccessTokenDirect(String token) {
        if (blank(token)) {
            return;
        }
        jdbcTemplate.update("delete from access_tokens where token = ?", token);
    }

    private String stringSetting(String key, String fallback) {
        return systemSettingRepository.findByKeyAndDeletedAtIsNull(key)
                .map(SystemSetting::getValue)
                .filter(value -> value != null)
                .orElse(fallback);
    }

    private String preserveSecretString(Object incoming, String current, boolean preserveSecrets) {
        String next = string(incoming);
        if (next == null) {
            return current;
        }
        if (preserveSecrets && MASKED_SECRET.equals(next)) {
            return current;
        }
        return next.trim();
    }

    private String orDefault(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private OffsetDateTime parseDate(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsSafe(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String masked(String value) {
        return blank(value) ? "" : MASKED_SECRET;
    }

    public record EmailSettings(
            String provider,
            String fromAddress,
            String fromName,
            SmtpSettings smtp,
            GmailSettings gmail
    ) {
        public EmailSettings withGmail(GmailSettings nextGmail) {
            return new EmailSettings(provider, fromAddress, fromName, smtp, nextGmail);
        }
    }

    public record SmtpSettings(
            String host,
            int port,
            String username,
            String password,
            boolean auth,
            boolean starttls
    ) {
    }

    public record GmailSettings(
            String clientId,
            String clientSecret,
            String accessToken,
            String refreshToken,
            OffsetDateTime accessTokenExpiresAt,
            String connectedEmail
    ) {
        public boolean connected() {
            return refreshToken != null && !refreshToken.isBlank();
        }

        public GmailSettings withTokens(String nextAccessToken, String nextRefreshToken, OffsetDateTime nextExpiry, String nextConnectedEmail) {
            return new GmailSettings(clientId, clientSecret, nextAccessToken, nextRefreshToken, nextExpiry, nextConnectedEmail);
        }

        public GmailSettings withoutTokens() {
            return new GmailSettings(clientId, clientSecret, null, null, null, null);
        }
    }
}
