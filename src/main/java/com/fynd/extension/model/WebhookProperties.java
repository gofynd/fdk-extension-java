package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WebhookProperties {

    private String api_path;

    private String notification_email;

    private String subscribed_saleschannel;

    private List<EventMapProperties> event_map;

    private Boolean subscribe_on_install;
}