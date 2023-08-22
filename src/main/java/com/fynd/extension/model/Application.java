package com.fynd.extension.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {

    @JsonProperty("_id")
    private String ID;

    @JsonProperty("description")
    private String Description;

    @JsonProperty("cache_ttl")
    private int CacheTTL;

    @JsonProperty("name")
    private String Name;

    @JsonProperty("owner")
    private String Owner;

    @JsonProperty("token")
    private String Token;

    @JsonProperty("secret")
    private String Secret;

    @JsonProperty("company_id")
    private int CompanyID;

    @JsonProperty("createdAt")
    private Date CreatedAt;

    @JsonProperty("updatedAt")
    private Date UpdatedAt;

    @JsonProperty("domain")
    private Domain domain;

    @JsonProperty("website")
    private Website website;

    @JsonProperty("cors")
    private Cors cors;

    @JsonProperty("meta")
    private List<Meta> meta;


}