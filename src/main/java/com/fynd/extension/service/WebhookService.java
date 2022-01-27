package com.fynd.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.error.*;
import com.fynd.extension.middleware.EventHandler;
import com.fynd.extension.model.ExtensionProperties;
import com.fynd.extension.model.WebhookProperties;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformModels;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WebhookService {

    String webhookUrl;
    String associationCriteria;
    WebhookProperties webhookProperties;

    @Autowired
    Map<String, EventHandler> eventHandlerMap;

    @Autowired
    ExtensionProperties extensionProperties;

    public void initialize(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
        this.webhookProperties = extensionProperties.getWebhook();
        //1. Validate the Email notification
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}");
        Matcher mat = pattern.matcher(webhookProperties.getNotification_email());
        if(!mat.matches()) {
            throw new FdkInvalidWebhookConfig("Invalid or missing notification_email");
        }

        //2. Validate API path
        if(StringUtils.isNotEmpty(webhookProperties.getApi_path()) && !webhookProperties.getApi_path().startsWith("/")) {
            throw new FdkInvalidWebhookConfig("Invalid or missing api_path");
        }

        //3. Validate Event Map
        if(CollectionUtils.isEmpty(webhookProperties.getEvent_map())) {
            throw new FdkInvalidWebhookConfig("Invalid or missing event_map");
        }

        if(Objects.isNull(webhookProperties.getSubscribe_on_install())) {
            webhookProperties.setSubscribe_on_install(true);
        } else {
            webhookProperties.setSubscribe_on_install(webhookProperties.getSubscribe_on_install());
        }
        log.info("Webhook registry initialized");
    }

    public void syncEvents(PlatformClient platformClient, ExtensionProperties extensionProperties) {
        log.info("Sync events started");
        if(Objects.nonNull(extensionProperties)) {
            initialize(extensionProperties);
        }
        try {
            PlatformModels.EventConfigResponse eventConfigList = platformClient.webhook.fetchAllEventConfigurations();
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.
                    webhook.
                    getSubscribersByExtensionId(1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = new PlatformModels.SubscriberConfig();;
            if(Objects.nonNull(subscriberConfigList) &&
                    CollectionUtils.isEmpty(subscriberConfigList.getItems()) &&
                    Objects.nonNull(this.webhookProperties)) {
                subscriberConfig.setName(this.extensionProperties.getApi_key());
                subscriberConfig.setWebhookUrl(getWebhookUrl(this.extensionProperties.getBase_url(), this.webhookProperties.getApi_path()));
                subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
                subscriberConfig.setEmailId(this.webhookProperties.getNotification_email());
                subscriberConfig.setEventId(getEventIds(this.webhookProperties, eventConfigList));
                PlatformModels.Association association = new PlatformModels.Association();
                association.setCompanyId(Integer.parseInt(platformClient.getConfig().getCompanyId()));
                association.setCriteria(getCriteria(this.webhookProperties));
                association.setApplicationId(new ArrayList<>());
                subscriberConfig.setAssociation(association);
                subscriberConfig.setAuthMeta(new PlatformModels.AuthMeta("hmac", this.extensionProperties.getApi_secret()));
                platformClient.webhook.registerSubscriberToEvent(subscriberConfig);  
            } else {
                log.debug("Webhook config on platform side for company id : " + platformClient.getConfig()
                                                                                              .getCompanyId() + " with config : " +
                                  new ObjectMapper().writeValueAsString(subscriberConfigList));
                subscriberConfig = setSubscriberConfig(subscriberConfigList);
                subscriberConfig.setEventId(getEventIds(this.webhookProperties, eventConfigList));
                if(isConfigurationUpdated(subscriberConfig, this.webhookProperties) ||
                                isEventDiff(subscriberConfigList.getItems().get(0), subscriberConfig)) {
                    platformClient.webhook.updateSubscriberConfig(subscriberConfig);
                }
            }

        } catch (IOException e) {
            throw new FdkWebhookRegistrationError("Failed to sync webhook events. Reason: " + e.getMessage());
        }

    }

    private PlatformModels.SubscriberConfig setSubscriberConfig(PlatformModels.SubscriberConfigList subscriberConfigList) {
        PlatformModels.SubscriberResponse subscriberResponse = subscriberConfigList.getItems()
                                                                                   .get(0);
        PlatformModels.SubscriberConfig subscriberConfig = new PlatformModels.SubscriberConfig();
        subscriberConfig.setName(subscriberResponse.getName());
        subscriberConfig.setId(subscriberResponse.getId());
        subscriberConfig.setWebhookUrl(subscriberResponse.getWebhookUrl());
        subscriberConfig.setAssociation(subscriberResponse.getAssociation());
        subscriberConfig.setStatus(subscriberResponse.getStatus());
        if(subscriberResponse.getStatus().equals(PlatformModels.SubscriberStatus.inactive)) {
            subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
        }
        subscriberConfig.setAuthMeta(subscriberResponse.getAuthMeta());
        subscriberConfig.setEmailId(subscriberResponse.getEmailId());
        return subscriberConfig;
    }

    private String getCriteria(WebhookProperties webhookProperties) {
        return webhookProperties.getSubscribed_saleschannel().equals("specific") ? "SPECIFIC-EVENTS" : "ALL";
    }

    private String getWebhookUrl(String baseURL, String apiPath) {
        return baseURL + apiPath;
    }

    private boolean isConfigurationUpdated(PlatformModels.SubscriberConfig subscriberConfig, WebhookProperties webhookProperties) {
        boolean updated = false;
        if(!webhookProperties.getNotification_email().equals(subscriberConfig.getEmailId())) {
            log.debug("Webhook notification email updated from : "+ subscriberConfig.getEmailId() + "to : " + webhookProperties.getNotification_email());
            subscriberConfig.setEmailId(webhookProperties.getNotification_email());
            updated = true;
        }

        this.webhookUrl = getWebhookUrl(this.extensionProperties.getBase_url(), this.webhookProperties.getApi_path());
        if(!this.webhookUrl.equals(subscriberConfig.getWebhookUrl())) {
            log.debug("Webhook URL updated from : "+ subscriberConfig.getWebhookUrl() + "to : " + this.webhookUrl);
            subscriberConfig.setWebhookUrl(this.webhookUrl);
            updated = true;
        }

        this.associationCriteria = getCriteria(webhookProperties);
        if(!this.associationCriteria.equals(subscriberConfig.getAssociation().getCriteria())) {
            if(this.associationCriteria.equals("ALL")) {
                subscriberConfig.getAssociation().setApplicationId(new ArrayList<>());
            }
            log.debug("Webhook Association Criteria updated from : "+ subscriberConfig.getAssociation().getCriteria() + "to : " + this.associationCriteria);
            subscriberConfig.getAssociation().setCriteria(this.associationCriteria);
            updated = true;
        }

        return updated;
    }

    private List<Integer> getEventIds(WebhookProperties webhookProperties, PlatformModels.EventConfigResponse eventConfigList) {
        List<Integer> eventIds = new ArrayList<>();
        webhookProperties.getEvent_map()
                         .keySet()
                         .forEach(eventKey -> {
                                      if (!CollectionUtils.isEmpty(eventConfigList.getEventConfigs())) {
                                          eventConfigList.getEventConfigs()
                                                         .forEach(eventConfig -> {
                                                             String eventName = eventConfig.getEventName() + "/" + eventConfig.getEventType();
                                                             if (eventName.equals(eventKey)) {
                                                                 eventIds.add(eventConfig.getId());
                                                             }
                                                         });
                                      }
                                  }
                         );
        return eventIds;
    }

    private boolean isEventDiff(PlatformModels.SubscriberResponse existingEvents, PlatformModels.SubscriberConfig newEvents) {
        AtomicBoolean updated = new AtomicBoolean(false);
        existingEvents.getEventConfigs().forEach(eventConfig -> {
            if(!newEvents.getEventId().contains(eventConfig.getId())) {
                updated.set(true);
            }
        });
        return updated.get();
    }

    public void disableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if(!this.webhookProperties.getSubscribed_saleschannel().equals("specific")) {
            throw new FdkWebhookRegistrationError("subscribed_saleschannel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.
                    getSubscribersByExtensionId(1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberConfigList);
            List<Integer> eventIds = new ArrayList<>();
            subscriberConfigList.getItems().get(0).getEventConfigs().forEach(eventConfig -> eventIds.add(eventConfig.getId()));
            subscriberConfig.setEventId(eventIds);
            if(Objects.nonNull(subscriberConfig.getAssociation()) &&
                    !CollectionUtils.isEmpty(subscriberConfig.getAssociation().getApplicationId()) &&
                    !subscriberConfig.getAssociation().getApplicationId().contains(applicationId)) {
                subscriberConfig.getAssociation().getApplicationId().remove(applicationId);
            }
            platformClient.webhook.updateSubscriberConfig(subscriberConfig);
            log.debug("Webhook disabled for saleschannel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred : ", e);
            throw new FdkWebhookRegistrationError("Failed to add saleschannel webhook. Reason: "+ e.getMessage());
        }
    }

    public void enableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if(!this.webhookProperties.getSubscribed_saleschannel().equals("specific")) {
            throw new FdkWebhookRegistrationError("subscribed_saleschannel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.
                    webhook.
                    getSubscribersByExtensionId(1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberConfigList);
            List<Integer> eventIds = new ArrayList<>();
            subscriberConfigList.getItems().get(0).getEventConfigs().forEach(eventConfig -> eventIds.add(eventConfig.getId()));
            subscriberConfig.setEventId(eventIds);
            if(!subscriberConfig.getAssociation().getApplicationId().contains(applicationId)) {
                subscriberConfig.getAssociation().getApplicationId().add(applicationId);
            }
            platformClient.webhook.updateSubscriberConfig(subscriberConfig);
            log.debug("Webhook enabled for saleschannel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred : ", e);
            throw new FdkWebhookRegistrationError("Failed to add saleschannel webhook. Reason: "+ e.getMessage());
        }
    }

    public void processWebhook(HttpServletRequest httpServletRequest) {
        try {
            String signature = httpServletRequest.getHeader("x-fp-signature");
            String responseBody = httpServletRequest.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            JSONObject response = new JSONObject(responseBody);
            JSONObject event = response.getJSONObject("event");
            if(event.getString("name").equals("ping")) {
                return;
            }
            verifySignature(signature, responseBody);
            String eventName = event.getString("name") + "/" + event.getString("type");
            String instanceName = this.extensionProperties.getWebhook().getEvent_map().get(eventName);
            if(Objects.nonNull(this.eventHandlerMap.get(instanceName))) {
                this.eventHandlerMap.get(instanceName)
                                    .handle(eventName, response, response.get("company_id").toString(),
                                            response.getString("application_id"));
            } else {
                throw new FdkWebhookHandlerNotFound("Webhook handler not assigned: "+ eventName);
            }
        } catch (Exception e) {
            log.error("Exception occurred : ", e);
            throw new FdkWebhookProcessError(e.getMessage());
        }
    }

    private void verifySignature(String headerSignature, String responseBody) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            sha256Hmac.init(new SecretKeySpec(this.extensionProperties.getApi_secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculatedSignature = Hex.encodeHexString(sha256Hmac.doFinal(responseBody.getBytes(StandardCharsets.UTF_8)));
            if(!calculatedSignature.equals(headerSignature)) {
                throw new FdkInvalidHMacError("Signature passed does not match calculated body signature");
            }
        } catch (Exception e) {
            log.error("Exception occurred : ", e);
            throw new FdkWebhookProcessError("Verify Signature Failed " +e.getMessage());
        }
    }
}
