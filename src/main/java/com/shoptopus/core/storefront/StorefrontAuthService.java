package com.shoptopus.core.storefront;

import com.shoptopus.core.auth.AuthService;
import com.shoptopus.core.auth.dto.AuthPayloadDto;
import com.shoptopus.core.social.SocialLoginService;
import com.shoptopus.core.social.SocialProviderSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StorefrontAuthService {

    private final AuthService authService;
    private final SocialProviderSettingsService socialProviderSettingsService;
    private final SocialLoginService socialLoginService;

    public StorefrontAuthService(
            AuthService authService,
            SocialProviderSettingsService socialProviderSettingsService,
            SocialLoginService socialLoginService
    ) {
        this.authService = authService;
        this.socialProviderSettingsService = socialProviderSettingsService;
        this.socialLoginService = socialLoginService;
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

    public Map<String, Object> meResponse(String email) {
        var auth = new LinkedHashMap<String, Object>();
        auth.put("authenticated", true);
        auth.put("token_type", "Bearer");
        auth.put("user", authService.me(email));

        var data = new LinkedHashMap<String, Object>();
        data.put("auth", auth);
        return data;
    }

    public Map<String, Object> logoutResponse() {
        var auth = new LinkedHashMap<String, Object>();
        auth.put("authenticated", false);

        var data = new LinkedHashMap<String, Object>();
        data.put("auth", auth);
        return data;
    }

    public List<Map<String, Object>> socialProviders() {
        return socialProviderSettingsService.publicProviders();
    }

    public Map<String, Object> socialProvider(String provider) {
        Map<String, Object> row = new LinkedHashMap<>(socialProviderSettingsService.publicProviders().stream()
                .filter(item -> provider.equalsIgnoreCase(String.valueOf(item.get("provider"))))
                .findFirst()
                .orElseThrow(() -> new com.shoptopus.core.common.error.ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Provider is invalid.")));
        row.put("target_url", row.get("authorize_url"));
        return row;
    }

    public URI socialAuthorizationRedirect(String provider) {
        return socialLoginService.authorizationRedirect(provider);
    }

    public URI socialCallbackRedirect(String provider, String code, String state, String error) {
        return socialLoginService.callbackRedirect(provider, code, state, error);
    }

    public Map<String, Object> socialLoginCompletion(String token) {
        return socialLoginService.completeLogin(token);
    }
}
