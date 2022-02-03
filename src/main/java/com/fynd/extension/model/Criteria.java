package com.fynd.extension.model;

public enum Criteria {
    SPECIFIC("SPECIFIC-EVENTS"),
    ALL("ALL"),
    EMPTY("EMPTY");

    private final String name;

    Criteria(String value) {
        name = value;
    }

    public String getValue() {
        return name;
    }
}
