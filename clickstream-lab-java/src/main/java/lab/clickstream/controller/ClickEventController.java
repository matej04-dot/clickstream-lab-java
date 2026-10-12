package lab.clickstream.controller;

import jakarta.validation.Valid;
import lab.clickstream.model.ClickEvent;
import lab.clickstream.model.ClickCount;
import lab.clickstream.service.ClickEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
public class ClickEventController {
    private final ClickEventService service;
    public ClickEventController(ClickEventService service) { this.service = service; }

    @PostMapping("/events/click")
    public ResponseEntity<ClickEvent> click(@Valid @RequestBody ClickEvent event) {
        var result = service.store(event);
        return ResponseEntity.status(result.inserted() ? 201 : 200)
                .header("X-Event-Inserted", Boolean.toString(result.inserted())).body(result.event());
    }

    @GetMapping("/events/click/{eventId}")
    public ResponseEntity<ClickEvent> find(@PathVariable("eventId") UUID eventId) {
        return ResponseEntity.of(service.findById(eventId));
    }

    @GetMapping("/stats/clicks")
    public ResponseEntity<List<ClickCount>> counts() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.counts());
    }
}
