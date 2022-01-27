package com.fynd.extension.middleware;

public interface EventHandler {

    void handle(String eventName, Object body, String companyId, String applicationId);
}
