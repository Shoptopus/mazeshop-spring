package com.shoptopus.core.engagement.dto;

import java.util.UUID;

public record BannerResponse(
        UUID id,
        String name,
        String slug,
        String image,
        String video,
        String buttonText,
        String content,
        String url,
        long clickCount
) {
}
