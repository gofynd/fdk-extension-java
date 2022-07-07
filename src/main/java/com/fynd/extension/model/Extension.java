package com.fynd.extension.model;

import com.fynd.extension.error.FdkInvalidExtensionConfig;
import com.fynd.extension.middleware.ClientCall;
import com.fynd.extension.service.WebhookService;
import com.fynd.extension.storage.BaseStorage;
import com.sdk.common.AccessToken;
import com.sdk.common.RequestSignerInterceptor;
import com.sdk.common.RetrofitServiceFactory;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import retrofit2.Response;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class Extension {

    ExtensionProperties extensionProperties;

    BaseStorage storage;

    ExtensionCallback callbacks;

    WebhookService webhookService;

    boolean isInitialized;

    public Extension initialize(ExtensionProperties extensionProperties, BaseStorage storage,
                                ExtensionCallback callbacks) {
        Extension extension = new Extension();
        extension.setInitialized(false);
        this.isInitialized = false;
        extension.setStorage(storage);
        extension.setExtensionProperties(extensionProperties);

        if (StringUtils.isEmpty(extensionProperties.getApi_key())) {
            throw new FdkInvalidExtensionConfig("Invalid apiKey");
        }

        if (StringUtils.isEmpty(extensionProperties.getApi_secret())) {
            throw new FdkInvalidExtensionConfig("Invalid apiSecret");
        }
        if (ObjectUtils.isEmpty(callbacks) || (ObjectUtils.isEmpty(callbacks) &&
                (ObjectUtils.isEmpty(callbacks.getAuth()) ||
                        ObjectUtils.isEmpty(callbacks.getInstall()) ||
                        ObjectUtils.isEmpty(callbacks.getUninstall())))) {
            throw new FdkInvalidExtensionConfig(
                    "Missing some of callbacks. Please add all , auth, install and uninstall callbacks.");
        }
        extension.setCallbacks(callbacks);
        extensionProperties.setAccess_mode(StringUtils.isEmpty(
                extensionProperties.getAccess_mode()) ? "offline" : extensionProperties.getAccess_mode());

        if (StringUtils.isNotEmpty(extensionProperties.getCluster())) {
            if (!isValid(extensionProperties.getCluster())) {
                throw new FdkInvalidExtensionConfig(
                        "Invalid cluster value. Invalid value: " + extensionProperties.getCluster());
            }
        }
        extension.setWebhookService(new WebhookService());
        this.extensionProperties = extensionProperties;
        ExtensionDetailsDTO extensionDetails = null;
        try {
            extensionDetails = getExtensionDetails(extensionProperties).body();
        } catch (Exception e) {
            log.error("Failed in getting Extension details", e);
        }
        if (!isValid(extensionProperties.getBase_url())) {
            throw new FdkInvalidExtensionConfig("Invalid baseUrl");
        } else if (StringUtils.isNotEmpty(extensionProperties.getBase_url()) &&
                Objects.nonNull(extensionDetails)) {
            extensionProperties.setBase_url(extensionDetails.getBaseUrl());
        }
        if (StringUtils.isNotEmpty(extensionProperties.getScopes())) {
            verifyScopes(extensionProperties.getScopes(), extensionDetails);
        }
        extension.setExtensionProperties(extensionProperties);
        log.info("Extension initialized");
        if (Objects.nonNull(extensionProperties.getWebhook())) {
            extension.getWebhookService()
                     .initialize(extensionProperties);
        }
        this.isInitialized = true;
        extension.setInitialized(true);
        return extension;
    }

    public static boolean isValid(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Response<ExtensionDetailsDTO> getExtensionDetails(
            ExtensionProperties extensionProperties) throws IOException {
        RetrofitServiceFactory retrofitServiceFactory = new RetrofitServiceFactory();
        List<Interceptor> interceptorList = new ArrayList<>();
        interceptorList.add(new ExtensionInterceptor(extensionProperties));
        interceptorList.add(new RequestSignerInterceptor());
        ClientCall clientCall = retrofitServiceFactory.createService(extensionProperties.getCluster(), ClientCall.class,
                                                                     interceptorList);
        return clientCall.getExtensionDetails(extensionProperties.getApi_key())
                         .execute();
    }

    private static void verifyScopes(String scopes, ExtensionDetailsDTO extensionDetailsDTO) {
        List<String> scopeList = Arrays.asList(scopes.split("\\s*,\\s*"));
        List<String> missingScopes = scopeList.stream()
                                              .filter(val -> !extensionDetailsDTO.getScope()
                                                                                 .contains(val))
                                              .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(scopeList) || !missingScopes.isEmpty()) {
            throw new FdkInvalidExtensionConfig(
                    "Invalid scopes in extension config. Invalid scopes : " + missingScopes);
        }
    }

    public String getAuthCallback() {
        return String.format("%s%s", this.extensionProperties.getBase_url(), "/fp/auth");
    }

    public boolean isOnlineAccessMode() {
        return Objects.equals(this.extensionProperties.getAccess_mode(), "online");
    }

    public PlatformClient getPlatformClient(String companyId, AccessToken session) {
        if (!this.isInitialized){
            throw new FdkInvalidExtensionConfig("Extension not initialized due to invalid data");
        }
        PlatformConfig platformConfig = this.getPlatformConfig(companyId);
        platformConfig.getPlatformOauthClient()
                      .setToken(session);
        platformConfig.getPlatformOauthClient().getTokenExpiresIn()
        if (session.getExpiresIn() != 0) {
            if (((session.getExpiresIn() - new Date().getTime()) / 1000) <= Fields.MIN_TIME_MILLIS) {
                try {
                    log.info("Renewing access token for company : " + companyId);
                    platformConfig.getPlatformOauthClient()
                                  .renewAccesstoken();
                    log.info("Access token renewed for company : " + companyId);
                } catch (Exception e) {
                    log.error("Exception occurred in renewing access token ", e);
                }
            }
        }
        return new PlatformClient(platformConfig);
    }

    public PlatformConfig getPlatformConfig(String companyId) {
        if (!this.isInitialized){
            throw new FdkInvalidExtensionConfig("Extension not initialized due to invalid data");
        }
        return new PlatformConfig(companyId, this.extensionProperties.getApi_key(),
                                  this.extensionProperties.getApi_secret(), this.extensionProperties.getCluster(), false);
    }

    interface Fields {
        int MIN_TIME_MILLIS = 120;
    }
}



