package com.vendorfraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controlled seed mechanism. When {@code enabled} is false - the default -
 * no user is ever created automatically. There is no public registration
 * endpoint anywhere in the application.
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(boolean enabled, String defaultPassword) {
}
