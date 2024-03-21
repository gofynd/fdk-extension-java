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
public class Page{







    @JsonProperty("item_total")
    private Integer itemTotal;




    @JsonProperty("next_id")
    private String nextId;




    @JsonProperty("has_previous")
    private Boolean hasPrevious;




    @JsonProperty("has_next")
    private Boolean hasNext;




    @JsonProperty("current")
    private Integer current;




    @JsonProperty("type")
    private String type;




    @JsonProperty("size")
    private Integer size;



}
