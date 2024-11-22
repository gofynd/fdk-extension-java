package com.fynd.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.error.*;
import com.fynd.extension.middleware.ClientCall;
import com.fynd.extension.middleware.EventHandler;
import com.fynd.extension.middleware.PlatformClientCall;
import com.fynd.extension.middleware.RetryInterceptor;
import com.fynd.extension.model.Criteria;
import com.fynd.extension.model.EventMapProperties;
import com.fynd.extension.model.ExtensionProperties;
import com.fynd.extension.model.WebhookProperties;
import com.fynd.extension.middleware.ExtensionInterceptor;
import com.fynd.extension.model.webhookmodel.*;
import com.sdk.common.RequestSignerInterceptor;
import com.sdk.common.RetrofitServiceFactory;
import com.sdk.platform.AccessTokenInterceptor;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Response;

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

    private Map<String, Map<String, EventMapProperties>> allEventMap;

    EventConfigResponse eventConfigData;

    ClientCall getClientCallApiList(){
        RetrofitServiceFactory retrofitServiceFactory = new RetrofitServiceFactory();
        List<Interceptor> interceptorList = new ArrayList<>();
        interceptorList.add(new ExtensionInterceptor(extensionProperties));
        interceptorList.add(new RequestSignerInterceptor());
        interceptorList.add(new RetryInterceptor());
        ClientCall clientCall = retrofitServiceFactory.createService(extensionProperties.getCluster(), ClientCall.class,
                interceptorList);
        return clientCall;
    }

    PlatformClientCall getPlatformClientCallApiList(PlatformConfig platformConfig){
        RetrofitServiceFactory retrofitServiceFactory = new RetrofitServiceFactory();
        List<Interceptor> interceptorList = new ArrayList<>();
        interceptorList.add(new AccessTokenInterceptor(platformConfig));
        interceptorList.add(new RequestSignerInterceptor());
        PlatformClientCall platformClientCall = retrofitServiceFactory.createService(extensionProperties.getCluster(), PlatformClientCall.class,
                interceptorList);
        return platformClientCall;
    }

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
        boolean subscriberSyncedForAllProvider;

        try{
            subscriberSyncedForAllProvider = syncSubscriberConfigForAllProviders(platformClient, subscriberResponseContainer);
        } catch(IOException e){
            log.error("Exception occurred during Webhook Sync : ", e);
            throw new FdkWebhookRegistrationError("Failed to sync webhook events. Reason: " + e.getMessage());
        }

        if(!subscriberSyncedForAllProvider){
            subscriberResponseContainer = getSubscriberConfig(platformClient);
            syncSubscriberConfig(subscriberResponseContainer.getRest(), "rest", platformClient, enableWebhooks);
            syncSubscriberConfig(subscriberResponseContainer.getKafka(), "kafka", platformClient, enableWebhooks);
            syncSubscriberConfig(subscriberResponseContainer.getPubSub(), "pub_sub", platformClient, enableWebhooks);
            syncSubscriberConfig(subscriberResponseContainer.getSqs(), "sqs", platformClient, enableWebhooks);
            syncSubscriberConfig(subscriberResponseContainer.getEventBridge(), "event_bridge", platformClient, enableWebhooks);
            syncSubscriberConfig(subscriberResponseContainer.getTemporal(), "temporal", platformClient, enableWebhooks);
        }
    }

    void syncSubscriberConfig(SubscriberResponse subscriberResponse, String configType, PlatformClient platformClient, Boolean enableWebhooks) {
        try {

            SubscriberConfigRequestV2 subscriberConfig = new SubscriberConfigRequestV2();
            if (Objects.isNull(subscriberResponse) && Objects.nonNull(this.webhookProperties)) {
                if(this.allEventMap.get(configType).isEmpty()){
                    return;
                }
                subscriberConfig.setName(this.extensionProperties.getApiKey());
                if(configType.equals("rest")){
                    subscriberConfig.setWebhookUrl(
                            getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath()));
                }
                subscriberConfig.setStatus(SubscriberStatus.active);
                subscriberConfig.setEmailId(this.webhookProperties.getNotificationEmail());
                subscriberConfig.setEvents(getEventList(this.allEventMap.get(configType), configType));
                subscriberConfig.setProvider(configType);

                Association association = new Association();
                association.setCompanyId(Integer.parseInt(platformClient.getConfig()
                                                                        .getCompanyId()));
                association.setCriteria(getCriteria(this.webhookProperties, Collections.emptyList()));
                association.setApplicationId(new ArrayList<>());
                subscriberConfig.setAssociation(association);

                subscriberConfig.setAuthMeta(
                        new AuthMeta(Fields.HMAC, this.extensionProperties.getApiSecret()));
                if (enableWebhooks != null) {
                    if (enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(SubscriberStatus.active);
                    } else {
                        subscriberConfig.setStatus(SubscriberStatus.inactive);
                    }
                }
                this.registerSubscriberConfig(platformClient.getConfig(), subscriberConfig);
                log.info("Webhook Config Details Registered");
            } else {
                log.info("Webhook config on platform side for company id : " + platformClient.getConfig()
                        .getCompanyId() + " with config : " +
                        objectMapper.writeValueAsString(subscriberResponse));
                subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                subscriberConfig.setEvents(getEventList(this.allEventMap.get(configType), configType));
                if (enableWebhooks != null) {
                    if (enableWebhooks.equals(Boolean.TRUE)) {
                        subscriberConfig.setStatus(SubscriberStatus.active);
                    } else {
                        subscriberConfig.setStatus(SubscriberStatus.inactive);
                    }
                }
                if (isConfigurationUpdated(subscriberConfig, this.webhookProperties) || isEventDiff(
                        subscriberResponse, subscriberConfig) || subscriberResponse.getStatus()
                        .equals(SubscriberStatus.inactive)) {
                    this.updateSubscriberConfig(platformClient.getConfig(), subscriberConfig);
                    log.info("Webhook Config Details updated");
                }
            }

        } catch (IOException e) {
            log.error("Exception occurred during Webhook Sync : ", e);
            throw new FdkWebhookRegistrationError("Failed to sync webhook events. Reason: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    boolean syncSubscriberConfigForAllProviders(PlatformClient platformClient, SubscriberConfigContainer subscriberResponseContainer) throws IOException {
        SubscriberConfigRequestV3 payload = createRegisterPayloadData(subscriberResponseContainer);
        PlatformConfig platformConfig = platformClient.getConfig();
        Response<SubscriberConfigResponseV3> res;
        res = getPlatformClientCallApiList(platformConfig).registerSubscriberToEventV3(platformConfig.getCompanyId(), payload).execute();
        if(!res.isSuccessful()){
            if(res.code() != 404){
                String errorMessage = String.format("Request to %s failed with status code %d: %s",
                        res.raw().request().url(),
                        res.code(),
                        res.errorBody().string());
                log.error(errorMessage);
                return true;
            }else{
                return false;
            }
        }
        return true;
    }

    SubscriberConfigRequestV3 createRegisterPayloadData(SubscriberConfigContainer subscriberResponseContainer){
        SubscriberConfigRequestV3 payload = new SubscriberConfigRequestV3();
        WebhookConfig config = new WebhookConfig();
        Association association = new Association();
        config.setAssociation(association);
        payload.setWebhookConfig(config);
        config.setNotificationEmail(this.webhookProperties.getNotificationEmail());
        config.setName(this.extensionProperties.getApiKey());
        config.setStatus(SubscriberStatus.active);
        association.setExtensionId(this.extensionProperties.getApiKey());
        association.setCriteria(getCriteria(this.webhookProperties, Collections.emptyList()));
        association.setApplicationId(new ArrayList<>());
        Map<String, EventMap> eventMap = new HashMap<>();
        config.setEventMap(eventMap);
        EventMap restMap = new EventMap();
        AuthMeta authMeta = new AuthMeta();
        restMap.setAuthMeta(authMeta);
        restMap.setWebhookUrl(getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath()));
        restMap.getAuthMeta().setSecret(this.extensionProperties.getApiSecret());
        restMap.getAuthMeta().setType(Fields.HMAC);
        EventMap kafkaMap = new EventMap();
        EventMap pubSubMap = new EventMap();
        EventMap temporalMap = new EventMap();
        EventMap sqsMap = new EventMap();
        EventMap eventBridgeMap = new EventMap();
        restMap.setEvents(new ArrayList<>());
        kafkaMap.setEvents(new ArrayList<>());
        pubSubMap.setEvents(new ArrayList<>());
        temporalMap.setEvents(new ArrayList<>());
        sqsMap.setEvents(new ArrayList<>());
        eventBridgeMap.setEvents(new ArrayList<>());
        // Check if the subscribed sales channel is "specific"
        if ("specific".equals(this.webhookProperties.getSubscribedSalesChannel())) {
            //Every provider has same association. Get the first one.
            SubscriberResponse firstConfig = null;

            if (subscriberResponseContainer.getRest() != null) {
                firstConfig = subscriberResponseContainer.getRest();
            } else if (subscriberResponseContainer.getKafka() != null) {
                firstConfig = subscriberResponseContainer.getKafka();
            } else if (subscriberResponseContainer.getPubSub() != null) {
                firstConfig = subscriberResponseContainer.getPubSub();
            } else if (subscriberResponseContainer.getSqs() != null) {
                firstConfig = subscriberResponseContainer.getSqs();
            } else if (subscriberResponseContainer.getEventBridge() != null) {
                firstConfig = subscriberResponseContainer.getEventBridge();
            } else if (subscriberResponseContainer.getTemporal() != null) {
                firstConfig = subscriberResponseContainer.getTemporal();
            }

            // If a valid firstConfig is found and its association criteria is SPECIFIC
            if (firstConfig != null && Criteria.SPECIFIC.getValue().equals(firstConfig.getAssociation().getCriteria())) {
                payload.getWebhookConfig().setAssociation(firstConfig.getAssociation());
            }
        }

        if(Criteria.SPECIFIC.getValue().equals(payload.getWebhookConfig().getAssociation().getCriteria())){
            String isMarketplace = this.webhookProperties.getMarketplace() ? "marketplace" : null;
            restMap.setType(isMarketplace);
            kafkaMap.setType(isMarketplace);
            pubSubMap.setType(isMarketplace);
            temporalMap.setType(isMarketplace);
            sqsMap.setType(isMarketplace);
            eventBridgeMap.setType(isMarketplace);
        }
        for(EventMapProperties event: webhookProperties.getEventMap()) {
            EventV3 providerEvent = new EventV3();
            String[] eventDetails = event.getName().split("/");
            providerEvent.setEventCategory(event.getCategory());
            providerEvent.setEventName(eventDetails[0]);
            providerEvent.setEventType(eventDetails[1]);
            providerEvent.setVersion(event.getVersion());
            providerEvent.setFilters(event.getFilters());
            providerEvent.setReducer(event.getReducer());
            switch (event.getProvider()) {
                case "rest":
                    eventMap.put("rest", restMap);
                    restMap.getEvents().add(providerEvent);
                    break;
                case "kafka":
                    eventMap.put("kafka", kafkaMap);
                    providerEvent.setTopic(event.getTopic());
                    kafkaMap.getEvents().add(providerEvent);
                    break;
                case "pub_sub":
                    eventMap.put("pub_sub", pubSubMap);
                    providerEvent.setTopic(event.getTopic());
                    pubSubMap.getEvents().add(providerEvent);
                    break;
                case "sqs":
                    eventMap.put("sqs", sqsMap);
                    providerEvent.setQueue(event.getQueue());
                    sqsMap.getEvents().add(providerEvent);
                    break;
                case "event_bridge":
                    eventMap.put("event_bridge", eventBridgeMap);
                    providerEvent.setEventBridgeName(event.getEventBridgeName());
                    eventBridgeMap.getEvents().add(providerEvent);
                    break;
                case "temporal":
                    eventMap.put("temporal", temporalMap);
                    providerEvent.setQueue(event.getQueue());
                    providerEvent.setWorkflowName(event.getWorkflowName());
                    temporalMap.getEvents().add(providerEvent);
                    break;
            }

        }
        return payload;
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

        //3. Validate Type
        if (webhookProperties.getMarketplace() != null && webhookProperties.getMarketplace() && !"specific".equals(webhookProperties.getSubscribedSalesChannel())) {
            throw new FdkInvalidWebhookConfig(Fields.INVALID_TYPE);
        }

        //4. Validate Event Map
        if (CollectionUtils.isEmpty(webhookProperties.getEventMap())) {
            throw new FdkInvalidWebhookConfig(Fields.MISSING_EVENTS);
        }

        if (Objects.isNull(webhookProperties.getSubscribeOnInstall())) {
            webhookProperties.setSubscribeOnInstall(true);
        } else {
            webhookProperties.setSubscribeOnInstall(webhookProperties.getSubscribeOnInstall());
        }

        allEventMap = new HashMap<String, Map<String, EventMapProperties>>();
        allEventMap.put("rest", new HashMap<>());
        allEventMap.put("kafka", new HashMap<>());
        allEventMap.put("pub_sub", new HashMap<>());
        allEventMap.put("sqs", new HashMap<>());
        allEventMap.put("event_bridge", new HashMap<>());
        allEventMap.put("temporal", new HashMap<>());


        //4. Validate Webhook eventMap details
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
            List<String> allowedProviders = Arrays.asList("kafka", "rest", "pub_sub", "temporal", "sqs", "event_bridge");
            if (!allowedProviders.contains(event.getProvider())) {
                throw new FdkInvalidWebhookConfig("Invalid provider value in webhook event " + event.getName() +
                        ", allowed values are " + Arrays.toString(allowedProviders.toArray()));
            }

            if ("rest".equals(event.getProvider()) && StringUtils.isEmpty(event.getHandler())) {
                throw new FdkInvalidWebhookConfig("Missing handler in webhook event " + event.getName());
            } else if (("kafka".equals(event.getProvider()) || "pub_sub".equals(event.getProvider()))  && StringUtils.isEmpty(event.getTopic())) {
                throw new FdkInvalidWebhookConfig("Missing topic in webhook event " + event.getName());
            } else if ( ("temporal".equals(event.getProvider()) || "sqs".equals(event.getProvider()) )   && StringUtils.isEmpty(event.getQueue())) {
                throw new FdkInvalidWebhookConfig("Missing queue in webhook event " + event.getName());
            } else if ("temporal".equals(event.getProvider())  && StringUtils.isEmpty(event.getWorkflowName())) {
                throw new FdkInvalidWebhookConfig("Missing workflow_name in webhook event " + event.getName());
            }else if ("event_bridge".equals(event.getProvider())  && StringUtils.isEmpty(event.getEventBridgeName())) {
                throw new FdkInvalidWebhookConfig("Missing event_bridge_name in webhook event " + event.getName());
            }
            allEventMap.get(event.getProvider()).put(event.getCategory() + "/" + event.getName() + "/v" + event.getVersion(), event);
        }

        //5. Validate Webhook events
        this.eventConfigData = getEventConfig(webhookProperties.getEventMap());
        List<String> errorWebhooks = validateEvents(webhookProperties.getEventMap(), this.eventConfigData);
        if (!errorWebhooks.isEmpty()) {
            throw new FdkInvalidWebhookConfig("Webhooks events errors" + errorWebhooks);
        }
        this.isInitialized = true;
        log.info("Webhook registry initialized");
    }

    private SubscriberConfigContainer getSubscriberConfig(PlatformClient platformClient) {
        try {
            SubscriberConfigList subscriberConfigList = getPlatformClientCallApiList(platformClient.getConfig()).getSubscribersByExtensionId(platformClient.getConfig().getCompanyId(), this.extensionProperties.getApiKey(), null, null).execute().body();
            SubscriberConfigContainer subscriberConfigContainer = new SubscriberConfigContainer();

            if (Objects.nonNull(subscriberConfigList) && CollectionUtils.isNotEmpty(subscriberConfigList.getItems())) {

                for(SubscriberResponse subscriberResponse : subscriberConfigList.getItems()){

                    switch (subscriberResponse.getProvider()) {
                        case "rest":
                            subscriberConfigContainer.setRest(subscriberResponse);
                            break;
                        case "kafka":
                            subscriberConfigContainer.setKafka(subscriberResponse);
                            break;
                        case "pub_sub":
                            subscriberConfigContainer.setPubSub(subscriberResponse);
                            break;
                        case "sqs":
                            subscriberConfigContainer.setSqs(subscriberResponse);
                            break;
                        case "event_bridge":
                            subscriberConfigContainer.setEventBridge(subscriberResponse);
                            break;
                        case "temporal":
                            subscriberConfigContainer.setTemporal(subscriberResponse);
                            break;
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

    private String getWebhookUrl(String baseURL, String apiPath) {
        return baseURL + apiPath;
    }
  
    /**
     * @param eventMap - map of user provided configuration
     * @param configType - provider type - kafka or rest
     * @return - returns list of event configured by user to send in register or update subscriberConfig api
     */
    private List<Events> getEventList(Map<String, EventMapProperties> eventMap, String configType){
        List<Events> eventList = new ArrayList<Events>();

        for(Map.Entry<String, EventMapProperties> event : eventMap.entrySet()){
            Events eventData = new Events();
            eventData.setSlug(event.getKey());
            eventData.setFilters(event.getValue().getFilters());
            eventData.setReducer(event.getValue().getReducer());
            if(configType.equals("kafka") || configType.equals("pub_sub")){
                eventData.setTopic(event.getValue().getTopic());
            }
            if(configType.equals("temporal") || configType.equals("sqs")){
                eventData.setQueue(event.getValue().getQueue());
            }
            if(configType.equals("temporal")){
                eventData.setWorkflowName(event.getValue().getWorkflowName());
            }
            if(configType.equals("event_bridge")){
                eventData.setEventBridgeName(event.getValue().getEventBridgeName());
            }
            eventList.add(eventData);
        }
        log.info("Events opted for " + configType + " : " + eventList);
        return eventList;
    }

    private String getCriteria(WebhookProperties webhookProperties, List<String> applicationIds) {
        if (StringUtils.isNotEmpty(this.extensionProperties.getWebhook().getSubscribedSalesChannel())
                && webhookProperties.getSubscribedSalesChannel()
                             .equals(Fields.SPECIFIC_CHANNEL)) {
            return CollectionUtils.isEmpty(applicationIds) ? Criteria.EMPTY.getValue() : Criteria.SPECIFIC.getValue();
        }
        return Criteria.ALL.getValue();
    }

    private SubscriberConfigRequestV2 setSubscriberConfig(
            SubscriberResponse subscriberResponse, String configType) {
        if (Objects.isNull(subscriberResponse)) {
            throw new FdkWebhookRegistrationError("Subscriber Config Response not found");
        }
        SubscriberConfigRequestV2 subscriberConfig = new SubscriberConfigRequestV2();
        subscriberConfig.setName(subscriberResponse.getName());
        subscriberConfig.setId(subscriberResponse.getId());
        if(configType.equals("rest")){
            subscriberConfig.setWebhookUrl(subscriberResponse.getWebhookUrl());
        }
        subscriberConfig.setProvider(subscriberResponse.getProvider());
        subscriberConfig.setAssociation(subscriberResponse.getAssociation());
        subscriberConfig.setStatus(subscriberResponse.getStatus());
        if (subscriberResponse.getStatus()
                              .equals(SubscriberStatus.inactive) || subscriberResponse.getStatus().equals(SubscriberStatus.blocked)) {
            subscriberConfig.setStatus(SubscriberStatus.active);
        }
        subscriberConfig.setAuthMeta(subscriberResponse.getAuthMeta());
        subscriberConfig.setEmailId(subscriberResponse.getEmailId());
        return subscriberConfig;
    }

    private boolean isConfigurationUpdated(SubscriberConfigRequestV2 subscriberConfig,
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
                                                                                      .getCriteria() + " to : " + this.associationCriteria);
            subscriberConfig.getAssociation()
                            .setCriteria(this.associationCriteria);
            updated = true;
        }

        if (!webhookProperties.getNotificationEmail()
                              .equals(subscriberConfig.getEmailId())) {
            log.info(
                    "Webhook notification email updated from : " + subscriberConfig.getEmailId() + " to : " + webhookProperties.getNotificationEmail());
            subscriberConfig.setEmailId(webhookProperties.getNotificationEmail());
            updated = true;
        }

        this.webhookUrl = getWebhookUrl(this.extensionProperties.getBaseUrl(), this.webhookProperties.getApiPath());
        if (subscriberConfig.getProvider().equals("rest") && !this.webhookUrl.equals(subscriberConfig.getWebhookUrl())) {
            log.info("Webhook URL updated from : " + subscriberConfig.getWebhookUrl() + " to : " + this.webhookUrl);
            subscriberConfig.setWebhookUrl(this.webhookUrl);
            updated = true;
        }

        if (this.associationCriteria == Criteria.SPECIFIC.getValue()) {
            if ("marketplace".equals(subscriberConfig.getType()) && !webhookProperties.getMarketplace()) {
                log.debug(String.format("Type updated from %s to null", subscriberConfig.getType()));
                subscriberConfig.setType(null);
                updated = true;
            } else if ((subscriberConfig.getType() == null || !"marketplace".equals(subscriberConfig.getType())) && webhookProperties.getMarketplace()) {
                log.debug(String.format("Type updated from %s to marketplace", subscriberConfig.getType()));
                subscriberConfig.setType("marketplace");
                updated = true;
            }
        } else {
            if ("marketplace".equals(subscriberConfig.getType())) {
                log.debug(String.format("Type updated from %s to null", subscriberConfig.getType()));
                subscriberConfig.setType(null);
                updated = true;
            }
        }

        return updated;
    }

    private boolean isEventDiff(SubscriberResponse existingEvents,
                                SubscriberConfigRequestV2 newEvents) {
        // Check if the provider is 'kafka' and perform the topic equality check
        List<EventConfig> existingEventList = existingEvents.getEventConfigs();
        for (Events event : newEvents.getEvents()) {
            EventConfig existingEvent = existingEventList.stream()
                    .filter(e -> event.getSlug().equals(e.getEventCategory() + "/" + e.getEventName() + "/" + e.getEventType() + "/v" + e.getVersion()))
                    .findFirst()
                    .orElse(null);
            if (existingEvent != null) {
                if(!event.getFilters().equals(existingEvent.getFilters())){
                    return true;
                }else if (!event.getReducer().equals(existingEvent.getReducer())){
                    return true;
                }

                switch (newEvents.getProvider()) {
                    case "kafka", "pub_sub":
                        if(!event.getTopic().equals(existingEvent.getSubscriberEventMapping().getBroadcasterConfig().getTopic())){
                            return true;
                        }
                        break;
                    case "temporal":
                        if(!event.getQueue().equals(existingEvent.getSubscriberEventMapping().getBroadcasterConfig().getQueue())){
                            return true;
                        }else if(!event.getWorkflowName().equals(existingEvent.getSubscriberEventMapping().getBroadcasterConfig().getWorkflowName())){
                            return true;
                        }
                        break;
                    case "sqs":
                        if(!event.getQueue().equals(existingEvent.getSubscriberEventMapping().getBroadcasterConfig().getQueue())){
                            return true;
                        }
                        break;
                    case "event_bridge":
                        if(!event.getEventBridgeName().equals(existingEvent.getSubscriberEventMapping().getBroadcasterConfig().getEventBridgeName())){
                            return true;
                        }
                        break;
                }
            }
        }



        Set<String> existingEventSlugs = existingEvents.getEventConfigs()
                                                        .stream()
                                                        .map(eventConfig -> {
                                                            String slug = eventConfig.getEventCategory() + "/" +
                                                                            eventConfig.getEventName() + "/" +
                                                                            eventConfig.getEventType() + "/v" +
                                                                            eventConfig.getVersion();
                                                            return slug;
                                                        })
                                                        .collect(Collectors.toSet());
        List<String> uniques = new ArrayList<>(newEvents.getEvents()
                .stream()
                .map(Events::getSlug)
                .toList());
        if (existingEventSlugs.size() > uniques.size()) {
            return true;
        }
        uniques.removeAll(existingEventSlugs);
        log.info("Unique Event IDs found  : " + uniques);
        return !uniques.isEmpty();
    }
 
    private EventConfigResponse getEventConfig(List<EventMapProperties> eventMap) {
        try {
            List<EventConfigBase> eventConfigs = new ArrayList<>();
            eventMap.forEach(event -> {
                String[] eventDetails = event.getName()
                                             .split("/");
                if (eventDetails.length != 2) {
                    throw new FdkInvalidWebhookConfig("Invalid webhook event map key. Invalid key: " + event.getName());
                }
                EventConfigBase eventConfig = new EventConfigBase();
                eventConfig.setEventCategory(event.getCategory());
                eventConfig.setEventName(eventDetails[0]);
                eventConfig.setEventType(eventDetails[1]);
                eventConfig.setVersion(event.getVersion());
                eventConfigs.add(eventConfig);
            });
            EventConfigResponse eventConfigResponse = getClientCallApiList().queryWebhookEventDetails(eventConfigs).execute().body();
                    log.debug("Webhook events config received: {}", objectMapper.writeValueAsString(eventConfigResponse));
            return eventConfigResponse;
        } catch (Exception e) {
            log.error("Error in querying Webhook Event Details", e);
            throw new FdkInvalidWebhookConfig("Error in Webhook Event Details due to Missing / Invalid Data");
        }
    }

    private List<String> validateEvents(List<EventMapProperties> eventMap,
                                        EventConfigResponse eventConfigResponse) {
        List<String> errorWebhooks = new ArrayList<>();
        eventMap.forEach(eventSubscribed -> {
            if (!isPartOfEventConfig(eventConfigResponse, eventSubscribed)) {
                errorWebhooks.add(
                        "Not Found : " + eventSubscribed.getName() + "-" + eventSubscribed.getCategory() + "-" + eventSubscribed.getVersion());
            }
        });
        return errorWebhooks;
    }

    private boolean isPartOfEventConfig(EventConfigResponse eventConfigResponse,
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


    Response<SubscriberConfigResponse> registerSubscriberConfig(PlatformConfig platformConfig, SubscriberConfigRequestV2 subscriberConfig) throws IOException {
        if(subscriberConfig.getEvents().isEmpty()){
            return null;
        }
        
        Response<SubscriberConfigResponse> res;
        res = getPlatformClientCallApiList(platformConfig).registerSubscriberToEventV2(platformConfig.getCompanyId(), subscriberConfig).execute();
        if(!res.isSuccessful()){
            if(res.code() == 404){
                if(!subscriberConfig.getProvider().equals("rest")){
                    log.debug("Webhook Subscriber Config type " + subscriberConfig.getProvider() + " is not supported with current fp version");
                    return res;
                }
                res = getPlatformClientCallApiList(platformConfig).registerSubscriberToEvent(platformConfig.getCompanyId(), convertReqBodyFromV2ToV1(subscriberConfig)).execute();
            }
        }
        if(!res.isSuccessful()){
            String errorMessage = String.format("Request to %s failed with status code %d: %s",
                res.raw().request().url(),
                res.code(),
                res.errorBody().string());
            log.error(errorMessage);
        }

        return res;
    }

    Response<SubscriberConfigResponse> updateSubscriberConfig(PlatformConfig platformConfig, SubscriberConfigRequestV2 subscriberConfig) throws IOException {
        if(subscriberConfig.getEvents().isEmpty()){
            subscriberConfig.setStatus(SubscriberStatus.inactive);
            subscriberConfig.setEvents(null); // Don't send events array in request
        }
        
        Response<SubscriberConfigResponse> res;
        res = this.getPlatformClientCallApiList(platformConfig).updateSubscriberV2(platformConfig.getCompanyId(), subscriberConfig).execute();
        if(!res.isSuccessful()){
            if(res.code() == 404){
                if(!subscriberConfig.getProvider().equals("rest")){
                    log.debug("Webhook Subscriber Config type " + subscriberConfig.getProvider() + " is not supported with current fp version");
                    return res;
                }
                res = getPlatformClientCallApiList(platformConfig).updateSubscriberConfig(platformConfig.getCompanyId(), convertReqBodyFromV2ToV1(subscriberConfig)).execute();
            }
            
        }
        if(!res.isSuccessful()){
            String errorMessage = String.format("Request to %s failed with status code %d: %s",
                res.raw().request().url(),
                res.code(),
                res.errorBody().string());
            log.error(errorMessage);
        }
        
        return res;
    }

    SubscriberConfig convertReqBodyFromV2ToV1(SubscriberConfigRequestV2 subscriberConfigRequestV2){
        List<Integer> eventIds = Optional.ofNullable(subscriberConfigRequestV2.getEvents())
                                    .orElse(Collections.emptyList())
                                    .stream()
                                    .map(Events::getSlug)
                                    .map(this::convertSlugToId)
                                    .collect(Collectors.toList());

        SubscriberConfig subscriberConfig = new SubscriberConfig(
                subscriberConfigRequestV2.getId(),
                subscriberConfigRequestV2.getName(),
                subscriberConfigRequestV2.getWebhookUrl(),
                subscriberConfigRequestV2.getAssociation(),
                subscriberConfigRequestV2.getCustomHeaders(),
                subscriberConfigRequestV2.getStatus(),
                subscriberConfigRequestV2.getEmailId(),
                subscriberConfigRequestV2.getAuthMeta(),
                eventIds
        );
        return subscriberConfig;
    }

    private Integer convertSlugToId(String slug) {
        String[] parts = slug.split("/");
        String eventCategory = parts[0];
        String eventName = parts[1];
        String eventType = parts[2];
        String version = parts[3].substring(1); // Assuming the version prefix is 'v'
    
        return this.eventConfigData.getEventConfigs().stream()
                .filter(eventConfig -> eventConfig.getEventCategory().equals(eventCategory)
                        && eventConfig.getEventName().equals(eventName)
                        && eventConfig.getEventType().equals(eventType)
                        && eventConfig.getVersion().equals(version))
                .findFirst()
                .map(EventConfig::getId)
                .orElseThrow(() -> new FdkWebhookProcessError("EventId not found for event " + eventCategory + "/" + eventName + "/" + eventType + "/v" + version + "." + "Check if event is valid."));
    }

    public Map<String, SubscriberResponse> createSubscriberResponseMap(SubscriberConfigContainer subscriberConfigContainer) {
        Map<String, SubscriberResponse> subscriberResponseMap = new HashMap<>();

        if (subscriberConfigContainer.getRest() != null) {
            subscriberResponseMap.put("rest", subscriberConfigContainer.getRest());
        }
        if (subscriberConfigContainer.getKafka() != null) {
            subscriberResponseMap.put("kafka", subscriberConfigContainer.getKafka());
        }
        if (subscriberConfigContainer.getSqs() != null) {
            subscriberResponseMap.put("sqs", subscriberConfigContainer.getSqs()); // Corrected from Kafka to Sqs
        }
        if (subscriberConfigContainer.getEventBridge() != null) {
            subscriberResponseMap.put("event_bridge", subscriberConfigContainer.getEventBridge()); // Corrected from Kafka to EventBridge
        }
        if (subscriberConfigContainer.getPubSub() != null) {
            subscriberResponseMap.put("pub_sub", subscriberConfigContainer.getPubSub()); // Corrected from Kafka to PubSub
        }

        return subscriberResponseMap;
    }

    public void applyBroadcasterConfig(Events event, String configType, BroadcasterConfig broadcasterConfig) {
        if(configType.equals("kafka") || configType.equals("pub_sub")){
            event.setTopic(broadcasterConfig.getTopic());
        }
        if(configType.equals("temporal") || configType.equals("sqs")){
            event.setQueue(broadcasterConfig.getQueue());
        }
        if(configType.equals("temporal")){
            event.setWorkflowName(broadcasterConfig.getWorkflowName());
        }
        if(configType.equals("event_bridge")){
            event.setEventBridgeName(broadcasterConfig.getEventBridgeName());
        }
    }

 
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

            Map<String, SubscriberResponse> subscriberResponseMap = createSubscriberResponseMap(subscriberConfigContainer);

            for(Map.Entry<String, SubscriberResponse> subscriberResponseEntry : subscriberResponseMap.entrySet()){
                String configType = subscriberResponseEntry.getKey();
                SubscriberResponse subscriberResponse = subscriberResponseEntry.getValue();

                SubscriberConfigRequestV2 subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                List<Events> events = new ArrayList<Events>();
                subscriberResponse.getEventConfigs()
                        .forEach((eventConfig) -> {
                            Events event = new Events();
                            event.setSlug(eventConfig.getEventCategory() + "/" + eventConfig.getEventName() + "/" + eventConfig.getEventType() + "/v" + eventConfig.getVersion());
                            BroadcasterConfig broadcasterConfig = eventConfig.getSubscriberEventMapping().getBroadcasterConfig();
                            applyBroadcasterConfig(event, configType, broadcasterConfig);
                            events.add(event);
                        });
                subscriberConfig.setEvents(events);

                if (Objects.nonNull(subscriberConfig.getAssociation()) && !CollectionUtils.isEmpty(
                        subscriberConfig.getAssociation()
                                .getApplicationId()) && subscriberConfig.getAssociation()
                        .getApplicationId()
                        .contains(applicationId)) {
                    subscriberConfig.getAssociation()
                            .getApplicationId()
                            .remove(applicationId);
                    subscriberConfig.getAssociation()
                            .setCriteria(getCriteria(subscriberConfig.getAssociation()
                                    .getApplicationId()));
                }

                if(Criteria.SPECIFIC.getValue().equals(subscriberConfig.getAssociation().getCriteria())){
                    String isMarketplace = this.webhookProperties.getMarketplace() ? "marketplace" : null;
                    subscriberConfig.setType(isMarketplace);
                }else {
                    subscriberConfig.setType(null);
                }
                this.updateSubscriberConfig(platformClient.getConfig(), subscriberConfig);
            }
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
        if (StringUtils.isNotEmpty(this.extensionProperties.getWebhook().getSubscribedSalesChannel())
                && !this.extensionProperties.getWebhook()
                                     .getSubscribedSalesChannel()
                                     .equals(Fields.SPECIFIC_CHANNEL)) {
            throw new FdkWebhookRegistrationError("subscribed_sales channel is not set to specific in webhook config");
        }
        try {
            SubscriberConfigContainer subscriberConfigContainer = getSubscriberConfig(platformClient);

            Map<String, SubscriberResponse> subscriberResponseMap = createSubscriberResponseMap(subscriberConfigContainer);

            for(Map.Entry<String, SubscriberResponse> subscriberResponseEntry : subscriberResponseMap.entrySet()){
                String configType = subscriberResponseEntry.getKey();
                SubscriberResponse subscriberResponse = subscriberResponseEntry.getValue();

                SubscriberConfigRequestV2 subscriberConfig = setSubscriberConfig(subscriberResponse, configType);
                List<Events> events = new ArrayList<Events>();
                subscriberResponse.getEventConfigs()
                        .forEach((eventConfig) -> {
                            Events event = new Events();
                            event.setSlug(eventConfig.getEventCategory() + "/" + eventConfig.getEventName() + "/" + eventConfig.getEventType() + "/v" + eventConfig.getVersion());
                            BroadcasterConfig broadcasterConfig = eventConfig.getSubscriberEventMapping().getBroadcasterConfig();
                            applyBroadcasterConfig(event, configType, broadcasterConfig);
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
                if(Criteria.SPECIFIC.getValue().equals(subscriberConfig.getAssociation().getCriteria())){
                    String isMarketplace = this.webhookProperties.getMarketplace() ? "marketplace" : null;
                    subscriberConfig.setType(isMarketplace);
                }
                this.updateSubscriberConfig(platformClient.getConfig(), subscriberConfig);
            }

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
                if(eventMap.getProvider().equals("kafka")){
                    continue;
                }
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
        String INVALID_TYPE = "marketplace is only allowed when subscribed_saleschannel is specific";
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
