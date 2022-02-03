package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkWebhookHandlerNotFound extends RuntimeException{
    public FdkWebhookHandlerNotFound(@NonNull String message)
    {
        super(message);
    }
}

