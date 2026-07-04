package com.nextdigital.oncall.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for OpenAPI server URLs.
 * Allows externalizing the list of servers via application.yml
 * or environment variables.
 */
@Configuration
@ConfigurationProperties(prefix = "openapi")
@Getter
@Setter
public class OpenApiServerProperties {

    private List<ServerInfo> servers = new ArrayList<>();

    @Getter
    @Setter
    public static class ServerInfo {
        private String url;
        private String description;
    }
}
