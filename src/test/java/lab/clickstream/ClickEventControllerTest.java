package lab.clickstream;

import lab.clickstream.controller.ClickEventController;
import lab.clickstream.model.ClickEvent;
import lab.clickstream.model.ClickCount;
import lab.clickstream.service.ClickEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClickEventController.class)
class ClickEventControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ClickEventService service;
    static final UUID ID = UUID.fromString("c46de8cb-9280-4db2-9b7e-d00e7c79cb68");
    static final String JSON = """
        {"event_id":"c46de8cb-9280-4db2-9b7e-d00e7c79cb68","schema_version":1,
        "occurred_at":"2026-10-06T12:00:00+02:00","session_id":" session-1 ","page":"/products","element_id":"gumb-č"}
        """;
    static ClickEvent event() { return new ClickEvent(ID, 1, OffsetDateTime.parse("2026-10-06T12:00:00+02:00"), "session-1", "/products", "gumb-č"); }

    @Test void newAndDuplicate() throws Exception {
        when(service.store(any())).thenAnswer(call -> new ClickEventService.StoreResult(true, call.getArgument(0)));
        mvc.perform(post("/events/click").contentType("application/json").content(JSON))
                .andExpect(status().isCreated()).andExpect(header().string("X-Event-Inserted", "true"))
                .andExpect(jsonPath("$.session_id").value("session-1"));
        when(service.store(any())).thenReturn(new ClickEventService.StoreResult(false, event()));
        mvc.perform(post("/events/click").contentType("application/json").content(JSON.replace("gumb-č", "different")))
                .andExpect(status().isOk()).andExpect(header().string("X-Event-Inserted", "false"))
                .andExpect(jsonPath("$.element_id").value("gumb-č"));
    }

    @Test void invalidInputsNeverReachStorage() throws Exception {
        for (String body : List.of(JSON.replace("\"schema_version\":1", "\"schema_version\":2"),
                JSON.replace("\"schema_version\":1", "\"schema_version\":\"1\""),
                JSON.replace("\"schema_version\":1", "\"schema_version\":1.0"),
                JSON.replace("\"schema_version\":1", "\"schema_version\":true"),
                JSON.replace("+02:00", ""), JSON.replace(ID.toString(), "bad"),
                JSON.replace("\" session-1 \"", "42"), JSON.replace("\" session-1 \"", "\"   \""),
                JSON.replace("\" session-1 \"", "null"), JSON.replace("gumb-č", "x".repeat(129)),
                JSON.replace("/products", "x".repeat(2049)), JSON.replace("\"page\":\"/products\",", ""),
                JSON.replace("\"schema_version\":1", "\"extra\":1,\"schema_version\":1"), "{")) {
            mvc.perform(post("/events/click").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void unicodeLengthCountsCharacters() throws Exception {
        when(service.store(any())).thenAnswer(call -> new ClickEventService.StoreResult(true, call.getArgument(0)));
        mvc.perform(post("/events/click").contentType("application/json").content(JSON.replace("gumb-č", "😀".repeat(128))))
                .andExpect(status().isCreated());
        mvc.perform(post("/events/click").contentType("application/json").content(JSON.replace("gumb-č", "😀".repeat(129))))
                .andExpect(status().isBadRequest());
    }

    @Test void lookupAndStatistics() throws Exception {
        when(service.findById(ID)).thenReturn(Optional.of(event()));
        mvc.perform(get("/events/click/" + ID)).andExpect(status().isOk()).andExpect(jsonPath("$.event_id").value(ID.toString()));
        when(service.findById(ID)).thenReturn(Optional.empty());
        mvc.perform(get("/events/click/" + ID)).andExpect(status().isNotFound());
        mvc.perform(get("/events/click/bad")).andExpect(status().isBadRequest());
        when(service.counts()).thenReturn(List.of());
        mvc.perform(get("/stats/clicks")).andExpect(content().json("[]"));
        when(service.counts()).thenReturn(List.of(new ClickCount("gumb-č", 3)));
        mvc.perform(get("/stats/clicks")).andExpect(jsonPath("$[0].count").value(3));
    }

    @Test void databaseFailuresAreSafe() throws Exception {
        when(service.store(any())).thenThrow(new DataAccessResourceFailureException("secret password"));
        mvc.perform(post("/events/click").contentType("application/json").content(JSON))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Baza je nedostupna. Pokušajte ponovno."));
        when(service.counts()).thenThrow(new DataAccessResourceFailureException("secret"));
        mvc.perform(get("/stats/clicks")).andExpect(status().isServiceUnavailable());
        doThrow(new InvalidDataAccessResourceUsageException("secret query")).when(service).counts();
        mvc.perform(get("/stats/clicks")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Neočekivana pogreška poslužitelja."));
    }
}
