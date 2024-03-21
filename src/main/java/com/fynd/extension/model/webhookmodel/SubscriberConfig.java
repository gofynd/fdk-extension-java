package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/*
    Model: SubscriberConfig
*/
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriberConfig{



    @JsonProperty("id")
    private Integer id;




    @JsonProperty("name")
    private String name;




    @JsonProperty("webhook_url")
    private String webhookUrl;




    @JsonProperty("association")
    private Association association;




    @JsonProperty("custom_headers")
    private Object customHeaders;




    @JsonProperty("status")
    private SubscriberStatus status;




    @JsonProperty("email_id")
    private String emailId;




    @JsonProperty("auth_meta")
    private AuthMeta authMeta;




    @JsonProperty("event_id")
    private List<Integer> eventId;



}