package com.shoptopus.core.social;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SocialProviderConfig {
    private String provider;
    private String label;
    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private List<String> scopes = new ArrayList<>();
    private String userIdAttribute;
    private String emailAttribute;
    private String firstNameAttribute;
    private String lastNameAttribute;
    private String nameAttribute;
}
