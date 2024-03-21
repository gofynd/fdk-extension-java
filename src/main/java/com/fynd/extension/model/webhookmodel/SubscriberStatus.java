package com.fynd.extension.model.webhookmodel;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum SubscriberStatus {


    active("active"),

    inactive("inactive"),

    blocked("blocked");


    private String priority;
    SubscriberStatus(String priority) {
        this.priority = priority;
    }

    @JsonValue
    public String getPriority() {
        return priority;
    }

}