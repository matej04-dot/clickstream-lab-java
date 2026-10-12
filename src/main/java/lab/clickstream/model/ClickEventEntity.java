package lab.clickstream.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "click_events")
public class ClickEventEntity implements Persistable<UUID> {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;
    @Column(name = "page", nullable = false, length = 2048)
    private String page;
    @Column(name = "element_id", nullable = false, length = 128)
    private String elementId;
    @Transient
    private boolean newEntity = true;

    protected ClickEventEntity() {}

    public ClickEventEntity(ClickEvent event) {
        eventId = event.eventId();
        schemaVersion = event.schemaVersion();
        occurredAt = event.occurredAt();
        sessionId = event.sessionId();
        page = event.page();
        elementId = event.elementId();
    }

    @Override public UUID getId() { return eventId; }
    @Override public boolean isNew() { return newEntity; }

    @PostPersist
    @PostLoad
    private void markPersisted() { newEntity = false; }

    public ClickEvent toEvent() {
        return new ClickEvent(eventId, schemaVersion, occurredAt, sessionId, page, elementId);
    }
}
