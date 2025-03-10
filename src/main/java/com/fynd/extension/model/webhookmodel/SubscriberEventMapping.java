package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriberEventMapping{

    @JsonProperty("id")
    private Double id;

    @JsonProperty("event_id")
    private Double eventId;

    @JsonProperty("subscriber_id")
    private Double subscriberId;

    @JsonProperty("broadcaster_config")
    private BroadcasterConfig BroadcasterConfig;

    @JsonProperty("filters")
    private Map<String, Object> filters;

    @JsonProperty("reducer")
    private Map<String, Object> reducer;

    @JsonProperty("created_on")
    private String createdOn;

}
