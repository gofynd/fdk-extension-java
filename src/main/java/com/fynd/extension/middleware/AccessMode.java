package com.fynd.extension.middleware;

public enum AccessMode {
    OFFLINE("offline"),
    ONLINE("online");

    private final String name;

    AccessMode(String value) {
        name = value;
    }

    public String getName() {
        return name;
    }
}
