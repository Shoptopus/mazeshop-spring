package com.shoptopus.core.devseed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.dev.test-data")
public class DevTestDataProperties {

    private boolean enabled;
    private String dumpPath;
    private String imagesPath;
    private String seedKey = "mysql-dump-2026-04-04";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDumpPath() {
        return dumpPath;
    }

    public void setDumpPath(String dumpPath) {
        this.dumpPath = dumpPath;
    }

    public String getImagesPath() {
        return imagesPath;
    }

    public void setImagesPath(String imagesPath) {
        this.imagesPath = imagesPath;
    }

    public String getSeedKey() {
        return seedKey;
    }

    public void setSeedKey(String seedKey) {
        this.seedKey = seedKey;
    }
}
