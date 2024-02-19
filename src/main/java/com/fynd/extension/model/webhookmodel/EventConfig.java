package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventConfig{







    @JsonProperty("id")
    private Integer id;




    @JsonProperty("event_name")
    private String eventName;




    @JsonProperty("event_type")
    private String eventType;




    @JsonProperty("event_category")
    private String eventCategory;




    @JsonProperty("subscriber_event_mapping")
    private SubscriberEventMapping subscriberEventMapping;




    @JsonProperty("event_schema")
    private HashMap<String,Object> eventSchema;




    @JsonProperty("group")
    private String group;




    @JsonProperty("version")
    private String version;




    @JsonProperty("display_name")
    private String displayName;




    @JsonProperty("description")
    private String description;




    @JsonProperty("created_on")
    private String createdOn;




    @JsonProperty("updated_on")
    private String updatedOn;



}
