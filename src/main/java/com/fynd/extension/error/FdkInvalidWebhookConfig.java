package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkInvalidWebhookConfig extends RuntimeException {

    public FdkInvalidWebhookConfig(@NonNull String message) {
        super(message);
    }
}
