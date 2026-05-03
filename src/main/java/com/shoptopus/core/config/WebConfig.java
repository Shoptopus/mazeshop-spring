package com.shoptopus.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String mediaStoragePath;

    public WebConfig(@Value("${app.media.storage-path}") String mediaStoragePath) {
        this.mediaStoragePath = mediaStoragePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(mediaStoragePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");

        registry.addResourceHandler("/studio", "/studio/**")
                .addResourceLocations("classpath:/static/studio/")
                .resourceChain(true)
                .addResolver(spaResourceResolver(Set.of(), "index.html"));

        registry.addResourceHandler("/", "/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(spaResourceResolver(Set.of("api", "media", "studio", "actuator"), "index.html"));
    }

    private PathResourceResolver spaResourceResolver(Set<String> excludedPrefixes, String indexFile) {
        return new PathResourceResolver() {
            @Override
            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                String normalizedPath = resourcePath == null ? "" : resourcePath.replaceFirst("^/+", "");
                Resource requested = location.createRelative(normalizedPath);
                if (requested.exists() && requested.isReadable()) {
                    return requested;
                }
                if (!shouldServeSpaIndex(normalizedPath, excludedPrefixes)) {
                    return null;
                }
                Resource index = location.createRelative(indexFile);
                return index.exists() && index.isReadable() ? index : null;
            }
        };
    }

    private boolean shouldServeSpaIndex(String resourcePath, Set<String> excludedPrefixes) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return true;
        }
        if (resourcePath.contains(".")) {
            return false;
        }
        return excludedPrefixes.stream().noneMatch(prefix ->
                resourcePath.equals(prefix) || resourcePath.startsWith(prefix + "/"));
    }
}
