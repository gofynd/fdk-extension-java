package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventV3 {

    @JsonProperty("event_category")
    private String eventCategory;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("version")
    private String version;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("queue")
    private String queue;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("event_bridge_name")
    private String eventBridgeName;

    @JsonProperty("filters")
    private Object filters;

    @JsonProperty("reducer")
    private Object reducer;

}