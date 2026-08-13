package com.nextdigital.oncall.audit;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.security.PersonSyncFilter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Resuelve el autor de una acción (desde el JWT de Google) y los metadatos de
 * la petición HTTP, de forma segura para contextos sin autenticación (bot de
 * Telegram, schedulers), donde devuelve {@code null} → actor SYSTEM.
 *
 * IMPORTANTE: no ejecuta consultas a BD — se ejecuta dentro de los listeners
 * de eventos de Hibernate (en pleno flush) y una query provocaría flush
 * anidados. La Person sincronizada se obtiene del atributo de petición que
 * publica {@link PersonSyncFilter}; si no está, se registra la identidad del
 * JWT.
 */
@Slf4j
@Component
public class AuditActorProvider {

    public record Actor(UUID personId, String email, String name) {
    }

    public record RequestInfo(String ip, String method, String path, String userAgent) {
    }

    public Actor current() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                return null;
            }
            var jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            if (email == null) {
                return null;
            }

            Person fromRequest = fromRequestAttribute();
            if (fromRequest != null) {
                return new Actor(fromRequest.getId(), fromRequest.getEmail(), fromRequest.getName());
            }
            // Sin Person en la petición (p. ej. durante el sync del propio
            // PersonSyncFilter): la identidad del JWT basta como actor.
            return new Actor(null, email, name);
        } catch (Exception e) {
            log.warn("No se pudo resolver el actor de auditoría: {}", e.getMessage());
            return null;
        }
    }

    public RequestInfo requestInfo() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String path = request.getRequestURI();
                if (request.getQueryString() != null) {
                    path = path + "?" + request.getQueryString();
                }
                return new RequestInfo(
                        clientIp(request),
                        request.getMethod(),
                        truncate(path, 500),
                        truncate(request.getHeader("User-Agent"), 500));
            }
        } catch (Exception e) {
            log.debug("Sin petición servlet disponible para metadatos de auditoría: {}", e.getMessage());
        }
        return null;
    }

    private Person fromRequestAttribute() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            Object attribute = sra.getRequest().getAttribute(PersonSyncFilter.PERSON_ATTRIBUTE);
            if (attribute instanceof Person person) {
                return person;
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
