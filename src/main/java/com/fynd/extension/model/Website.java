package com.fynd.extension.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Website {

    @JsonProperty("enabled")
    Boolean Enabled;

    @JsonProperty("basepath")
    String Basepath;
}
