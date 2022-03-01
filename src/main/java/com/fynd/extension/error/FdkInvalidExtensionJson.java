package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkInvalidExtensionJson extends RuntimeException {

    public FdkInvalidExtensionJson(@NonNull String message) {
        super(message);
    }
}
