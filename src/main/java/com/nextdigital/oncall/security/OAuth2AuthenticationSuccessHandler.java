package com.nextdigital.oncall.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles a successful Google OAuth2 login.
 *
 * Extracts the Google-issued ID token from the authenticated principal and
 * redirects the browser to the frontend SPA, passing the token as a query
 * parameter so the frontend can store it and use it for subsequent API calls.
 *
 * The OAuth2 login session is invalidated immediately after the redirect
 * because the frontend will use stateless Bearer authentication from this
 * point onward.
 */
@Slf4j
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            @Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String idToken = oidcUser.getIdToken().getTokenValue();
            String redirectUrl = frontendUrl
                    + "/oauth2/redirect?token="
                    + URLEncoder.encode(idToken, StandardCharsets.UTF_8);

            log.info("OAuth2 login success for {} — redirecting to frontend", oidcUser.getEmail());

            // Invalidate the temporary OAuth2 session — from here on the
            // frontend uses stateless Bearer authentication.
            request.getSession().invalidate();

            getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        } else {
            log.error("Unexpected principal type: {}",
                    authentication.getPrincipal().getClass().getName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unexpected authentication principal type");
        }
    }
}
