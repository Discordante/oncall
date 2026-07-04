package com.nextdigital.oncall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

        private final OpenApiServerProperties serverProperties;

        public OpenApiConfig(OpenApiServerProperties serverProperties) {
                this.serverProperties = serverProperties;
        }

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Oncall API")
                                                .version("1.0.0")
                                                .description("API REST para la gestión de guardias (Oncall). "
                                                                + "Permite administrar personas, aplicaciones y grupos de guardia.")
                                                .contact(new Contact()
                                                                .name("Equipo Oncall")
                                                                .email("oncall@nextdigital.es")
                                                                .url("https://nextdigital.es"))
                                                .license(new License()
                                                                .name("Apache 2.0")
                                                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                                .servers(serverProperties.getServers().stream()
                                                .map(s -> new Server().url(s.getUrl()).description(s.getDescription()))
                                                .toList())
                                .components(new Components()
                                                .addSecuritySchemes("google-oauth2",
                                                                new SecurityScheme()
                                                                                .type(SecurityScheme.Type.OAUTH2)
                                                                                .flows(new OAuthFlows()
                                                                                                .authorizationCode(
                                                                                                                new OAuthFlow()
                                                                                                                                .authorizationUrl(
                                                                                                                                                "https://accounts.google.com/o/oauth2/v2/auth")
                                                                                                                                .tokenUrl(
                                                                                                                                                "https://oauth2.googleapis.com/token")
                                                                                                                                .scopes(new Scopes()
                                                                                                                                                .addString("openid",
                                                                                                                                                                "OpenID Connect")
                                                                                                                                                .addString("profile",
                                                                                                                                                                "Profile information")
                                                                                                                                                .addString("email",
                                                                                                                                                                "Email address"))))))
                                .addSecurityItem(new SecurityRequirement()
                                                .addList("google-oauth2"));
        }

}
