package com.nextdigital.oncall.audit;

import org.hibernate.event.spi.EventEngineContributions;
import org.hibernate.event.spi.EventEngineContributor;
import org.hibernate.event.spi.EventType;

import lombok.extern.slf4j.Slf4j;

/**
 * Contribuidor SPI de Hibernate 7 (descubierto por ServiceLoader desde
 * {@code META-INF/services/org.hibernate.event.spi.EventEngineContributor})
 * que registra {@link AuditEventListener} en la EventEngine.
 *
 * Como Hibernate instancia el contribuidor por reflexión (sin Spring), la
 * instancia del listener se publica estáticamente desde
 * {@link AuditHibernateConfig}, cuya inicialización previa al
 * EntityManagerFactory fuerza
 * {@link AuditEntityManagerFactoryDependsOnPostProcessor}.
 */
@Slf4j
public class AuditEventEngineContributor implements EventEngineContributor {

    private static volatile AuditEventListener listener;

    public static void register(AuditEventListener auditEventListener) {
        listener = auditEventListener;
    }

    @Override
    public void contribute(EventEngineContributions contributions) {
        AuditEventListener current = listener;
        if (current == null) {
            log.warn("AuditEventListener no estaba disponible al construir la EventEngine; "
                    + "la auditoría quedará sin registrar");
            return;
        }
        contributions.configureListeners(EventType.POST_INSERT, group -> group.appendListener(current));
        contributions.configureListeners(EventType.POST_UPDATE, group -> group.appendListener(current));
        contributions.configureListeners(EventType.POST_DELETE, group -> group.appendListener(current));
        contributions.configureListeners(EventType.POST_COLLECTION_RECREATE,
                group -> group.appendListener(current));
        contributions.configureListeners(EventType.POST_COLLECTION_REMOVE,
                group -> group.appendListener(current));
        contributions.configureListeners(EventType.POST_COLLECTION_UPDATE,
                group -> group.appendListener(current));
    }
}
