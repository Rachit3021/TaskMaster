package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Attachment storage settings. */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @DefaultValue("./data/uploads") String uploadDir,
        @DefaultValue("10485760") long maxFileSizeBytes,
        List<String> allowedContentTypes
) {
    public StorageProperties {
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? List.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf",
                          "text/plain", "text/csv", "application/zip")
                : List.copyOf(allowedContentTypes);
    }
}
