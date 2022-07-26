package com.fynd.extension.middleware;

import com.fynd.extension.model.ExtensionDetailsDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ClientCall {

    @GET("/service/panel/partners/v1.0/extensions/details/{api_key}")
    Call<ExtensionDetailsDTO> getExtensionDetails(@Path("api_key")  String apiKey);
}
