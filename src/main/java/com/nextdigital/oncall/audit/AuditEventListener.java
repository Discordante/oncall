package com.nextdigital.oncall.audit;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.event.spi.PostCollectionRecreateEvent;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostCollectionRemoveEvent;
import org.hibernate.event.spi.PostCollectionRemoveEventListener;
import org.hibernate.event.spi.PostCollectionUpdateEvent;
import org.hibernate.event.spi.PostCollectionUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import com.nextdigital.oncall.config.AuditProperties;
import com.nextdigital.oncall.entity.AuditLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Listener de eventos de Hibernate que registra cada INSERT/UPDATE/DELETE en la
 * tabla única {@code audit_logs}, dentro de la misma transacción que el cambio
 * de negocio (fail-closed: si no se puede registrar, la transacción revierte).
 *
 * Se registra en la EventEngine de Hibernate 7 vía el SPI
 * {@link AuditEventEngineContributor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener,
        PostDeleteEventListener, PostCollectionRecreateEventListener, PostCollectionRemoveEventListener,
        PostCollectionUpdateEventListener {

    private static final String AUDIT_TABLE = "audit_logs";

    private final AuditProperties properties;
    private final AuditActorProvider actorProvider;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Entidades
    // ------------------------------------------------------------------

    @Override
    public void onPostInsert(PostInsertEvent event) {
        EntityPersister persister = event.getPersister();
        String entityType = persister.getTableName();
        if (!shouldAudit(entityType)) {
            return;
        }
        write((SessionImplementor) event.getSession(), "CREATE", entityType,
                String.valueOf(event.getId()), snapshot(persister, event.getState()));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        EntityPersister persister = event.getPersister();
        String entityType = persister.getTableName();
        if (!shouldAudit(entityType)) {
            return;
        }
        Map<String, Object> diff = diff(persister, event.getOldState(), event.getState());
        if (diff.isEmpty()) {
            return;
        }
        write((SessionImplementor) event.getSession(), "UPDATE", entityType,
                String.valueOf(event.getId()), diff);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        EntityPersister persister = event.getPersister();
        String entityType = persister.getTableName();
        if (!shouldAudit(entityType)) {
            return;
        }
        write((SessionImplementor) event.getSession(), "DELETE", entityType,
                String.valueOf(event.getId()), snapshot(persister, event.getDeletedState()));
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    // ------------------------------------------------------------------
    // Colecciones many-to-many (miembros de grupos, aplicaciones)
    // Nota Hibernate 7: los eventos de colección no exponen el elemento
    // afectado; se registra el estado completo de la colección.
    // ------------------------------------------------------------------

    @Override
    public void onPostRecreateCollection(PostCollectionRecreateEvent event) {
        SessionImplementor session = event.getSession();
        CollectionPersister persister = event.getCollectionPersister();
        String entityType = collectionOwnerTable(persister);
        if (entityType == null) {
            return;
        }
        Object ownerId = collectionOwnerId(event);
        if (ownerId == null) {
            return;
        }
        String field = fieldName(persister.getRole());
        Map<String, Object> fieldChanges = new LinkedHashMap<>();
        fieldChanges.put("old", safeSnapshotIds(event.getCollection(), persister));
        fieldChanges.put("new", safeValueIds(event.getCollection().getValue(), session));
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(field, fieldChanges);
        write(session, "UPDATE", entityType, String.valueOf(ownerId), changes);
    }

    @Override
    public void onPostRemoveCollection(PostCollectionRemoveEvent event) {
        SessionImplementor session = event.getSession();
        CollectionPersister persister = event.getCollectionPersister();
        String entityType = collectionOwnerTable(persister);
        if (entityType == null) {
            return;
        }
        // El valor de una colección en proceso de borrado no es inicializable:
        // se usa el snapshot cargado (que puede no estar disponible en wrappers
        // no inicializados → lista vacía).
        List<String> removed = safeSnapshotIds(event.getCollection(), persister);
        if (removed.isEmpty()) {
            return;
        }
        Object ownerId = collectionOwnerId(event);
        if (ownerId == null) {
            return;
        }
        String field = fieldName(persister.getRole());
        Map<String, Object> fieldChanges = new LinkedHashMap<>();
        fieldChanges.put("removed", removed);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(field, fieldChanges);
        write(session, "UPDATE", entityType, String.valueOf(ownerId), changes);
    }

    @Override
    public void onPostUpdateCollection(PostCollectionUpdateEvent event) {
        SessionImplementor session = event.getSession();
        CollectionPersister persister = event.getCollectionPersister();
        String entityType = collectionOwnerTable(persister);
        if (entityType == null) {
            return;
        }
        List<String> current = safeValueIds(event.getCollection().getValue(), session);
        if (current.isEmpty()) {
            return;
        }
        Object ownerId = collectionOwnerId(event);
        if (ownerId == null) {
            return;
        }
        String field = fieldName(persister.getRole());
        Map<String, Object> fieldChanges = new LinkedHashMap<>();
        fieldChanges.put("current", current);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(field, fieldChanges);
        write(session, "UPDATE", entityType, String.valueOf(ownerId), changes);
    }

    /** Id del propietario de la colección, con degradación segura. */
    private Object collectionOwnerId(org.hibernate.event.spi.AbstractCollectionEvent event) {
        Object key = event.getCollection().getKey();
        if (key != null) {
            return key;
        }
        return event.getAffectedOwnerIdOrNull();
    }

    /**
     * Snapshot de la colección; ante estados internos raros se degrada a lista
     * vacía.
     */
    private List<String> safeSnapshotIds(PersistentCollection<?> collection, CollectionPersister persister) {
        try {
            return idsFromSnapshot(collection.getSnapshot(persister));
        } catch (Exception e) {
            log.debug("Snapshot no disponible para la colección {}: {}", persister.getRole(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Valor actual de la colección; ante estados internos raros se degrada a lista
     * vacía.
     */
    private List<String> safeValueIds(Object value, SessionImplementor session) {
        try {
            return idsFromValue(value, session);
        } catch (Exception e) {
            log.debug("Valor no disponible para la colección: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Tabla del propietario si la colección es many-to-many y auditable; null en
     * caso contrario.
     */
    private String collectionOwnerTable(CollectionPersister persister) {
        if (persister == null || !persister.isManyToMany()) {
            return null;
        }
        String entityType = persister.getOwnerEntityPersister().getTableName();
        return shouldAudit(entityType) ? entityType : null;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void write(SessionImplementor session, String action, String entityType, String entityId,
            Map<String, Object> changes) {
        var actor = actorProvider.current();
        var info = actorProvider.requestInfo();
        try {
            var row = AuditLog.builder()
                    .occurredAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .changes(objectMapper.writeValueAsString(changes))
                    .personId(actor != null ? actor.personId() : null)
                    .actorEmail(actor != null ? actor.email() : null)
                    .actorName(actor != null ? actor.name() : null)
                    .ipAddress(info != null ? info.ip() : null)
                    .httpMethod(info != null ? info.method() : null)
                    .requestPath(info != null ? info.path() : null)
                    .userAgent(info != null ? info.userAgent() : null)
                    .build();

            // Se inserta por JDBC directo en la conexión de la transacción:
            // persistir vía JPA dentro de un evento de Hibernate modificaría el
            // ActionQueue mientras se está iterando (ConcurrentModificationException),
            // y las sincronizaciones de Spring no cubren el orden flush→commit.
            session.doWork(connection -> insertAuditLog(connection, row));

            log.debug("Auditoría: {} {}#{} por {}", action, entityType, entityId,
                    actor != null ? actor.email() : "SYSTEM");
        } catch (Exception e) {
            // Fail-closed: sin registro de auditoría no debe haber cambio de negocio.
            throw new IllegalStateException(
                    "No se pudo registrar la auditoría para " + entityType + "#" + entityId, e);
        }
    }

    private void insertAuditLog(Connection connection, AuditLog row) throws SQLException {
        String sql = """
                INSERT INTO audit_logs (occurred_at, action, entity_type, entity_id, changes,
                                        person_id, actor_email, actor_name, ip_address, http_method,
                                        request_path, user_agent)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, row.getOccurredAt());
            statement.setString(2, row.getAction());
            statement.setString(3, row.getEntityType());
            statement.setString(4, row.getEntityId());
            statement.setString(5, row.getChanges());
            statement.setObject(6, row.getPersonId());
            statement.setString(7, row.getActorEmail());
            statement.setString(8, row.getActorName());
            statement.setString(9, row.getIpAddress());
            statement.setString(10, row.getHttpMethod());
            statement.setString(11, row.getRequestPath());
            statement.setString(12, row.getUserAgent());
            statement.executeUpdate();
        }
    }

    private boolean shouldAudit(String entityType) {
        return properties.isEnabled()
                && !AUDIT_TABLE.equals(entityType)
                && !properties.getExcludedEntities().contains(entityType);
    }

    private Map<String, Object> snapshot(EntityPersister persister, Object[] state) {
        Map<String, Object> map = new LinkedHashMap<>();
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        for (int i = 0; i < names.length; i++) {
            if (types[i].isAssociationType()) {
                continue;
            }
            map.put(names[i], clean(state[i]));
        }
        return map;
    }

    private Map<String, Object> diff(EntityPersister persister, Object[] oldState, Object[] newState) {
        Map<String, Object> map = new LinkedHashMap<>();
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        for (int i = 0; i < names.length; i++) {
            if (types[i].isAssociationType()) {
                continue;
            }
            Object oldValue = clean(oldState[i]);
            Object newValue = clean(newState[i]);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("old", oldValue);
                entry.put("new", newValue);
                map.put(names[i], entry);
            }
        }
        return map;
    }

    private Object clean(Object value) {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private String fieldName(String role) {
        int idx = role.lastIndexOf('.');
        return idx >= 0 ? role.substring(idx + 1) : role;
    }

    private List<String> idsFromSnapshot(Object snapshot) {
        List<String> ids = new ArrayList<>();
        if (snapshot instanceof Map<?, ?> map) {
            map.keySet().forEach(k -> ids.add(String.valueOf(k)));
        } else if (snapshot instanceof Iterable<?> iterable) {
            iterable.forEach(e -> ids.add(String.valueOf(e)));
        } else if (snapshot != null) {
            ids.add(String.valueOf(snapshot));
        }
        return ids;
    }

    private List<String> idsFromValue(Object value, SessionImplementor session) {
        List<String> ids = new ArrayList<>();
        if (value instanceof PersistentCollection<?> persistent && !persistent.isDirectlyAccessible()) {
            // Colección perezosa sin inicializar (p. ej. en proceso de borrado).
            return ids;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(e -> ids.add(idOf(e, session)));
        } else if (value != null) {
            ids.add(String.valueOf(value));
        }
        return ids;
    }

    private String idOf(Object element, SessionImplementor session) {
        try {
            Object id = session.getIdentifier(element);
            if (id != null) {
                return String.valueOf(id);
            }
        } catch (Exception ignored) {
            // Elemento no gestionado: se registra su representación textual.
        }
        return String.valueOf(element);
    }
}
