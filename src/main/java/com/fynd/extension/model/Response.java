package com.fynd.extension.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class Response {
    boolean success;
    String error;

    public Response(boolean success)
    {
        this.success = success;
    }
}
