package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "ext")
@Component
@Getter
@Setter
public class ExtensionProperties {

    private String integration_id ;

    private String api_key ;

    private String api_secret ;

    private String scopes;

    private String base_url ;

    private String access_mode ;

    private String cluster;

    private WebhookProperties webhook;

}
