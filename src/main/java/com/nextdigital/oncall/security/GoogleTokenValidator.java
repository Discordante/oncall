package com.nextdigital.oncall.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class GoogleTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ALLOWED_DOMAIN = "@nextdigital.com";

    private final String expectedAudience;

    public GoogleTokenValidator(@Value("${google.client-id}") String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return failure("invalid_token", "Token does not contain an 'email' claim");
        }
        if (!email.endsWith(ALLOWED_DOMAIN)) {
            return failure("invalid_token",
                    "Email domain '" + email.substring(email.indexOf('@')) + "' is not allowed. "
                            + "Only @" + ALLOWED_DOMAIN.substring(1) + " accounts are permitted");
        }

        var audience = token.getAudience();
        if (audience == null || !audience.contains(expectedAudience)) {
            return failure("invalid_token",
                    "Token audience does not match the expected client ID");
        }

        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}
