package lab.clickstream.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import lab.clickstream.model.ClickEvent;
import lab.clickstream.model.ClickCount;
import lab.clickstream.component.ClickEventRowMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ClickEventRepository {
    private final JdbcTemplate jdbc;
    private final ClickEventRowMapper mapper;

    public ClickEventRepository(JdbcTemplate jdbc, ClickEventRowMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean insert(ClickEvent event) {
        int rows = jdbc.update("""
                INSERT INTO click_events
                    (event_id, schema_version, occurred_at, session_id, page, element_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.schemaVersion(), event.occurredAt(),
                event.sessionId(), event.page(), event.elementId());
        return rows == 1;
    }

    public Optional<ClickEvent> findById(UUID eventId) {
        var events = jdbc.query("""
                SELECT event_id, schema_version, occurred_at, session_id, page, element_id
                FROM click_events WHERE event_id = ?
                """, mapper, eventId);
        return events.stream().findFirst();
    }
    public List<ClickCount> counts() {
        return jdbc.query("SELECT element_id, COUNT(*) AS count FROM click_events GROUP BY element_id ORDER BY element_id",
                (rs, row) -> new ClickCount(rs.getString("element_id"), rs.getLong("count")));
    }
}
