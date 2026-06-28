package com.nextdigital.oncall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;

@Configuration
public class OpenApiConfig {

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
                                                                .email("oncall@nextdigital.com")
                                                                .url("https://nextdigital.com"))
                                                .license(new License()
                                                                .name("Apache 2.0")
                                                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                                .servers(List.of(
                                                new Server()
                                                                .url("http://localhost:8080")
                                                                .description("Servidor de desarrollo local"),
                                                new Server()
                                                                .url("https://api.oncall.nextdigital.com")
                                                                .description("Servidor de producción")));
        }

}
