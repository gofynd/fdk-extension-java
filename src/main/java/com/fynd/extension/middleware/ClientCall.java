package com.fynd.extension.middleware;

import com.fynd.extension.model.ExtensionDetailsDTO;
import com.fynd.extension.model.webhookmodel.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ClientCall {

    @GET("/service/panel/partners/v1.0/extensions/details/{api_key}")
        Call<ExtensionDetailsDTO> getExtensionDetails(@Path("api_key")  String apiKey);

    @POST ("/service/common/webhook/v1.0/events/query-event-details")
    Call<EventConfigResponse> queryWebhookEventDetails(@Body List<EventConfigBase> payload);
}

