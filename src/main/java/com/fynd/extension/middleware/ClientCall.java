package com.fynd.extension.middleware;

import com.fynd.extension.model.ExtensionDetailsDTO;
import com.fynd.extension.model.webhookmodel.*;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface ClientCall {

    @GET("/service/panel/partners/v1.0/extensions/details/{api_key}")
        Call<ExtensionDetailsDTO> getExtensionDetails(@Path("api_key")  String apiKey);

    @POST ("/service/platform/webhook/v2.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> registerSubscriberToEventV2(@Path("company_id") String companyId, @Body SubscriberConfigRequestV2 payload);

    @GET ("/service/platform/webhook/v1.0/company/{company_id}/extension/{extension_id}/subscriber/")
    Call<SubscriberConfigList> getSubscribersByExtensionId(@Path("company_id") String companyId, @Path("extension_id") String extensionId, @Query("page_no") Integer pageNo, @Query("page_size") Integer pageSize);

    @PUT ("/service/platform/webhook/v2.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> updateSubscriberV2(@Path("company_id") String companyId, @Body SubscriberConfigRequestV2 payload);

    @POST ("/service/common/webhook/v1.0/events/query-event-details")
    Call<EventConfigResponse> queryWebhookEventDetails(@Body List<EventConfigBase> payload);
}
