package lab.clickstream.service;

import lab.clickstream.model.ClickEvent;
import lab.clickstream.model.ClickEventEntity;
import lab.clickstream.model.ClickCount;
import lab.clickstream.repository.ClickEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClickEventService {
    private final ClickEventRepository repository;
    private final TransactionTemplate insertTransaction;

    public ClickEventService(ClickEventRepository repository, PlatformTransactionManager transactions) {
        this.repository = repository;
        insertTransaction = new TransactionTemplate(transactions);
        insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StoreResult store(ClickEvent event) {
        var existing = findById(event.eventId());
        if (existing.isPresent()) return new StoreResult(false, existing.get());
        boolean inserted = true;
        try {
            insertTransaction.executeWithoutResult(status ->
                    repository.saveAndFlush(new ClickEventEntity(event)));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request may have committed the same UUID first.
            // Read only after the failed insert transaction has rolled back.
            if (!isDuplicateKey(ex)) throw ex;
            inserted = false;
        }
        var stored = findById(event.eventId())
                .orElseThrow(() -> new IllegalStateException("Stored event missing"));
        return new StoreResult(inserted, stored);
    }

    private static boolean isDuplicateKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<ClickEvent> findById(UUID eventId) {
        return repository.findById(eventId).map(ClickEventEntity::toEvent);
    }

    @Transactional(readOnly = true)
    public List<ClickCount> counts() { return repository.counts(); }

    public record StoreResult(boolean inserted, ClickEvent event) {}
}
