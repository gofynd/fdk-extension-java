package com.fynd.extension.error;

import org.springframework.lang.NonNull;

public class FdkClusterMetaMissing extends RuntimeException{

    public FdkClusterMetaMissing(@NonNull String message)
    {
        super(message);

    }

}
