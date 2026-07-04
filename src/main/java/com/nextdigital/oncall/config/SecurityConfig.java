package com.nextdigital.oncall.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.ObjectMapper;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.ValidationError;
import com.nextdigital.oncall.security.CustomOAuth2UserService;
import com.nextdigital.oncall.security.GoogleTokenValidator;
import com.nextdigital.oncall.security.OAuth2AuthenticationFailureHandler;
import com.nextdigital.oncall.security.OAuth2AuthenticationSuccessHandler;
import com.nextdigital.oncall.security.PersonSyncFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final GoogleTokenValidator googleTokenValidator;
        private final PersonSyncFilter personSyncFilter;
        private final ObjectMapper objectMapper;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
        private final OAuth2AuthenticationFailureHandler oauth2FailureHandler;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/health",
                                                                "/actuator/health",
                                                                "/swagger-ui.html",
                                                                "/swagger-ui/**",
                                                                "/api-docs/**",
                                                                "/v3/api-docs/**",
                                                                "/oauth2-redirect.html",
                                                                "/oauth2/authorization/**",
                                                                "/login/oauth2/code/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .oidcUserService(customOAuth2UserService))
                                                .successHandler(oauth2SuccessHandler)
                                                .failureHandler(oauth2FailureHandler))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .decoder(jwtDecoder())))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .csrf(csrf -> csrf.disable())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response,
                                                                authException) -> writeError(response,
                                                                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                                                                "Authentication required. Please provide a valid Google ID token."))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> writeError(response,
                                                                                HttpStatus.FORBIDDEN, "FORBIDDEN",
                                                                                "You do not have permission to access this resource.")))
                                .addFilterAfter(personSyncFilter,
                                                org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public JwtDecoder jwtDecoder() {
                NimbusJwtDecoder decoder = JwtDecoders.fromOidcIssuerLocation("https://accounts.google.com");
                var defaultValidators = JwtValidators.createDefaultWithIssuer("https://accounts.google.com");
                var delegating = new DelegatingOAuth2TokenValidator<>(
                                defaultValidators,
                                googleTokenValidator);
                decoder.setJwtValidator(delegating);
                return decoder;
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(frontendUrl));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
                        throws IOException {
                response.setStatus(status.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                                ApiResponse.error(ValidationError.of(code, message)));
        }
}
