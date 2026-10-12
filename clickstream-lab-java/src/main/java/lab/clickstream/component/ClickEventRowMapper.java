package lab.clickstream.component;

import lab.clickstream.model.ClickEvent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ClickEventRowMapper implements RowMapper<ClickEvent> {
    @Override
    public ClickEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ClickEvent(rs.getObject("event_id", UUID.class), rs.getInt("schema_version"),
                rs.getObject("occurred_at", OffsetDateTime.class), rs.getString("session_id"),
                rs.getString("page"), rs.getString("element_id"));
    }
}
