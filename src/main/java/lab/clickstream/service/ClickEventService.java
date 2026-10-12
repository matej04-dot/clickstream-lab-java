package lab.clickstream.service;

import lab.clickstream.model.ClickEvent;
import lab.clickstream.model.ClickCount;
import lab.clickstream.repository.ClickEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClickEventService {
    private final ClickEventRepository repository;

    public ClickEventService(ClickEventRepository repository) { this.repository = repository; }

    @Transactional
    public StoreResult store(ClickEvent event) {
        boolean inserted = repository.insert(event);
        var stored = repository.findById(event.eventId())
                .orElseThrow(() -> new IllegalStateException("Stored event missing"));
        return new StoreResult(inserted, stored);
    }

    public Optional<ClickEvent> findById(UUID eventId) { return repository.findById(eventId); }
    public List<ClickCount> counts() { return repository.counts(); }
    public record StoreResult(boolean inserted, ClickEvent event) {}
}
