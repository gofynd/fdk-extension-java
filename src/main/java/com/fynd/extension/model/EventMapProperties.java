package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventMapProperties {

    private String name;

    private String handler;

    private String category;

    private String version;

    private String provider;

    private String topic;

    private String queue;

    private String workflowName;

    private String accountId;

    private String eventBridgeName;

}
