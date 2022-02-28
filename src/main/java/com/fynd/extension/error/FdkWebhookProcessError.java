package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkWebhookProcessError extends RuntimeException {
    public FdkWebhookProcessError(@NonNull String message) {
        super(message);
    }
}
