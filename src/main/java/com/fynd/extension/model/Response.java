package com.fynd.extension.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class Response {
    @JsonProperty("success")
    boolean success;

    @JsonProperty("error")
    String error;

    public Response(boolean success)
    {
        this.success = success;
    }
}
