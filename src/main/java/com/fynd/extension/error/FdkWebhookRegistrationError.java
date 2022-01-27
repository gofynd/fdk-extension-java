package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkWebhookRegistrationError extends RuntimeException{
    public FdkWebhookRegistrationError(@NonNull String message)
    {
        super(message);
    }
}
