package com.nextdigital.oncall.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Custom OIDC user service that validates the email domain BEFORE the
 * OAuth2 login completes. Rejects any Google account whose email does
 * not belong to the allowed corporate domain.
 *
 * This gives the user a clear error on the login page instead of letting
 * the redirect to the frontend succeed and then getting a 401 on the
 * first API call.
 */
@Component
public class CustomOAuth2UserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String ALLOWED_DOMAIN = "@nextdigital.es";

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_domain",
                            "Google account does not have an email address.", null));
        }

        if (!email.endsWith(ALLOWED_DOMAIN)) {
            String domain = email.substring(email.indexOf('@'));
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_domain",
                            "Email domain '" + domain + "' is not allowed. "
                                    + "Only " + ALLOWED_DOMAIN + " accounts are permitted.",
                            null));
        }

        return oidcUser;
    }
}
