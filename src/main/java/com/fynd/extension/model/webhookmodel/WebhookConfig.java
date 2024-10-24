package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookConfig {

    @JsonProperty("notification_email")
    private String notificationEmail;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private SubscriberStatus status;

    @JsonProperty("association")
    private Association association;

    @JsonProperty("event_map")
    private Map<String, EventMap> eventMap;

}
