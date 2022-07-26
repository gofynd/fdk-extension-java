package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WebhookProperties {

    private String apiPath;

    private String notificationEmail;

    private String subscribedSalesChannel;

    private List<EventMapProperties> eventMap;

    private Boolean subscribeOnInstall;
}