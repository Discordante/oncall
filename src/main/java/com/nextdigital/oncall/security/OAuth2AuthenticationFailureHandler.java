package com.nextdigital.oncall.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles a failed Google OAuth2 login.
 *
 * Redirects the browser back to the frontend login page with an error code
 * so the user sees a clear, translated error message instead of a blank
 * server error page.
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final String frontendUrl;

    public OAuth2AuthenticationFailureHandler(
            @Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String errorCode = mapToErrorCode(exception);
        log.warn("OAuth2 login failed — redirecting to frontend with error={}", errorCode, exception);

        String redirectUrl = frontendUrl
                + "/login?error="
                + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }

    /**
     * Maps Spring Security OAuth2 errors to short codes the frontend
     * login page can display as user-friendly messages.
     */
    private String mapToErrorCode(AuthenticationException exception) {
        String message = exception.getMessage() != null
                ? exception.getMessage().toLowerCase()
                : "";

        if (message.contains("invalid_domain") || message.contains("not allowed")) {
            return "invalid_domain";
        }
        if (message.contains("access_denied") || message.contains("user denied")) {
            return "access_denied";
        }
        return "oauth_failed";
    }
}
