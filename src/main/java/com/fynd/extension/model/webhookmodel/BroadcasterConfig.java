package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BroadcasterConfig {

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("queue")
    private String queue;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("event_bridge_name")
    private String eventBridgeName;

    @JsonProperty("created_on")
    private String createdOn;
}
