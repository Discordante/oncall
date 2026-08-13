package com.nextdigital.oncall.audit;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Publica el bean {@link AuditEventListener} en el SPI
 * {@link AuditEventEngineContributor} de Hibernate 7. El orden de
 * inicialización (antes de que se construya el EntityManagerFactory) lo
 * garantiza {@link AuditEntityManagerFactoryDependsOnPostProcessor}.
 */
@Configuration("auditHibernateConfig")
@RequiredArgsConstructor
public class AuditHibernateConfig {

    private final AuditEventListener listener;

    @PostConstruct
    void registerContributorListener() {
        AuditEventEngineContributor.register(listener);
    }
}
