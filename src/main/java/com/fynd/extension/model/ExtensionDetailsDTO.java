package com.fynd.extension.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionDetailsDTO {

    @JsonProperty("name")
    String name;

    @JsonProperty("extension_type")
    String extensionType;

    @JsonProperty("base_url")
    String baseUrl;

    @JsonProperty("scope")
    List<String> scope;
}
