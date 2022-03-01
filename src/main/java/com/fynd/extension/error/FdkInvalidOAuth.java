package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkInvalidOAuth extends RuntimeException {

    public FdkInvalidOAuth(@NonNull String message) {
        super(message);
    }
}
