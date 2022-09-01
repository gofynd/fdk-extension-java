package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "ext")
@Component
@Getter
@Setter
public class ExtensionProperties {

    private String apiKey;

    private String apiSecret;

    private String scopes;

    private String baseUrl;

    private String accessMode;

    private String cluster;

    private WebhookProperties webhook;

}
