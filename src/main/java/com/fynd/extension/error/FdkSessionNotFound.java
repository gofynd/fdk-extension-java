package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkSessionNotFound extends RuntimeException{

    public FdkSessionNotFound(@NonNull String message)
    {
        super(message);

    }

}
