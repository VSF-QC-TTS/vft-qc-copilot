package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.admin.seed")
public record AdminSeedProperties(
    String email, String password, String avatarUrl, String displayName) {}
