package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Browser origins allowed to call the API. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:3000", "http://localhost:5173"}) List<String> allowedOrigins
) {
}
