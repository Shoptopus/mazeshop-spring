package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminUserAvatarService {

    private final UserRepository userRepository;

    public AdminUserAvatarService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateAvatar(String email, Map<String, Object> body) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found"));
        Map<String, Object> avatar = new LinkedHashMap<>();
        if (body != null && body.get("avatar") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> avatar.put(String.valueOf(key), value));
        } else if (body != null && body.get("path") != null) {
            avatar.put("path", String.valueOf(body.get("path")));
        } else if (body != null && body.get("url") != null) {
            avatar.put("url", String.valueOf(body.get("url")));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The avatar field is required.");
        }
        user.setAvatar(avatar);
        userRepository.save(user);
        return Map.of(
                "id", user.getId(),
                "avatar", user.getAvatar()
        );
    }
}
