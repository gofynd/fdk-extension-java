package com.fynd.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.error.*;
import com.fynd.extension.middleware.EventHandler;
import com.fynd.extension.model.Criteria;
import com.fynd.extension.model.EventMapProperties;
import com.fynd.extension.model.ExtensionProperties;
import com.fynd.extension.model.WebhookProperties;
import com.fynd.extension.model.webhookmodel.SubscriberConfigContainer;
import com.sdk.common.model.FDKException;
import com.sdk.common.model.FDKServerResponseError;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.webhook.WebhookPlatformModels;
import com.sdk.universal.PublicClient;
import com.sdk.universal.PublicConfig;
import com.sdk.universal.webhook.WebhookPublicModels;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WebhookService {

    public boolean isInitialized;

    String webhookUrl;

    String associationCriteria;

    WebhookProperties webhookProperties;

    @Autowired(required = false)
    Map<String, EventHandler> eventHandlerMap;

    @Autowired
    ExtensionProperties extensionProperties;

    @Autowired
    ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, EventMapProperties> restEventMap;
    private Map<String, EventMapProperties> kafkaEventMap;

    // Done
    public void syncEvents(PlatformClient platformClient, ExtensionProperties extensionProperties,
                           Boolean enableWebhooks) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        log.info("Webhook sync events started");
        if (Objects.nonNull(extensionProperties)) {
            initialize(extensionProperties);
        }
        
        SubscriberConfigContainer subscriberResponseContainer = getSubscriberConfig(platformClient);

        syncSubscriberConfig(subscriberResponseContainer.getRest(), "rest", platformClient, enableWebhooks);
        syncSubscriberConfig(subscriberResponseContainer.getKafka(), "kafka", platformClient, enableWebhooks);
    }

    // Done
    void syncSubscriberConfig(WebhookPlatformModels.SubscriberResponse subscriberResponse, String configType, PlatformClient platformClient, Boolean enableWebhooks) {
        try {

            WebhookPlatformModels.SubscriberConfigRequestV2 subscriberConfig = new WebhookPlatformModels.SubscriberConfigRequestV2();
            if (Objects.isNull(subscriberResponse) && Objects.nonNull(this.webhookProperties)) {
                subscriberConfig.setName(this.extensionProperties.getApiKey());
                if(configType.equals("rest")){
                    subscriberConfig.setWebhookUrl(
                            getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath()));
                }
                subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.active.getPriority());
                subscriberConfig.setEmailId(this.webhookProperties.getNotificationEmail());
                subscriberConfig.setEvents(getEventList(configType.equals("rest") ? this.restEventMap : this.kafkaEventMap, configType));
                subscriberConfig.setProvider(configType);

                WebhookPlatformModels.Association association = new WebhookPlatformModels.Association();
                association.setCompanyId(Integer.parseInt(platformClient.getConfig()
                                                                        .getCompanyId()));
                association.setCriteria(getCriteria(this.webhookProperties, Collections.emptyList()));
                association.setApplicationId(new ArrayList<>());
                subscriberConfig.setAssociation(association);

                subscriberConfig.setAuthMeta(
                        new WebhookPlatformModels.AuthMeta(Fields.HMAC, this.extensionProperties.getApiSecret()));
                if (enableWebhooks != null) {
                    if (enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.active.getPriority());
                    } else {
                        subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.inactive.getPriority());
                    }
                }
                platformClient.webhook.registerSubscriberToEventV2(subscriberConfig);
                log.info("Webhook Config Details Registered");
            } else {
                log.info("Webhook config on platform side for company id : " + platformClient.getConfig()
                        .getCompanyId() + " with config : " +
                        objectMapper.writeValueAsString(subscriberResponse));
                subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                subscriberConfig.setEvents(getEventList(configType.equals("rest") ? this.restEventMap : this.kafkaEventMap, configType));
                if (enableWebhooks != null) {
                    if (enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.active.getPriority());
                    } else {
                        subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.inactive.getPriority());
                    }
                }
                if (isConfigurationUpdated(subscriberConfig, this.webhookProperties) || isEventDiff(
                        subscriberResponse, subscriberConfig) || subscriberResponse.getStatus()
                        .equals(WebhookPlatformModels.SubscriberStatus.inactive)) {
                    platformClient.webhook.updateSubscriberV2(subscriberConfig);
                    log.info("Webhook Config Details updated");
                }
            }

        } catch (IOException | FDKException | FDKServerResponseError e) {
            log.error("Exception occurred during Webhook Sync : ", e);
            throw new FdkWebhookRegistrationError("Failed to sync webhook events. Reason: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    // Done
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

        restEventMap = new HashMap<String, EventMapProperties>();
        kafkaEventMap = new HashMap<String, EventMapProperties>();

        // Validate Webhook eventMap details
        for(EventMapProperties event: webhookProperties.getEventMap()){
            if(StringUtils.isEmpty(event.getName()) || event.getName().split("/").length != 2){
                throw new FdkInvalidWebhookConfig("Invalid/Missing webhook event name. Invalid name: " + event.getName());
            }
            if(StringUtils.isEmpty(event.getCategory())){
                throw new FdkInvalidWebhookConfig("Invalid webhook event category for webhook event " + event.getName());
            }

            if(StringUtils.isEmpty(event.getVersion())){
                throw new FdkInvalidWebhookConfig("Missing version in webhook event " + event.getName());
            }
            if(StringUtils.isEmpty(event.getProvider())){
                event.setProvider("rest");
            }
            List<String> allowedProviders = Arrays.asList("kafka", "rest");
            if (!allowedProviders.contains(event.getProvider())) {
                throw new FdkInvalidWebhookConfig("Invalid provider value in webhook event " + event.getName() +
                        ", allowed values are " + Arrays.toString(allowedProviders.toArray()));
            }

            if ("rest".equals(event.getProvider()) && StringUtils.isEmpty(event.getHandler())) {
                throw new FdkInvalidWebhookConfig("Missing handler in webhook event " + event.getName());
            } else if ("kafka".equals(event.getProvider()) && StringUtils.isEmpty(event.getTopic())) {
                throw new FdkInvalidWebhookConfig("Missing topic in webhook event " + event.getName());
            }

            if ("rest".equals(event.getProvider())) {
                restEventMap.put(event.getCategory() + "/" + event.getName() + "/v" + event.getVersion(), event);
            }
            if ("kafka".equals(event.getProvider())) {
                kafkaEventMap.put(event.getCategory() + "/" + event.getName() + "/v" + event.getVersion(), event);
            }
        }

        //4. Validate Webhook events
        WebhookPublicModels.EventConfigResponse eventConfigResponse = getEventConfig(webhookProperties.getEventMap());
        List<String> errorWebhooks = validateEvents(webhookProperties.getEventMap(), eventConfigResponse);
        if (!errorWebhooks.isEmpty()) {
            throw new FdkInvalidWebhookConfig("Webhooks events errors" + errorWebhooks);
        }
        this.isInitialized = true;
        log.info("Webhook registry initialized");
    }

    // Done
    private SubscriberConfigContainer getSubscriberConfig(PlatformClient platformClient) {
        try {
            WebhookPlatformModels.SubscriberConfigList subscriberConfigList = platformClient.webhook.getSubscribersByExtensionId(
                    null, null, this.extensionProperties.getApiKey());
            SubscriberConfigContainer subscriberConfigContainer = new SubscriberConfigContainer();

            if (Objects.nonNull(subscriberConfigList) && CollectionUtils.isNotEmpty(subscriberConfigList.getItems())) {

                for(WebhookPlatformModels.SubscriberResponse subscriberResponse : subscriberConfigList.getItems()){
                    if(subscriberResponse.getProvider().equals("kafka")){
                        subscriberConfigContainer.setKafka(subscriberResponse);
                    }
                    else{
                        subscriberConfigContainer.setRest(subscriberResponse);
                    }
                }
            }
            return subscriberConfigContainer;
        } catch (Exception e) {
            log.error("Error fetching webhook Subscriber Configuration", e);
            throw new FdkInvalidWebhookConfig(
                    "Error while fetching webhook subscriber configuration : " + e.getMessage());
        }
    }

    // Done
    private String getWebhookUrl(String baseURL, String apiPath) {
        return baseURL + apiPath;
    }

    // Done
    /**
     * @param eventMap - map of user provided configuration
     * @param configType - provider type - kafka or rest
     * @return - returns list of event configured by user to send in register or update subscriberConfig api
     */
    private List<WebhookPlatformModels.Events> getEventList(Map<String, EventMapProperties> eventMap, String configType){
        List<WebhookPlatformModels.Events> eventList = new ArrayList<WebhookPlatformModels.Events>();

        for(Map.Entry<String, EventMapProperties> event : eventMap.entrySet()){
            WebhookPlatformModels.Events eventData = new WebhookPlatformModels.Events();
            eventData.setSlug(event.getKey());
            if(configType.equals("kafka")){
                eventData.setTopic(event.getValue().getTopic());
            }
            eventList.add(eventData);
        }
        log.info("Events opted for " + configType + " : " + eventList);
        return eventList;
    }


    // Done
    private String getCriteria(WebhookProperties webhookProperties, List<String> applicationIds) {
        if (StringUtils.isNotEmpty(this.extensionProperties.getWebhook().getSubscribedSalesChannel())
                && webhookProperties.getSubscribedSalesChannel()
                             .equals(Fields.SPECIFIC_CHANNEL)) {
            return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
        }
        return Criteria.ALL.getValue();
    }

    // Done
    private WebhookPlatformModels.SubscriberConfigRequestV2 setSubscriberConfig(
            WebhookPlatformModels.SubscriberResponse subscriberResponse, String configType) {
        if (Objects.isNull(subscriberResponse)) {
            throw new FdkWebhookRegistrationError("Subscriber Config Response not found");
        }
        WebhookPlatformModels.SubscriberConfigRequestV2 subscriberConfig = new WebhookPlatformModels.SubscriberConfigRequestV2();
        subscriberConfig.setName(subscriberResponse.getName());
        subscriberConfig.setId(subscriberResponse.getId());
        if(configType.equals("rest")){
            subscriberConfig.setWebhookUrl(subscriberResponse.getWebhookUrl());
        }
        subscriberConfig.setProvider(subscriberResponse.getProvider());
        subscriberConfig.setAssociation(subscriberResponse.getAssociation());
        subscriberConfig.setStatus(subscriberResponse.getStatus().getPriority());
        if (subscriberResponse.getStatus()
                              .equals(WebhookPlatformModels.SubscriberStatus.inactive) || subscriberResponse.getStatus()
                                                                                                            .equals(WebhookPlatformModels.SubscriberStatus.blocked)) {
            subscriberConfig.setStatus(WebhookPlatformModels.SubscriberStatus.active.getPriority());
        }
        subscriberConfig.setAuthMeta(subscriberResponse.getAuthMeta());
        subscriberConfig.setEmailId(subscriberResponse.getEmailId());
        return subscriberConfig;
    }

    // Done
    private boolean isConfigurationUpdated(WebhookPlatformModels.SubscriberConfigRequestV2 subscriberConfig,
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

    // Done
    private boolean isEventDiff(WebhookPlatformModels.SubscriberResponse existingEvents,
                                WebhookPlatformModels.SubscriberConfigRequestV2 newEvents) {
        Set<Integer> existingEventIds = existingEvents.getEventConfigs()
                                                      .stream()
                                                      .map(WebhookPlatformModels.EventConfig::getId)
                                                      .collect(Collectors.toSet());
        List<Integer> uniques = new ArrayList<>(newEvents.getId());
        if (existingEventIds.size() > uniques.size()) {
            return true;
        }
        uniques.removeAll(existingEventIds);
        log.info("Unique Event IDs found  : " + uniques);
        return !uniques.isEmpty();
    }

    // Done
    private WebhookPublicModels.EventConfigResponse getEventConfig(List<EventMapProperties> eventMap) {
        try {
            List<WebhookPublicModels.EventConfigBase> eventConfigs = new ArrayList<>();
            eventMap.forEach(event -> {
                String[] eventDetails = event.getName()
                                             .split("/");
                if (eventDetails.length != 2) {
                    throw new FdkInvalidWebhookConfig("Invalid webhook event map key. Invalid key: " + event.getName());
                }
                WebhookPublicModels.EventConfigBase eventConfig = new WebhookPublicModels.EventConfigBase();
                eventConfig.setEventCategory(event.getCategory());
                eventConfig.setEventName(eventDetails[0]);
                eventConfig.setEventType(eventDetails[1]);
                eventConfig.setVersion(event.getVersion());
                eventConfigs.add(eventConfig);
            });
            PublicConfig publicConfig = new PublicConfig(extensionProperties.getApiKey(),
                                                         extensionProperties.getCluster());
            PublicClient publicClient = new PublicClient(publicConfig);
            WebhookPublicModels.EventConfigResponse eventConfigResponse = publicClient.webhook.queryWebhookEventDetails(
                    eventConfigs);
            log.debug("Webhook events config received: {}", objectMapper.writeValueAsString(eventConfigResponse));
            return eventConfigResponse;
        } catch (Exception e) {
            log.error("Error in querying Webhook Event Details", e);
            throw new FdkInvalidWebhookConfig("Error in Webhook Event Details due to Missing / Invalid Data");
        }
    }


    // Done
    private List<String> validateEvents(List<EventMapProperties> eventMap,
                                        WebhookPublicModels.EventConfigResponse eventConfigResponse) {
        List<String> errorWebhooks = new ArrayList<>();
        eventMap.forEach(eventSubscribed -> {
            if (!isPartOfEventConfig(eventConfigResponse, eventSubscribed)) {
                errorWebhooks.add(
                        "Not Found : " + eventSubscribed.getName() + "-" + eventSubscribed.getCategory() + "-" + eventSubscribed.getVersion());
            }
        });
        return errorWebhooks;
    }

    // Done
    private boolean isPartOfEventConfig(WebhookPublicModels.EventConfigResponse eventConfigResponse,
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

    // Done
    public void disableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        if (StringUtils.isNotEmpty(this.extensionProperties.getWebhook().getSubscribedSalesChannel())
                && !this.extensionProperties.getWebhook()
                                     .getSubscribedSalesChannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            SubscriberConfigContainer subscriberConfigContainer = getSubscriberConfig(platformClient);

            Map<String, WebhookPlatformModels.SubscriberResponse> subscriberResponseMap = new HashMap<String, WebhookPlatformModels.SubscriberResponse>();
            subscriberResponseMap.put("rest", subscriberConfigContainer.getRest());
            subscriberResponseMap.put("kafka", subscriberConfigContainer.getKafka());

            for(Map.Entry<String, WebhookPlatformModels.SubscriberResponse> subscriberResponseEntry : subscriberResponseMap.entrySet()){
                String configType = subscriberResponseEntry.getKey();
                WebhookPlatformModels.SubscriberResponse subscriberResponse = subscriberResponseEntry.getValue();

                WebhookPlatformModels.SubscriberConfigRequestV2 subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                List<WebhookPlatformModels.Events> events = new ArrayList<WebhookPlatformModels.Events>();
                subscriberResponse.getEventConfigs()
                        .forEach((eventConfig) -> {
                            WebhookPlatformModels.Events event = new WebhookPlatformModels.Events();
                            event.setSlug(eventConfig.getEventCategory() + "/" + eventConfig.getEventName() + "/" + eventConfig.getEventType() + "/v" + eventConfig.getVersion());
                            if(configType.equals("kafka")){
                                event.setTopic(eventConfig.getSubscriberEventMapping().getTopic());
                            }
                            events.add(event);
                        });
                subscriberConfig.setEvents(events);

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
                platformClient.webhook.updateSubscriberV2(subscriberConfig);
            }
            log.info("Webhook disabled for Sales Channel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred during Disable Webhook Event : ", e);
            throw new FdkWebhookRegistrationError("Failed to add Sales Channel Webhook. Reason: " + e.getMessage());
        }
    }

    // Done
    private String getCriteria(List<String> applicationIds) {
        return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
    }

    // Done
    public void enableSalesChannelWebhook(PlatformClient platformClient, String applicationId) {
        if (!this.isInitialized) {
            throw new FdkInvalidWebhookConfig(Fields.WEBHOOK_NOT_INITIALISED_ERROR);
        }
        if (StringUtils.isNotEmpty(this.extensionProperties.getWebhook().getSubscribedSalesChannel())
                && !this.extensionProperties.getWebhook()
                                     .getSubscribedSalesChannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            SubscriberConfigContainer subscriberConfigContainer = getSubscriberConfig(platformClient);

            Map<String, WebhookPlatformModels.SubscriberResponse> subscriberResponseMap = new HashMap<String, WebhookPlatformModels.SubscriberResponse>();
            subscriberResponseMap.put("rest", subscriberConfigContainer.getRest());
            subscriberResponseMap.put("kafka", subscriberConfigContainer.getKafka());

            for(Map.Entry<String, WebhookPlatformModels.SubscriberResponse> subscriberResponseEntry : subscriberResponseMap.entrySet()){
                String configType = subscriberResponseEntry.getKey();
                WebhookPlatformModels.SubscriberResponse subscriberResponse = subscriberResponseEntry.getValue();

                WebhookPlatformModels.SubscriberConfigRequestV2 subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                List<WebhookPlatformModels.Events> events = new ArrayList<WebhookPlatformModels.Events>();
                subscriberResponse.getEventConfigs()
                        .forEach((eventConfig) -> {
                            WebhookPlatformModels.Events event = new WebhookPlatformModels.Events();
                            event.setSlug(eventConfig.getEventCategory() + "/" + eventConfig.getEventName() + "/" + eventConfig.getEventType() + "/v" + eventConfig.getVersion());
                            if(configType.equals("kafka")){
                                event.setTopic(eventConfig.getSubscriberEventMapping().getTopic());
                            }
                            events.add(event);
                        });
                subscriberConfig.setEvents(events);
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
                platformClient.webhook.updateSubscriberV2(subscriberConfig);
            }

            log.info("Webhook enabled for sales channel: " + applicationId);
        } catch (Exception e) {
            log.error("Exception occurred during Enable Webhook event : ", e);
            throw new FdkWebhookRegistrationError("Failed to add Sales Channel Webhook. Reason: " + e.getMessage());
        }
    }

    // Done
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

    // Done
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
