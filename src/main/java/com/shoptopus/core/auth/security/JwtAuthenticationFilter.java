package com.shoptopus.core.auth.security;

import com.shoptopus.core.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AccessTokenRepository accessTokenRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            AccessTokenRepository accessTokenRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.accessTokenRepository = accessTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = header.substring(7);
        try {
            var claims = jwtService.parse(token);
            var email = claims.get("email", String.class);
            var userEntity =
                    userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
            if (userEntity != null
                    && accessTokenRepository.existsByTokenAndAccessableTypeAndAccessableId(
                            token, "User", userEntity.getId().toString())
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                var principal =
                        User.withUsername(userEntity.getEmail())
                                .password(
                                        userEntity.getPassword() == null
                                                ? ""
                                                : userEntity.getPassword())
                                .authorities("ROLE_USER")
                                .build();
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("user_id", userEntity.getId());
                details.put("remote_address", request.getRemoteAddr());
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
        }

        filterChain.doFilter(request, response);
    }
}
