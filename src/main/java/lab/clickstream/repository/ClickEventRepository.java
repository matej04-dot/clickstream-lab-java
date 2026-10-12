package lab.clickstream.repository;

import lab.clickstream.model.ClickCount;
import lab.clickstream.model.ClickEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEventEntity, UUID> {
    @Query("""
            select new lab.clickstream.model.ClickCount(e.elementId, count(e))
            from ClickEventEntity e
            group by e.elementId
            order by e.elementId
            """)
    List<ClickCount> counts();
}
