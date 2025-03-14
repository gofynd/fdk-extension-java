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
public class SubscriberConfigContainer {

    @JsonProperty("rest")
    private SubscriberResponse rest ;

    @JsonProperty("kafka")
    private SubscriberResponse kafka;

    @JsonProperty("pub_sub")
    private SubscriberResponse pubSub;

    @JsonProperty("sqs")
    private SubscriberResponse sqs;

    @JsonProperty("event_bridge")
    private SubscriberResponse eventBridge;

    @JsonProperty("temporal")
    private SubscriberResponse temporal;

}
