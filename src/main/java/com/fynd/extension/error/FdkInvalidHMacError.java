package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkInvalidHMacError extends RuntimeException {
    public FdkInvalidHMacError(@NonNull String message) {
        super(message);
    }
}
