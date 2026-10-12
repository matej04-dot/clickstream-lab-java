package lab.clickstream.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClickCount(@JsonProperty("element_id") String elementId, long count) {}
