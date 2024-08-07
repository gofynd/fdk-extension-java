package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "ext")
@Component
@Getter
@Setter
public class ExtensionProperties {

    private String apiKey;

    private String apiSecret;

    private List<String> scopes;

    private String baseUrl;

    private String accessMode;

    private String cluster;

    private WebhookProperties webhook;

    // Default cluster value
    private static final String DEFAULT_CLUSTER = "https://api.fynd.com";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = Optional.ofNullable(baseUrl).orElse(null);
    }

   public WebhookProperties getWebhook() {
        return webhook;
   }

   public void setWebhook(WebhookProperties webhook) {
        this.webhook = Optional.ofNullable(webhook).orElse(null);
   }

   public List<String> getScopes() {
        return scopes;
   }

   public void setScopes(List<String> scopes) {
        this.scopes = Optional.ofNullable(scopes).orElse(null);
   }

    public String getCluster() {
        return Optional.ofNullable(cluster).orElse(DEFAULT_CLUSTER);
    }

    public void setCluster(String cluster) {
        this.cluster = Optional.ofNullable(cluster).orElse(DEFAULT_CLUSTER);
    }
}
