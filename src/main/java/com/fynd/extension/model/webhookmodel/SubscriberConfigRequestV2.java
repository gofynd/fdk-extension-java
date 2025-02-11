package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriberConfigRequestV2{







    @JsonProperty("id")
    private Integer id;




    @JsonProperty("name")
    private String name;




    @JsonProperty("webhook_url")
    private String webhookUrl;




    @JsonProperty("provider")
    private String provider;




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




    @JsonProperty("events")
    private List<Events> events;


    @JsonProperty("type")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String type;



}
