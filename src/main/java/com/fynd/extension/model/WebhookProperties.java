package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class WebhookProperties {

    private String apiPath;

    private String notificationEmail;

    private String subscribedSalesChannel;

    private Object customHeaders;

    private List<EventMapProperties> eventMap;

    private Boolean subscribeOnInstall;

    public String getSubscribedSalesChannel() {
        return subscribedSalesChannel;
    }

    public void setSubscribedSalesChannel(String subscribedSalesChannel) {
        this.subscribedSalesChannel = Optional.ofNullable(subscribedSalesChannel).orElse(null);
    }

//    public void setCustomHeaders(Object customHeaders) {
//        this.customHeaders = Optional.ofNullable(customHeaders).orElse(null);
//    }
//    public Object getCustomHeaders() {
//        return Optional.ofNullable(customHeaders).orElse(null);
//    }

}