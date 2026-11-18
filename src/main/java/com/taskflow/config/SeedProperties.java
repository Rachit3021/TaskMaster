package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Demo data seeding (useful for local evaluation only). */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(@DefaultValue("false") boolean enabled) {
}
