package com.fynd.extension.model;

import com.fynd.extension.error.FdkInvalidExtensionJson;
import com.fynd.extension.service.WebhookService;
import com.fynd.extension.storage.BaseStorage;
import com.sdk.common.AccessToken;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class Extension {

    ExtensionProperties extensionProperties;

    BaseStorage storage;

    ExtensionCallback callbacks;

    WebhookService webhookService;

    public Extension initialize(ExtensionProperties extensionProperties, BaseStorage storage,
                                ExtensionCallback callbacks) {
        Extension extension = new Extension();
        extension.setStorage(storage);
        extension.setExtensionProperties(extensionProperties);

        if (StringUtils.isEmpty(extensionProperties.getApi_key())) {
            throw new FdkInvalidExtensionJson("Invalid apiKey");
        }

        if (StringUtils.isEmpty(extensionProperties.getApi_secret())) {
            throw new FdkInvalidExtensionJson("Invalid apiSecret");
        }

        if (!isValid(extensionProperties.getBase_url())) {
            throw new FdkInvalidExtensionJson("Invalid baseUrl");
        }
        verifyScopes(extensionProperties.getScopes());
        if (ObjectUtils.isEmpty(callbacks) || (ObjectUtils.isEmpty(callbacks) &&
                (ObjectUtils.isEmpty(callbacks.getAuth()) ||
                        ObjectUtils.isEmpty(callbacks.getInstall()) ||
                        ObjectUtils.isEmpty(callbacks.getUninstall())))) {
            throw new FdkInvalidExtensionJson(
                    "Missing some of callbacks. Please add all , auth, install and uninstall callbacks.");
        }
        extension.setCallbacks(callbacks);
        extensionProperties.setAccess_mode(StringUtils.isEmpty(
                extensionProperties.getAccess_mode()) ? "offline" : extensionProperties.getAccess_mode());

        if (StringUtils.isNotEmpty(extensionProperties.getCluster())) {
            if (!isValid(extensionProperties.getCluster())) {
                throw new FdkInvalidExtensionJson("Invalid cluster");
            }
        }
        extension.setWebhookService(new WebhookService());
        extension.setExtensionProperties(extensionProperties);
        if (Objects.nonNull(extensionProperties.getWebhook())) {
            extension.getWebhookService()
                     .initialize(extensionProperties);
        }
        this.extensionProperties = extensionProperties;
        return extension;
    }

    private static void verifyScopes(String scopes) {
        List<String> scopeList = Arrays.asList(scopes.split("\\s*,\\s*"));
        if (CollectionUtils.isEmpty(scopeList)) {
            throw new FdkInvalidExtensionJson("Invalid scopes in extension");
        }
    }

    public String getAuthCallback() {
        return String.format("%s%s", this.extensionProperties.getBase_url(), "/fp/auth");
    }

    public boolean isOnlineAccessMode() {
        return Objects.equals(this.extensionProperties.getAccess_mode(), "online");
    }

    public PlatformConfig getPlatformConfig(String companyId) {
        return new PlatformConfig(companyId, this.extensionProperties.getApi_key(),
                                  this.extensionProperties.getApi_secret(), this.extensionProperties.getCluster());
    }

    public PlatformClient getPlatformClient(String companyId, AccessToken session) {
        log.info("companyId: "+companyId);
        PlatformConfig platformConfig = this.getPlatformConfig(companyId);
        log.info("platformConfig: "+platformConfig);
        log.info("platformConfig.getApiKey: "+platformConfig.getApiKey());
        log.info("platformConfig.getCompanyId: "+platformConfig.getCompanyId());
        log.info("platformConfig.getApiSecret: "+platformConfig.getApiSecret());

        platformConfig.getPlatformOauthClient()
                      .setToken(session);
        if(session.getExpiresIn()!=0) {
            if (((session.getExpiresIn() - new Date().getTime()) / 1000) <= Fields.MIN_TIME_MILLIS) {
                try {
                    log.info("Renewing access token for company : " + companyId);
                    platformConfig.getPlatformOauthClient().renewAccesstoken();
                    log.info("Access token renewed for company : " + companyId);
                } catch (Exception e) {
                    log.error("Exception occurred in renewing access token ", e);
                }
            }
        }
        return new PlatformClient(platformConfig);
    }

    public static boolean isValid(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    interface Fields {
        int MIN_TIME_MILLIS = 120;
    }
}



