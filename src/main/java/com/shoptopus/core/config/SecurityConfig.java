package com.shoptopus.core.config;

import com.shoptopus.core.auth.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(request -> isFrontendRequest(request)).permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/admin-ui/auth/login",
                                "/api/admin-ui/auth/forgot-password",
                                "/api/admin-ui/auth/reset-password",
                                "/api/admin-ui/shop-settings/email/gmail/callback",
                                "/media/**",
                                "/api/auth/register-by-invite/**",
                                "/api/auth/verify/resend",
                                "/api/auth/verify/**",
                                "/api/auth/login/*",
                                "/api/auth/login/*/callback",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/me",
                                "/api/public/**",
                                "/api/products/**",
                                "/api/product/**",
                                "/api/product_categories/**",
                                "/api/product_category/**",
                                "/api/product_tags/**",
                                "/api/product_tag/**",
                                "/api/product_attributes/**",
                                "/api/product_attribute/**",
                                "/api/cart/**",
                                "/api/checkout/**",
                                "/api/payment/**",
                                "/api/banners/**",
                                "/api/ratings/**",
                                "/api/home",
                                "/api/storefront/**",
                                "/api/pages",
                                "/api/pages/**",
                                "/api/meta",
                                "/api/shared-options",
                                "/api/download-paid-file/**",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:4173,http://localhost:4173}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            var body = new LinkedHashMap<String, Object>();
            body.put("user_message", "Unauthenticated");
            body.put("developer_message", "Authentication required");
            body.put("error_code", 1101);
            objectMapper.writeValue(response.getOutputStream(), body);
        };
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private boolean isFrontendRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return true;
        }
        String normalized = uri.toLowerCase(Locale.ROOT);
        return !normalized.startsWith("/api/")
                && !normalized.equals("/api")
                && !normalized.startsWith("/actuator/")
                && !normalized.equals("/actuator")
                && !normalized.startsWith("/swagger-ui/")
                && !normalized.equals("/swagger-ui")
                && !normalized.startsWith("/api/docs")
                && !normalized.startsWith("/media/")
                && !normalized.equals("/media");
    }
}
