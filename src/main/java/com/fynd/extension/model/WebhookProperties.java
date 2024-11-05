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

    private List<EventMapProperties> eventMap;

    private Boolean subscribeOnInstall;

    private Boolean marketplace;

    public String getSubscribedSalesChannel() {
        return subscribedSalesChannel;
    }

    public void setSubscribedSalesChannel(String subscribedSalesChannel) {
        this.subscribedSalesChannel = Optional.ofNullable(subscribedSalesChannel).orElse(null);
    }
}