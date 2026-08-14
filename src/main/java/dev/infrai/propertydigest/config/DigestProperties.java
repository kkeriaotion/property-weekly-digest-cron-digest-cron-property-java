package dev.infrai.propertydigest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("digest")
public record DigestProperties(String recipient, String propertyName, String taskUrl, String cronExpr) {}
