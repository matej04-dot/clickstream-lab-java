package lab.clickstream;

import lab.clickstream.model.ClickEvent;
import lab.clickstream.service.ClickEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClickstreamIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired ClickEventService service;
    @Autowired MockMvc mvc;

    @Test void persistenceAndConcurrentDuplicates() throws Exception {
        var event = new ClickEvent(UUID.randomUUID(), 1, OffsetDateTime.parse("2026-10-06T12:00:00.123456+02:00"),
                "session-č", "'; DROP TABLE click_events; --", "😀gumb-č");
        try (var workers = Executors.newFixedThreadPool(8)) {
            var jobs = IntStream.range(0, 16).mapToObj(i -> (Callable<Boolean>) () -> service.store(event).inserted()).toList();
            var results = workers.invokeAll(jobs);
            int inserted = 0;
            for (var result : results) if (result.get()) inserted++;
            assertThat(inserted).isEqualTo(1);
        }
        var stored = service.findById(event.eventId()).orElseThrow();
        assertThat(stored.eventId()).isEqualTo(event.eventId());
        assertThat(stored.schemaVersion()).isEqualTo(1);
        assertThat(stored.occurredAt().toInstant()).isEqualTo(event.occurredAt().toInstant());
        assertThat(stored.sessionId()).isEqualTo(event.sessionId());
        assertThat(stored.page()).isEqualTo(event.page());
        assertThat(stored.elementId()).isEqualTo(event.elementId());
        var duplicate = service.store(new ClickEvent(event.eventId(), 1, OffsetDateTime.now(), "other", "/other", "other"));
        assertThat(duplicate.inserted()).isFalse();
        assertThat(duplicate.event()).isEqualTo(stored);
        assertThat(service.findById(UUID.randomUUID())).isEmpty();
        assertThat(service.counts()).hasSize(1);
        assertThat(service.counts().getFirst().count()).isEqualTo(1);
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(post("/events/click").contentType("application/json").content(ClickEventControllerTest.JSON))
                .andExpect(status().isCreated());
        mvc.perform(get("/stats/clicks")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
