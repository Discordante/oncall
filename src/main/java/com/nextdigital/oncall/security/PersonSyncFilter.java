package com.nextdigital.oncall.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nextdigital.oncall.service.PersonService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersonSyncFilter extends OncePerRequestFilter {

    private final PersonService personService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            String googleSub = jwt.getSubject();

            if (email != null && googleSub != null) {
                try {
                    personService.syncFromGoogle(email, name, googleSub);
                } catch (Exception e) {
                    log.error("Failed to sync Person from Google SSO for email {}: {}", email, e.getMessage(), e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
