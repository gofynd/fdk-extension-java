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
public class Association{







    @JsonProperty("company_id")
    private Integer companyId;




    @JsonProperty("application_id")
    private List<String> applicationId;




    @JsonProperty("extension_id")
    private String extensionId;




    @JsonProperty("criteria")
    private String criteria;



}
