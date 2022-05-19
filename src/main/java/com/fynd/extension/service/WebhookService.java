package com.fynd.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.error.*;
import com.fynd.extension.middleware.EventHandler;
import com.fynd.extension.model.Criteria;
import com.fynd.extension.model.EventMapProperties;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WebhookService {

    String webhookUrl;

    String associationCriteria;

    WebhookProperties webhookProperties;

    @Autowired(required = false)
    Map<String, EventHandler> eventHandlerMap;

    @Autowired
    ExtensionProperties extensionProperties;

    public void syncEvents(PlatformClient platformClient, ExtensionProperties extensionProperties) {
        log.info("Sync events started");
        if (Objects.nonNull(extensionProperties)) {
            initialize(extensionProperties);
        }
        try {
            PlatformModels.EventConfigResponse eventConfigList = platformClient.webhook.fetchAllEventConfigurations();
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.getSubscribersByExtensionId(
                    1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = new PlatformModels.SubscriberConfig();
            ;
            if (Objects.nonNull(subscriberConfigList) && CollectionUtils.isEmpty(
                    subscriberConfigList.getItems()) && Objects.nonNull(this.webhookProperties)) {
                subscriberConfig.setName(this.extensionProperties.getApi_key());
                subscriberConfig.setWebhookUrl(
                        getWebhookUrl(this.extensionProperties.getBase_url(), this.webhookProperties.getApi_path()));
                subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
                subscriberConfig.setEmailId(this.webhookProperties.getNotification_email());
                subscriberConfig.setEventId(List.copyOf(getEventIds(this.webhookProperties, eventConfigList)));
                PlatformModels.Association association = new PlatformModels.Association();
                association.setCompanyId(Integer.parseInt(platformClient.getConfig()
                                                                        .getCompanyId()));
                association.setCriteria(getCriteria(this.webhookProperties, Collections.emptyList()));
                association.setApplicationId(new ArrayList<>());
                subscriberConfig.setAssociation(association);
                subscriberConfig.setAuthMeta(
                        new PlatformModels.AuthMeta(Fields.HMAC, this.extensionProperties.getApi_secret()));
                platformClient.webhook.registerSubscriberToEvent(subscriberConfig);
                log.info("Webhook Config Details Registered");
            } else {
                log.info("Webhook config on platform side for company id : " + platformClient.getConfig()
                                                                                             .getCompanyId() + " with config : " + new ObjectMapper().writeValueAsString(
                        subscriberConfigList));
                subscriberConfig = setSubscriberConfig(subscriberConfigList);
                subscriberConfig.setEventId(List.copyOf(getEventIds(this.webhookProperties, eventConfigList)));
                if (isConfigurationUpdated(subscriberConfig, this.webhookProperties) || isEventDiff(
                        subscriberConfigList.getItems()
                                            .get(0), subscriberConfig)) {
                    platformClient.webhook.updateSubscriberConfig(subscriberConfig);
                    log.info("Webhook Config Details updated");
                }
            }

        } catch (IOException e) {
            log.error("Exception occurred during Webhook Sync : ", e);
            throw new FdkWebhookRegistrationError("Failed to sync webhook events. Reason: " + e.getMessage());
        }

    }

    public void initialize(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
        this.webhookProperties = extensionProperties.getWebhook();
        //1. Validate the Email notification
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}");
        Matcher mat = pattern.matcher(webhookProperties.getNotification_email());
        if (!mat.matches()) {
            throw new FdkInvalidWebhookConfig(Fields.INVALID_EMAIL);
        }

        //2. Validate API path
        if (StringUtils.isNotEmpty(webhookProperties.getApi_path()) && !webhookProperties.getApi_path()
                                                                                         .startsWith("/")) {
            throw new FdkInvalidWebhookConfig(Fields.INVALID_PATH);
        }

        //3. Validate Event Map
        if (CollectionUtils.isEmpty(webhookProperties.getEvent_map())) {
            throw new FdkInvalidWebhookConfig(Fields.MISSING_EVENTS);
        }

        if (Objects.isNull(webhookProperties.getSubscribe_on_install())) {
            webhookProperties.setSubscribe_on_install(true);
        } else {
            webhookProperties.setSubscribe_on_install(webhookProperties.getSubscribe_on_install());
        }
        log.info("Webhook registry initialized");
    }

    private String getWebhookUrl(String baseURL, String apiPath) {
        return baseURL + apiPath;
    }

    private Set<Integer> getEventIds(WebhookProperties webhookProperties,
                                     PlatformModels.EventConfigResponse eventConfigList) {
        Set<Integer> eventIds = new HashSet<>();
        webhookProperties.getEvent_map()
                         .forEach(eventMap -> {
                             if (!CollectionUtils.isEmpty(eventConfigList.getEventConfigs())) {
                                 eventConfigList.getEventConfigs()
                                                .forEach(eventConfig -> {
                                                    String eventName = eventConfig.getEventName() + "/" + eventConfig.getEventType();
                                                    if (eventName.equals(eventMap.getName())) {
                                                        if (StringUtils.isEmpty(eventMap.getCategory()) || StringUtils.isEmpty(eventMap.getVersion())) {
                                                            eventIds.add(eventConfig.getId());
                                                        } else if (eventConfig.getEventCategory().equals(eventMap.getCategory())
                                                            && eventConfig.getVersion().equals(eventMap.getVersion())) {
                                                            eventIds.add(eventConfig.getId());
                                                        }
                                                    }
                                                });
                             }
                         });
        log.info("Events IDs opted : " + eventIds);
        return eventIds;
    }

    private String getCriteria(WebhookProperties webhookProperties, List<String> applicationIds) {
        if (webhookProperties.getSubscribed_saleschannel()
                             .equals(Fields.SPECIFIC_CHANNEL)) {
            return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
        }
        return Criteria.ALL.getValue();
    }

    private PlatformModels.SubscriberConfig setSubscriberConfig(
            PlatformModels.SubscriberConfigList subscriberConfigList) {
        PlatformModels.SubscriberResponse subscriberResponse = subscriberConfigList.getItems()
                                                                                   .get(0);
        if (Objects.isNull(subscriberResponse)) {
            throw new FdkWebhookRegistrationError("Subscriber Config Response not found");
        }
        PlatformModels.SubscriberConfig subscriberConfig = new PlatformModels.SubscriberConfig();
        subscriberConfig.setName(subscriberResponse.getName());
        subscriberConfig.setId(subscriberResponse.getId());
        subscriberConfig.setWebhookUrl(subscriberResponse.getWebhookUrl());
        subscriberConfig.setAssociation(subscriberResponse.getAssociation());
        subscriberConfig.setStatus(subscriberResponse.getStatus());
        if (subscriberResponse.getStatus()
                              .equals(PlatformModels.SubscriberStatus.inactive) || subscriberResponse.getStatus()
                                                                                                     .equals(PlatformModels.SubscriberStatus.blocked)) {
            subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
        }
        subscriberConfig.setAuthMeta(subscriberResponse.getAuthMeta());
        subscriberConfig.setEmailId(subscriberResponse.getEmailId());
        return subscriberConfig;
    }

    private boolean isConfigurationUpdated(PlatformModels.SubscriberConfig subscriberConfig,
                                           WebhookProperties webhookProperties) {
        boolean updated = false;
        this.associationCriteria = getCriteria(webhookProperties, subscriberConfig.getAssociation()
                                                                                  .getApplicationId());
        if (!this.associationCriteria.equals(subscriberConfig.getAssociation()
                                                             .getCriteria())) {
            if (this.associationCriteria.equals(Criteria.ALL.getValue())) {
                subscriberConfig.getAssociation()
                                .setApplicationId(new ArrayList<>());
            }
            log.info("Webhook Association Criteria updated from : " + subscriberConfig.getAssociation()
                                                                                      .getCriteria() + "to : " + this.associationCriteria);
            subscriberConfig.getAssociation()
                            .setCriteria(this.associationCriteria);
            updated = true;
        }

        if (!webhookProperties.getNotification_email()
                              .equals(subscriberConfig.getEmailId())) {
            log.info(
                    "Webhook notification email updated from : " + subscriberConfig.getEmailId() + "to : " + webhookProperties.getNotification_email());
            subscriberConfig.setEmailId(webhookProperties.getNotification_email());
            updated = true;
        }

        this.webhookUrl = getWebhookUrl(this.extensionProperties.getBase_url(), this.webhookProperties.getApi_path());
        if (!this.webhookUrl.equals(subscriberConfig.getWebhookUrl())) {
            log.info("Webhook URL updated from : " + subscriberConfig.getWebhookUrl() + "to : " + this.webhookUrl);
            subscriberConfig.setWebhookUrl(this.webhookUrl);
            updated = true;
        }

        return updated;
    }

    private boolean isEventDiff(PlatformModels.SubscriberResponse existingEvents,
                                PlatformModels.SubscriberConfig newEvents) {
        Set<Integer> existingEventIds = existingEvents.getEventConfigs()
                                                      .stream()
                                                      .map(PlatformModels.EventConfig::getId)
                                                      .collect(Collectors.toSet());
        List<Integer> uniques = new ArrayList<>(newEvents.getEventId());
        uniques.removeAll(existingEventIds);
        log.info("Unique Event IDs found  : " + uniques);
        return !uniques.isEmpty();
    }

    public void disableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.extensionProperties.getWebhook()
                                     .getSubscribed_saleschannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.getSubscribersByExtensionId(
                    1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberConfigList);
            List<Integer> eventIds = new ArrayList<>();
            subscriberConfigList.getItems()
                                .get(0)
                                .getEventConfigs()
                                .forEach(eventConfig -> eventIds.add(eventConfig.getId()));
            subscriberConfig.setEventId(eventIds);
            if (Objects.nonNull(subscriberConfig.getAssociation()) && !CollectionUtils.isEmpty(
                    subscriberConfig.getAssociation()
                                    .getApplicationId()) && !subscriberConfig.getAssociation()
                                                                             .getApplicationId()
                                                                             .contains(applicationId)) {
                subscriberConfig.getAssociation()
                                .getApplicationId()
                                .remove(applicationId);
                subscriberConfig.getAssociation()
                                .setCriteria(getCriteria(subscriberConfig.getAssociation()
                                                                         .getApplicationId()));
            }
            platformClient.webhook.updateSubscriberConfig(subscriberConfig);
            log.info("Webhook disabled for sales channel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred during Disable Webhook Event : ", e);
            throw new FdkWebhookRegistrationError("Failed to add saleschannel webhook. Reason: " + e.getMessage());
        }
    }

    private String getCriteria(List<String> applicationIds) {
        return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
    }

    public void enableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.extensionProperties.getWebhook()
                                     .getSubscribed_saleschannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.getSubscribersByExtensionId(
                    1, 1, this.extensionProperties.getApi_key());
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberConfigList);
            List<Integer> eventIds = new ArrayList<>();
            subscriberConfigList.getItems()
                                .get(0)
                                .getEventConfigs()
                                .forEach(eventConfig -> eventIds.add(eventConfig.getId()));
            subscriberConfig.setEventId(eventIds);
            if (!subscriberConfig.getAssociation()
                                 .getApplicationId()
                                 .contains(applicationId)) {
                subscriberConfig.getAssociation()
                                .getApplicationId()
                                .add(applicationId);
                subscriberConfig.getAssociation()
                                .setCriteria(getCriteria(subscriberConfig.getAssociation()
                                                                         .getApplicationId()));
            }
            platformClient.webhook.updateSubscriberConfig(subscriberConfig);
            log.info("Webhook enabled for sales channel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred during Enable Webhook event : ", e);
            throw new FdkWebhookRegistrationError("Failed to add saleschannel webhook. Reason: " + e.getMessage());
        }
    }

    public void processWebhook(HttpServletRequest httpServletRequest) {
        try {
            String signature = httpServletRequest.getHeader(Fields.SIGNATURE);
            String responseBody = httpServletRequest.getReader()
                                                    .lines()
                                                    .collect(Collectors.joining(System.lineSeparator()));
            log.info("Event Received in Extension : " + responseBody);
            JSONObject response = new JSONObject(responseBody);
            String companyID = response.has(Fields.COMPANY_ID) ? response.get(Fields.COMPANY_ID)
                                                                         .toString() : StringUtils.EMPTY;
            String applicationID = response.has(Fields.APPLICATION_ID) ? response.get(Fields.APPLICATION_ID)
                                                                                 .toString() : StringUtils.EMPTY;
            JSONObject event = response.getJSONObject(Fields.EVENT_OBJECT);
            if (event.getString(Fields.EVENT_NAME)
                     .equals(Fields.EVENT_PING)) {
                return;
            }
            verifySignature(signature, responseBody);
            String eventName = event.getString(Fields.EVENT_NAME) + "/" + event.getString(Fields.EVENT_TYPE);
            String eventCategory = event.has(Fields.EVENT_CATEGORY) ? event.getString(Fields.EVENT_CATEGORY)
                    : StringUtils.EMPTY;
            String eventVersion = event.has(Fields.EVENT_VERSION) ? event.getString(Fields.EVENT_VERSION)
                    : StringUtils.EMPTY;
            String instanceName = StringUtils.EMPTY;
            for (EventMapProperties eventMap : this.extensionProperties.getWebhook()
                                                                       .getEvent_map()) {
                if (eventMap.getName().equals(eventName) && StringUtils.isNotEmpty(eventMap.getCategory()) && eventMap.getCategory().equals(eventCategory)
                        && StringUtils.isNotEmpty(eventMap.getVersion()) && eventMap.getVersion().equals(eventVersion)) {
                    instanceName = eventMap.getHandler();
                } else if ((eventMap.getName().equals(eventName) && StringUtils.isEmpty(eventMap.getCategory()))
                        || (eventMap.getName().equals(eventName) && StringUtils.isEmpty(eventMap.getVersion()))) {
                    instanceName = eventMap.getHandler();
                }  else if ((eventMap.getName().equals(eventName) && StringUtils.isEmpty(eventCategory))
                        || (eventMap.getName().equals(eventName) && StringUtils.isEmpty(eventVersion))) {
                    instanceName = eventMap.getHandler();
                }
            }
            if (StringUtils.isNotEmpty(instanceName) && Objects.nonNull(this.eventHandlerMap.get(instanceName))) {
                log.info("Handler Chosen for execution " + instanceName);
                this.eventHandlerMap.get(instanceName)
                                    .handle(eventName, response, companyID, applicationID);
            } else {
                throw new FdkWebhookHandlerNotFound("Webhook handler not assigned: " + eventName);
            }
        } catch (Exception e) {
            log.error("Exception occurred during Webhook Event processing : ", e);
            throw new FdkWebhookProcessError(e.getMessage());
        }
    }

    private void verifySignature(String headerSignature, String responseBody) {
        try {
            Mac sha256Hmac = Mac.getInstance(Fields.HMAC_SHA);
            sha256Hmac.init(new SecretKeySpec(this.extensionProperties.getApi_secret()
                                                                      .getBytes(StandardCharsets.UTF_8),
                                              Fields.HMAC_SHA));
            String calculatedSignature = Hex.encodeHexString(
                    sha256Hmac.doFinal(responseBody.getBytes(StandardCharsets.UTF_8)));
            if (!calculatedSignature.equals(headerSignature)) {
                throw new FdkInvalidHMacError("Signature passed does not match calculated body signature");
            }
        } catch (Exception e) {
            log.error("Exception occurred during Signature Verification : ", e);
            throw new FdkWebhookProcessError("Verify Signature Failed " + e.getMessage());
        }
    }

    interface Fields {
        String INVALID_EMAIL = "Invalid or missing notification_email";
        String INVALID_PATH = "Invalid or missing api_path";
        String MISSING_EVENTS = "Invalid or missing event_map";
        String HMAC = "hmac";
        String SPECIFIC_CHANNEL = "specific";
        String SIGNATURE = "x-fp-signature";
        String EVENT_OBJECT = "event";
        String EVENT_NAME = "name";
        String EVENT_PING = "ping";
        String EVENT_TYPE = "type";
        String EVENT_CATEGORY = "category";
        String COMPANY_ID = "company_id";
        String APPLICATION_ID = "application_id";
        String HMAC_SHA = "HmacSHA256";
        String EVENT_VERSION = "version";
    }
}
