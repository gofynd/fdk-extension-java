package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class WebhookProperties {

    String api_path;
    String notification_email;
    String subscribed_saleschannel;
    HashMap<String, String> event_map;
    Boolean subscribe_on_install;

}

