package com.fynd.extension.model;

import com.fynd.extension.error.FdkInvalidExtensionConfig;
import com.fynd.extension.error.FdkSessionNotFound;
import com.fynd.extension.middleware.AccessMode;
import com.fynd.extension.middleware.ClientCall;
import com.fynd.extension.middleware.ExtensionInterceptor;
import com.fynd.extension.middleware.FdkConstants;
import com.fynd.extension.service.WebhookService;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.fynd.extension.storage.BaseStorage;
import com.sdk.common.RequestSignerInterceptor;
import com.sdk.common.RetrofitServiceFactory;
import com.sdk.common.model.AccessTokenDto;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import retrofit2.Response;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static com.fynd.extension.controllers.ExtensionController.Fields.DELIMITER;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class Extension {

    ExtensionProperties extensionProperties;

    BaseStorage storage;

    ExtensionCallback callbacks;

    WebhookService webhookService;

    @Value("${fdk-extension.version}")
    String buildVersion;

    boolean isInitialized;

    public Extension initialize(ExtensionProperties extensionProperties, BaseStorage storage,
                                ExtensionCallback callbacks) {
        Extension extension = new Extension();
        extension.setInitialized(false);
        this.isInitialized = false;
        extension.setStorage(storage);
        extension.setExtensionProperties(extensionProperties);

        if (StringUtils.isEmpty(extensionProperties.getApiKey())) {
            throw new FdkInvalidExtensionConfig("Invalid apiKey");
        }

        if (StringUtils.isEmpty(extensionProperties.getApiSecret())) {
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
        extensionProperties.setAccessMode(StringUtils.isEmpty(
                extensionProperties.getAccessMode()) ? AccessMode.OFFLINE.getName() : extensionProperties.getAccessMode());

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
        if (!isValid(extensionProperties.getBaseUrl())) {
            throw new FdkInvalidExtensionConfig("Invalid baseUrl");
        } else if (StringUtils.isNotEmpty(extensionProperties.getBaseUrl()) &&
                Objects.nonNull(extensionDetails)) {
            extensionProperties.setBaseUrl(extensionDetails.getBaseUrl());
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

    public Response<ExtensionDetailsDTO> getExtensionDetails(
            ExtensionProperties extensionProperties) throws IOException {
        RetrofitServiceFactory retrofitServiceFactory = new RetrofitServiceFactory();
        List<Interceptor> interceptorList = new ArrayList<>();
        interceptorList.add(new ExtensionInterceptor(extensionProperties));
        interceptorList.add(new RequestSignerInterceptor());
        ClientCall clientCall = retrofitServiceFactory.createService(extensionProperties.getCluster(), ClientCall.class,
                                                                     interceptorList);
        return clientCall.getExtensionDetails(extensionProperties.getApiKey())
                         .execute();
    }

    public String getSessionIdFromCookie(Cookie[] cookies, String companyId) {
        try {
            Cookie cookieFound = Arrays.stream(cookies)
                                       .filter(Objects::nonNull)
                                       .filter(cookie -> Objects.nonNull(cookie.getName()) && cookie.getName()
                                                                                                    .contains(
                                                                                                            FdkConstants.SESSION_COOKIE_NAME) && cookie.getName()
                                                                                                                                                       .split(DELIMITER).length == 3)
                                       .filter(cookie -> cookie.getName()
                                                               .split(DELIMITER)[2].equals(companyId))

                                       .findFirst()
                                       .orElseThrow(() -> new FdkSessionNotFound("Cookie not found"));
            log.info("Cookie found : {}", cookieFound.getName());
            return cookieFound.getValue();
        } catch (Exception e) {
            log.error("Failure in fetching Cookie for Company Id : {}", companyId, e);
        }
        return StringUtils.EMPTY;
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
        return String.format("%s%s", this.extensionProperties.getBaseUrl(), "/fp/auth");
    }

    public boolean isOnlineAccessMode() {
        return Objects.equals(this.extensionProperties.getAccessMode(), AccessMode.ONLINE.getName());
    }

    public PlatformClient getPlatformClient(String companyId, Session session) {
        if (!this.isInitialized) {
            throw new FdkInvalidExtensionConfig("Extension not initialized due to invalid data");
        }
        PlatformConfig platformConfig = this.getPlatformConfig(companyId);
        AccessTokenDto accessTokenDto = buildAccessToken(session);
        platformConfig.getPlatformOauthClient()
                      .setToken(accessTokenDto);
        platformConfig.getPlatformOauthClient()
                      .setTokenExpiresAt(session.getAccessTokenValidity());
        if (Objects.nonNull(session.getAccessTokenValidity()) && Objects.nonNull(session.getRefreshToken())) {
            boolean acNrExpired = ((session.getAccessTokenValidity() - new Date().getTime()) / 1000) <= 120;
            if (acNrExpired) {
                try {
                    log.debug("Renewing access token for company {} with platform config {}", companyId,
                              platformConfig);
                    AccessTokenDto renewTokenRes = platformConfig.getPlatformOauthClient()
                                                                 .renewAccesstoken();
                    renewTokenRes.setAccessTokenValidity(platformConfig.getPlatformOauthClient()
                                                                       .getTokenExpiresAt());
                    Session.updateToken(renewTokenRes, session);
                    SessionStorage sessionStorage = new SessionStorage();
                    sessionStorage.saveSession(session, this);
                    log.info("Access token renewed for company : " + companyId);
                } catch (Exception e) {
                    log.error("Exception occurred in renewing access token ", e);
                }
            }
        }
        PlatformClient platformClient = new PlatformClient(platformConfig);

        platformClient.setExtraHeader("x-ext-lib-version", "java/" + buildVersion);

        return platformClient;
    }

    public PlatformConfig getPlatformConfig(String companyId) {
        if (!this.isInitialized) {
            throw new FdkInvalidExtensionConfig("Extension not initialized due to invalid data");
        }
        return new PlatformConfig(companyId, this.extensionProperties.getApiKey(),
                                  this.extensionProperties.getApiSecret(), this.extensionProperties.getCluster(),
                                  false);
    }

    private AccessTokenDto buildAccessToken(Session session) {
        AccessTokenDto accessTokenDto = new AccessTokenDto();
        accessTokenDto.setAccessTokenValidity(session.getAccessTokenValidity());
        accessTokenDto.setRefreshToken(session.getRefreshToken());
        accessTokenDto.setAccessMode(session.getAccessMode());
        accessTokenDto.setAccessToken(session.getAccessToken());
        accessTokenDto.setExpires(session.getExpires());
        accessTokenDto.setExpiresIn(session.getExpiresIn());
        accessTokenDto.setCurrentUser(session.getCurrentUser());
        return accessTokenDto;
    }
}



