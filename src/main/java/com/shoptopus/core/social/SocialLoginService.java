package com.shoptopus.core.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.auth.AccessToken;
import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.AuthPayloadDto;
import com.shoptopus.core.auth.security.AccessTokenRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SocialLoginService {

    private static final int SOCIAL_OAUTH_STATE_TOKEN_TYPE = 4;
    private static final int SOCIAL_OAUTH_HANDOFF_TOKEN_TYPE = 5;

    private final SocialProviderSettingsService socialProviderSettingsService;
    private final AccessTokenRepository accessTokenRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String storefrontPublicBaseUrl;

    public SocialLoginService(
            SocialProviderSettingsService socialProviderSettingsService,
            AccessTokenRepository accessTokenRepository,
            AuthService authService,
            ObjectMapper objectMapper,
            @Value("${app.storefront.public-base-url:http://localhost:8080}") String storefrontPublicBaseUrl
    ) {
        this.socialProviderSettingsService = socialProviderSettingsService;
        this.accessTokenRepository = accessTokenRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.storefrontPublicBaseUrl = storefrontPublicBaseUrl == null || storefrontPublicBaseUrl.isBlank()
                ? "http://localhost:8080"
                : storefrontPublicBaseUrl.replaceAll("/+$", "");
    }

    public URI authorizationRedirect(String provider) {
        SocialProviderConfig config = requireConfiguredProvider(provider);
        String stateToken = createStateToken(config);
        String authorizeUrl = UriComponentsBuilder.fromUriString(config.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", socialProviderSettingsService.callbackUrl(config))
                .queryParam("scope", String.join(" ", config.getScopes()))
                .queryParam("state", stateToken)
                .build()
                .encode()
                .toUriString();
        return URI.create(authorizeUrl);
    }

    @Transactional("mainTransactionManager")
    public URI callbackRedirect(String provider, String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            return storefrontCallbackUri(null, error);
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return storefrontCallbackUri(null, "The social login response was incomplete.");
        }

        AccessToken stateToken = accessTokenRepository.findByToken(state)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login state is invalid."));
        validateStateToken(provider, stateToken);
        SocialProviderConfig config = requireConfiguredProvider(provider);

        try {
            Map<String, Object> tokenResponse = requestToken(config, code);
            String accessToken = string(tokenResponse.get("access_token"));
            if (accessToken == null || accessToken.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Social login failed", "The OAuth provider did not return an access token.");
            }

            Map<String, Object> profile = requestUserInfo(config, accessToken);
            AuthPayloadDto authPayload = authService.socialLogin(
                    requiredProfileField(profile, config.getEmailAttribute(), "email"),
                    firstName(profile, config),
                    lastName(profile, config)
            );

            AccessToken handoff = new AccessToken();
            handoff.setType(SOCIAL_OAUTH_HANDOFF_TOKEN_TYPE);
            handoff.setToken(UUID.randomUUID().toString());
            handoff.setAccessableType("User");
            handoff.setAccessableId(String.valueOf(authPayload.user().id()));
            handoff.setExpiry(OffsetDateTime.now().plusMinutes(5));
            handoff.setContent(Map.of(
                    "jwt", authPayload.token(),
                    "email", authPayload.user().email()
            ));
            accessTokenRepository.save(handoff);
            accessTokenRepository.delete(stateToken);
            return storefrontCallbackUri(handoff.getToken(), null);
        } catch (ApiException ex) {
            accessTokenRepository.delete(stateToken);
            return storefrontCallbackUri(null, ex.getUserMessage());
        } catch (Exception ex) {
            accessTokenRepository.delete(stateToken);
            return storefrontCallbackUri(null, "Unable to complete social login.");
        }
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> completeLogin(String token) {
        AccessToken handoff = accessTokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login token is invalid."));
        if (handoff.getType() != SOCIAL_OAUTH_HANDOFF_TOKEN_TYPE || handoff.getExpiry() == null || handoff.getExpiry().isBefore(OffsetDateTime.now())) {
            accessTokenRepository.delete(handoff);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login token has expired.");
        }
        String jwt = string(handoff.getContent() == null ? null : handoff.getContent().get("jwt"));
        String email = string(handoff.getContent() == null ? null : handoff.getContent().get("email"));
        if (jwt == null || email == null) {
            accessTokenRepository.delete(handoff);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login token is invalid.");
        }
        accessTokenRepository.delete(handoff);
        return authResponse(new AuthPayloadDto(jwt, "Bearer", authService.me(email)));
    }

    public Map<String, Object> authResponse(AuthPayloadDto payload) {
        var auth = new LinkedHashMap<String, Object>();
        auth.put("authenticated", true);
        auth.put("token", payload.token());
        auth.put("token_type", payload.tokenType());
        auth.put("user", payload.user());

        var data = new LinkedHashMap<String, Object>();
        data.put("auth", auth);
        return data;
    }

    private Map<String, Object> requestToken(SocialProviderConfig config, String code) {
        String body = UriComponentsBuilder.newInstance()
                .queryParam("grant_type", "authorization_code")
                .queryParam("code", code)
                .queryParam("redirect_uri", socialProviderSettingsService.callbackUrl(config))
                .queryParam("client_id", config.getClientId())
                .queryParam("client_secret", config.getClientSecret())
                .build()
                .getQuery();
        String raw = restClient.post()
                .uri(config.getTokenUri())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(body == null ? "" : body)
                .retrieve()
                .body(String.class);
        return readJsonMap(raw);
    }

    private Map<String, Object> requestUserInfo(SocialProviderConfig config, String accessToken) {
        String raw = restClient.get()
                .uri(config.getUserInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class);
        return readJsonMap(raw);
    }

    private SocialProviderConfig requireConfiguredProvider(String provider) {
        SocialProviderConfig config = socialProviderSettingsService.enabledConfig(provider);
        if (blank(config.getClientId()) || blank(config.getClientSecret()) || blank(config.getAuthorizationUri()) || blank(config.getTokenUri()) || blank(config.getUserInfoUri())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social provider is not fully configured.");
        }
        return config;
    }

    private String createStateToken(SocialProviderConfig config) {
        AccessToken token = new AccessToken();
        token.setType(SOCIAL_OAUTH_STATE_TOKEN_TYPE);
        token.setToken(UUID.randomUUID().toString());
        token.setAccessableType("OAuth");
        token.setAccessableId(config.getProvider());
        token.setExpiry(OffsetDateTime.now().plusMinutes(10));
        token.setContent(Map.of("provider", config.getProvider()));
        accessTokenRepository.save(token);
        return token.getToken();
    }

    private void validateStateToken(String provider, AccessToken token) {
        if (token.getType() != SOCIAL_OAUTH_STATE_TOKEN_TYPE || token.getExpiry() == null || token.getExpiry().isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login state has expired.");
        }
        String expectedProvider = string(token.getContent() == null ? null : token.getContent().get("provider"));
        if (expectedProvider == null || !expectedProvider.equalsIgnoreCase(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The social login state is invalid.");
        }
    }

    private URI storefrontCallbackUri(String token, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(storefrontPublicBaseUrl + "/auth/social/callback");
        if (token != null && !token.isBlank()) {
            builder.queryParam("token", token);
        }
        if (error != null && !error.isBlank()) {
            builder.queryParam("error", error);
        }
        return builder.build(true).toUri();
    }

    private Map<String, Object> readJsonMap(String raw) {
        try {
            return objectMapper.readValue(raw == null ? "{}" : raw, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Social login failed", "The OAuth provider returned an invalid response.");
        }
    }

    private String requiredProfileField(Map<String, Object> profile, String field, String label) {
        String value = string(profile.get(field));
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Social login failed", "The OAuth provider did not return a " + label + " address.");
        }
        return value;
    }

    private String firstName(Map<String, Object> profile, SocialProviderConfig config) {
        String firstName = string(profile.get(config.getFirstNameAttribute()));
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        String name = string(profile.get(config.getNameAttribute()));
        if (name == null || name.isBlank()) {
            return "Customer";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts[0];
    }

    private String lastName(Map<String, Object> profile, SocialProviderConfig config) {
        String lastName = string(profile.get(config.getLastNameAttribute()));
        if (lastName != null && !lastName.isBlank()) {
            return lastName;
        }
        String name = string(profile.get(config.getNameAttribute()));
        if (name == null || name.isBlank()) {
            return "User";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "User";
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
