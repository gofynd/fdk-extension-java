package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkInvalidExtensionConfig extends RuntimeException {

    public FdkInvalidExtensionConfig(@NonNull String message) {
        super(message);
    }
}
