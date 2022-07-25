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
import com.sdk.universal.PublicClient;
import com.sdk.universal.PublicConfig;
import com.sdk.universal.PublicModels;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired
    ObjectMapper objectMapper = new ObjectMapper();

    public boolean isInitialized;

    public void syncEvents(PlatformClient platformClient, ExtensionProperties extensionProperties,
                           Boolean enableWebhooks) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        log.info("Webhook sync events started");
        if (Objects.nonNull(extensionProperties)) {
            initialize(extensionProperties);
        }
        try {
            PlatformModels.EventConfigResponse eventConfigList = platformClient.webhook.fetchAllEventConfigurations();
            PlatformModels.SubscriberResponse subscriberResponse = getSubscriberConfig(platformClient);
            PlatformModels.SubscriberConfig subscriberConfig = new PlatformModels.SubscriberConfig();
            if (Objects.isNull(subscriberResponse) && Objects.nonNull(this.webhookProperties)) {
                subscriberConfig.setName(this.extensionProperties.getApiKey());
                subscriberConfig.setWebhookUrl(
                        getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath()));
                subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
                subscriberConfig.setEmailId(this.webhookProperties.getNotificationEmail());
                subscriberConfig.setEventId(List.copyOf(getEventIds(this.webhookProperties,
                                                                    eventConfigList)));
                PlatformModels.Association association = new PlatformModels.Association();
                association.setCompanyId(Integer.parseInt(platformClient.getConfig()
                                                                        .getCompanyId()));
                association.setCriteria(getCriteria(this.webhookProperties, Collections.emptyList()));
                association.setApplicationId(new ArrayList<>());
                subscriberConfig.setAssociation(association);
                subscriberConfig.setAuthMeta(
                        new PlatformModels.AuthMeta(Fields.HMAC, this.extensionProperties.getApiSecret()));
                if(enableWebhooks!=null) {
                    if(enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
                    } else {
                        subscriberConfig.setStatus(PlatformModels.SubscriberStatus.inactive);
                    }
                }
                platformClient.webhook.registerSubscriberToEvent(subscriberConfig);
                log.info("Webhook Config Details Registered");
            } else {
                log.info("Webhook config on platform side for company id : " + platformClient.getConfig()
                                                                                             .getCompanyId() + " with config : " +
                                 objectMapper.writeValueAsString(subscriberResponse));
                subscriberConfig = setSubscriberConfig(subscriberResponse);
                subscriberConfig.setEventId(List.copyOf(getEventIds(this.webhookProperties,
                                                                    eventConfigList)));
                if(enableWebhooks!=null) {
                    if(enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(PlatformModels.SubscriberStatus.active);
                    } else {
                        subscriberConfig.setStatus(PlatformModels.SubscriberStatus.inactive);
                    }
                }
                if (isConfigurationUpdated(subscriberConfig, this.webhookProperties, enableWebhooks) || isEventDiff(
                        subscriberResponse, subscriberConfig)) {
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
        this.isInitialized = false;
        //1. Validate the Email notification
        Pattern pattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}");
        Matcher mat = pattern.matcher(webhookProperties.getNotificationEmail());
        if (!mat.matches()) {
            throw new FdkInvalidWebhookConfig(Fields.INVALID_EMAIL);
        }

        //2. Validate API path
        if (StringUtils.isNotEmpty(webhookProperties.getApiPath()) && !webhookProperties.getApiPath()
                                                                                        .startsWith("/")) {
            throw new FdkInvalidWebhookConfig(Fields.INVALID_PATH);
        }

        //3. Validate Event Map
        if (CollectionUtils.isEmpty(webhookProperties.getEventMap())) {
            throw new FdkInvalidWebhookConfig(Fields.MISSING_EVENTS);
        }

        if (Objects.isNull(webhookProperties.getSubscribeOnInstall())) {
            webhookProperties.setSubscribeOnInstall(true);
        } else {
            webhookProperties.setSubscribeOnInstall(webhookProperties.getSubscribeOnInstall());
        }

        //4. Validate Webhook events
        PublicModels.EventConfigResponse eventConfigResponse = getEventConfig(webhookProperties.getEventMap());
        List<String> errorWebhooks = validateEvents(webhookProperties.getEventMap(), eventConfigResponse);
        if (!errorWebhooks.isEmpty()) {
            throw new FdkInvalidWebhookConfig("Webhooks events errors" + errorWebhooks);
        }
        this.isInitialized = true;
        log.info("Webhook registry initialized");
    }

    private PlatformModels.SubscriberResponse getSubscriberConfig(PlatformClient platformClient) {
        try {
            PlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.getSubscribersByExtensionId(
                    1, 1, this.extensionProperties.getApiKey());
            if (Objects.nonNull(subscriberConfigList) && CollectionUtils.isNotEmpty(subscriberConfigList.getItems())) {
                return subscriberConfigList.getItems()
                                           .get(0);
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching webhook Subscriber Configuration", e);
            throw new FdkInvalidWebhookConfig(
                    "Error while fetching webhook subscriber configuration : " + e.getMessage());
        }
    }

    private String getWebhookUrl(String baseURL, String apiPath) {
        return baseURL + apiPath;
    }

    private Set<Integer> getEventIds(WebhookProperties webhookProperties,
                                     PlatformModels.EventConfigResponse eventConfigList) {
        Set<Integer> eventIds = new HashSet<>();
        webhookProperties.getEventMap()
                         .forEach(eventMap -> {
                             if (!CollectionUtils.isEmpty(eventConfigList.getEventConfigs())) {
                                 eventConfigList.getEventConfigs()
                                                .forEach(eventConfig -> {
                                                    String eventName = eventConfig.getEventName() + "/" + eventConfig.getEventType();
                                                    if (eventName.equals(eventMap.getName())) {
                                                        if (StringUtils.isEmpty(
                                                                eventMap.getCategory()) || StringUtils.isEmpty(
                                                                eventMap.getVersion())) {
                                                            eventIds.add(eventConfig.getId());
                                                        } else if (eventConfig.getEventCategory()
                                                                              .equals(eventMap.getCategory())
                                                                && eventConfig.getVersion()
                                                                              .equals(eventMap.getVersion())) {
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
        if (webhookProperties.getSubscribedSalesChannel()
                             .equals(Fields.SPECIFIC_CHANNEL)) {
            return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
        }
        return Criteria.ALL.getValue();
    }

    private PlatformModels.SubscriberConfig setSubscriberConfig(
            PlatformModels.SubscriberResponse subscriberResponse) {
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
                                           WebhookProperties webhookProperties, Boolean enableWebhooks) {
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

        if (!webhookProperties.getNotificationEmail()
                              .equals(subscriberConfig.getEmailId())) {
            log.info(
                    "Webhook notification email updated from : " + subscriberConfig.getEmailId() + "to : " + webhookProperties.getNotificationEmail());
            subscriberConfig.setEmailId(webhookProperties.getNotificationEmail());
            updated = true;
        }

        this.webhookUrl = getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath());
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
        if(existingEventIds.size() > uniques.size()) {
            return true;
        }
        uniques.removeAll(existingEventIds);
        log.info("Unique Event IDs found  : " + uniques);
        return !uniques.isEmpty();
    }

    private PublicModels.EventConfigResponse getEventConfig(List<EventMapProperties> eventMap) {
        try {
            List<PublicModels.EventConfigBase> eventConfigs = new ArrayList<>();
            eventMap.forEach(event -> {
                String[] eventDetails = event.getName()
                                             .split("/");
                if (eventDetails.length != 2) {
                    throw new FdkInvalidWebhookConfig("Invalid webhook event map key. Invalid key: " + event.getName());
                }
                PublicModels.EventConfigBase eventConfig = new PublicModels.EventConfigBase();
                eventConfig.setEventCategory(event.getCategory());
                eventConfig.setEventName(eventDetails[0]);
                eventConfig.setEventType(eventDetails[1]);
                eventConfig.setVersion(event.getVersion());
                eventConfigs.add(eventConfig);
            });
            PublicConfig publicConfig = new PublicConfig(extensionProperties.getApiKey(),
                                                         extensionProperties.getCluster());
            PublicClient publicClient = new PublicClient(publicConfig);
            PublicModels.EventConfigResponse eventConfigResponse = publicClient.webhook.queryWebhookEventDetails(
                    eventConfigs);
            log.debug("Webhook events config received: {}", objectMapper.writeValueAsString(eventConfigResponse));
            return eventConfigResponse;
        } catch (Exception e) {
            log.error("Error in querying Webhook Event Details", e);
            throw new FdkInvalidWebhookConfig("Error in Webhook Event Details due to Missing / Invalid Data");
        }
    }

    private List<String> validateEvents(List<EventMapProperties> eventMap,
                                        PublicModels.EventConfigResponse eventConfigResponse) {
        List<String> errorWebhooks = new ArrayList<>();
        eventMap.forEach(eventSubscribed -> {
            if (!isPartOfEventConfig(eventConfigResponse, eventSubscribed)) {
                errorWebhooks.add(
                        "Not Found : " + eventSubscribed.getName() + "-" + eventSubscribed.getCategory() + "-" + eventSubscribed.getVersion());
            }
        });
        return errorWebhooks;
    }

    private boolean isPartOfEventConfig(PublicModels.EventConfigResponse eventConfigResponse,
                                        EventMapProperties eventSubscribed) {
        String subscribedEventName = eventSubscribed.getName()
                                                    .split("/")[0];
        String subscribedEventType = eventSubscribed.getName()
                                                    .split("/")[1];
        return eventConfigResponse.getEventConfigs()
                                  .stream()
                                  .anyMatch(eventConfig -> eventConfig.getEventCategory()
                                                                      .equals(eventSubscribed.getCategory()) && eventConfig.getEventName()
                                                                                                                           .equals(subscribedEventName) && eventConfig.getEventType()
                                                                                                                                                                      .equals(subscribedEventType) && eventConfig.getVersion()
                                                                                                                                                                                                                 .equals(eventSubscribed.getVersion()));
    }

    public void disableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        if (!this.extensionProperties.getWebhook()
                                     .getSubscribedSalesChannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberResponse subscriberResponse = getSubscriberConfig(platformClient);
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberResponse);
            List<Integer> eventIds = new ArrayList<>();
            subscriberResponse.getEventConfigs()
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
            log.info("Webhook disabled for Sales Channel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred during Disable Webhook Event : ", e);
            throw new FdkWebhookRegistrationError("Failed to add Sales Channel Webhook. Reason: " + e.getMessage());
        }
    }

    private String getCriteria(List<String> applicationIds) {
        return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
    }

    public void enableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        if (!this.extensionProperties.getWebhook()
                                     .getSubscribedSalesChannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            PlatformModels.SubscriberResponse subscriberResponse = getSubscriberConfig(platformClient);
            PlatformModels.SubscriberConfig subscriberConfig = setSubscriberConfig(subscriberResponse);
            List<Integer> eventIds = new ArrayList<>();
            subscriberResponse.getEventConfigs()
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
            throw new FdkWebhookRegistrationError("Failed to add Sales Channel Webhook. Reason: " + e.getMessage());
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
            String eventCategory = event.has(Fields.EVENT_CATEGORY) ? event.getString(
                    Fields.EVENT_CATEGORY) : StringUtils.EMPTY;
            String eventVersion = event.has(Fields.EVENT_VERSION) ? event.getString(
                    Fields.EVENT_VERSION) : StringUtils.EMPTY;
            String instanceName = StringUtils.EMPTY;
            for (EventMapProperties eventMap : this.extensionProperties.getWebhook()
                                                                       .getEventMap()) {
                if (eventMap.getName()
                            .equals(eventName) && StringUtils.isNotEmpty(
                        eventMap.getCategory()) && eventMap.getCategory()
                                                           .equals(eventCategory) && StringUtils.isNotEmpty(
                        eventMap.getVersion()) && eventMap.getVersion()
                                                          .equals(eventVersion)) {
                    instanceName = eventMap.getHandler();
                } else if ((eventMap.getName()
                                    .equals(eventName) && StringUtils.isEmpty(
                        eventMap.getCategory())) || (eventMap.getName()
                                                             .equals(eventName) && StringUtils.isEmpty(
                        eventMap.getVersion()))) {
                    instanceName = eventMap.getHandler();
                } else if ((eventMap.getName()
                                    .equals(eventName) && StringUtils.isEmpty(eventCategory)) || (eventMap.getName()
                                                                                                          .equals(eventName) && StringUtils.isEmpty(
                        eventVersion))) {
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
            sha256Hmac.init(new SecretKeySpec(this.extensionProperties.getApiSecret()
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
        String WEBHOOK_NOT_INITIALISED_ERROR = "Webhook registry not initialized";
    }
}
