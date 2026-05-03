package com.shoptopus.core.googlemerchant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class GoogleMerchantApiClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleMerchantApiClient.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/content";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GoogleMerchantApiClient(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public GoogleMerchantSession authorize(String serviceAccountJson) {
        ServiceAccountCredentials credentials;
        try {
            credentials = parseCredentials(serviceAccountJson);
        } catch (ApiException ex) {
            log.warn("Google Merchant auth failed while parsing service account JSON: developerMessage={}", ex.getMessage());
            throw ex;
        }
        log.info("Google Merchant auth: parsed service account credentials clientEmail={}, projectId={}, privateKeyId={}, tokenUri={}",
                credentials.clientEmail(),
                blankToDash(credentials.projectId()),
                abbreviateKeyId(credentials.privateKeyId()),
                credentials.tokenUri());
        String assertion;
        try {
            assertion = signedAssertion(credentials);
        } catch (ApiException ex) {
            log.warn("Google Merchant auth failed while signing JWT assertion clientEmail={}, projectId={}, privateKeyId={}: developerMessage={}",
                    credentials.clientEmail(),
                    blankToDash(credentials.projectId()),
                    abbreviateKeyId(credentials.privateKeyId()),
                    ex.getMessage());
            throw ex;
        }
        log.info("Google Merchant auth: JWT assertion signed clientEmail={}, projectId={}, tokenUri={}, scope={}",
                credentials.clientEmail(),
                blankToDash(credentials.projectId()),
                credentials.tokenUri(),
                SCOPE);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        body.add("assertion", assertion);
        try {
            log.info("Google Merchant auth: requesting OAuth token clientEmail={}, projectId={}, tokenUri={}",
                    credentials.clientEmail(),
                    blankToDash(credentials.projectId()),
                    credentials.tokenUri());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(credentials.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String accessToken = string(response == null ? null : response.get("access_token"));
            if (accessToken == null || accessToken.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Google Merchant request failed", "Google OAuth token response did not contain an access token.");
            }
            GoogleMerchantSession session = new GoogleMerchantSession(accessToken, credentials.clientEmail());
            log.info("Google Merchant auth: OAuth token acquired clientEmail={}, projectId={}, sessionId={}, tokenType={}, expiresIn={}, responseKeys={}",
                    credentials.clientEmail(),
                    blankToDash(credentials.projectId()),
                    session.sessionId(),
                    string(response.get("token_type")),
                    string(response.get("expires_in")),
                    responseKeys(response));
            return session;
        } catch (RestClientResponseException ex) {
            throw merchantException("Google OAuth token request failed.", ex, context(
                    "client_email", credentials.clientEmail(),
                    "project_id", credentials.projectId(),
                    "private_key_id", abbreviateKeyId(credentials.privateKeyId()),
                    "token_uri", credentials.tokenUri()
            ));
        }
    }

    public Map<String, Object> createPrimaryDataSource(
            GoogleMerchantSession session,
            String merchantId,
            String displayName,
            String feedLabel,
            String contentLanguage,
            String targetCountry
    ) {
        Map<String, Object> primaryProductDataSource = new LinkedHashMap<>();
        primaryProductDataSource.put("countries", List.of(targetCountry));
        if (feedLabel != null && !feedLabel.isBlank()) {
            primaryProductDataSource.put("feedLabel", feedLabel);
        }
        if (contentLanguage != null && !contentLanguage.isBlank()) {
            primaryProductDataSource.put("contentLanguage", contentLanguage);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", displayName);
        payload.put("primaryProductDataSource", primaryProductDataSource);
        log.info("Google Merchant: creating data source merchantId={}, clientEmail={}, sessionId={}, displayName={}, feedLabel={}, contentLanguage={}, targetCountry={}",
                merchantId, session.clientEmail(), session.sessionId(), displayName, feedLabel, contentLanguage, targetCountry);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://merchantapi.googleapis.com/datasources/v1/accounts/{merchantId}/dataSources", merchantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + session.accessToken())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            log.info("Google Merchant: data source create succeeded merchantId={}, clientEmail={}, sessionId={}, responseKeys={}, dataSourceName={}",
                    merchantId,
                    session.clientEmail(),
                    session.sessionId(),
                    responseKeys(response),
                    string(response == null ? null : response.get("name")));
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException ex) {
            throw merchantException("Google Merchant data source creation failed.", ex, context(
                    "merchant_id", merchantId,
                    "client_email", session.clientEmail(),
                    "session_id", session.sessionId(),
                    "display_name", displayName,
                    "feed_label", feedLabel,
                    "content_language", contentLanguage,
                    "target_country", targetCountry
            ));
        }
    }

    public Map<String, Object> insertProductInput(
            GoogleMerchantSession session,
            String merchantId,
            String dataSourceName,
            Map<String, Object> productInput
    ) {
        String uri = UriComponentsBuilder
                .fromHttpUrl("https://merchantapi.googleapis.com/products/v1/accounts/{merchantId}/productInputs:insert")
                .queryParam("dataSource", dataSourceName)
                .buildAndExpand(merchantId)
                .toUriString();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + session.accessToken())
                    .body(productInput)
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException ex) {
            throw merchantException("Google Merchant product sync failed.", ex, context(
                    "merchant_id", merchantId,
                    "client_email", session.clientEmail(),
                    "session_id", session.sessionId(),
                    "data_source", dataSourceName,
                    "offer_id", string(productInput.get("offerId"))
            ));
        }
    }

    public void deleteProductInput(
            GoogleMerchantSession session,
            String productInputName,
            String dataSourceName
    ) {
        String uri = UriComponentsBuilder
                .fromHttpUrl("https://merchantapi.googleapis.com/products/v1/" + productInputName)
                .queryParam("dataSource", dataSourceName)
                .toUriString();
        try {
            restClient.delete()
                    .uri(uri)
                    .header("Authorization", "Bearer " + session.accessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return;
            }
            throw merchantException("Google Merchant product removal failed.", ex, context(
                    "client_email", session.clientEmail(),
                    "session_id", session.sessionId(),
                    "product_input_name", productInputName,
                    "data_source", dataSourceName
            ));
        }
    }

    private ServiceAccountCredentials parseCredentials(String rawJson) {
        try {
            Map<String, Object> data = objectMapper.readValue(rawJson, new TypeReference<>() {
            });
            String clientEmail = string(data.get("client_email"));
            String privateKey = string(data.get("private_key"));
            String privateKeyId = string(data.get("private_key_id"));
            String projectId = string(data.get("project_id"));
            String tokenUri = string(data.get("token_uri"));
            if (clientEmail == null || clientEmail.isBlank() || privateKey == null || privateKey.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google service account JSON must include client_email and private_key.");
            }
            return new ServiceAccountCredentials(
                    clientEmail,
                    privateKey,
                    privateKeyId,
                    projectId,
                    tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google service account JSON could not be parsed.");
        }
    }

    private String signedAssertion(ServiceAccountCredentials credentials) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of(
                    "alg", "RS256",
                    "typ", "JWT"
            );
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", credentials.clientEmail());
            claims.put("scope", SCOPE);
            claims.put("aud", credentials.tokenUri());
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.plusSeconds(3600).getEpochSecond());
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String encodedHeader = encoder.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedClaims = encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
            String unsignedJwt = encodedHeader + "." + encodedClaims;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(credentials.privateKeyPem()));
            signature.update(unsignedJwt.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = encoder.encodeToString(signature.sign());
            return unsignedJwt + "." + encodedSignature;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google service account private key is invalid.");
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String normalized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Google service account private key is invalid.");
        }
    }

    private ApiException merchantException(String fallback, RestClientResponseException ex, Map<String, Object> context) {
        String detail = fallback + " HTTP " + ex.getStatusCode().value() + ".";
        String body = ex.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
                Object error = parsed.get("error");
                if (error instanceof Map<?, ?> errorMap) {
                    Object message = errorMap.get("message");
                    if (message != null) {
                        detail = detail + " " + String.valueOf(message);
                    } else {
                        detail = detail + " Response: " + abbreviateJson(errorMap);
                    }
                } else {
                    detail = detail + " Response: " + abbreviate(body);
                }
            } catch (Exception ignored) {
                detail = detail + " Response: " + abbreviate(body);
            }
        }
        log.error("Google Merchant API error: {} context={} response={}",
                fallback,
                abbreviateJson(context),
                abbreviate(body),
                ex);
        return new ApiException(HttpStatus.BAD_GATEWAY, "Google Merchant request failed", detail.trim());
    }

    private Map<String, Object> context(Object... entries) {
        Map<String, Object> context = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            Object key = entries[i];
            if (key == null) {
                continue;
            }
            context.put(String.valueOf(key), entries[i + 1]);
        }
        return context;
    }

    private String abbreviateJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return abbreviate(objectMapper.writeValueAsString(value));
        } catch (Exception ignored) {
            return abbreviate(String.valueOf(value));
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 1200 ? normalized : normalized.substring(0, 1200) + "…";
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String responseKeys(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "[]";
        }
        return new ArrayList<>(response.keySet()).toString();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String abbreviateKeyId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 12 ? value : value.substring(0, 12) + "…";
    }

    private record ServiceAccountCredentials(
            String clientEmail,
            String privateKeyPem,
            String privateKeyId,
            String projectId,
            String tokenUri
    ) {
    }

    public record GoogleMerchantSession(String accessToken, String clientEmail) {
        public UUID sessionId() {
            return UUID.nameUUIDFromBytes((clientEmail + ":" + accessToken).getBytes(StandardCharsets.UTF_8));
        }
    }
}
