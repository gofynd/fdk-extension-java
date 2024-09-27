package com.fynd.extension.middleware;

import com.fynd.extension.model.webhookmodel.SubscriberConfig;
import com.fynd.extension.model.webhookmodel.SubscriberConfigList;
import com.fynd.extension.model.webhookmodel.SubscriberConfigRequestV2;
import com.fynd.extension.model.webhookmodel.SubscriberConfigRequestV3;
import com.fynd.extension.model.webhookmodel.SubscriberConfigResponse;
import com.fynd.extension.model.webhookmodel.SubscriberConfigResponseV3;

import retrofit2.Call;
import retrofit2.http.*;

public interface PlatformClientCall {
    @PUT("/service/platform/webhook/v3.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponseV3> registerSubscriberToEventV3(@Path("company_id") String companyId, @Body SubscriberConfigRequestV3 payload);

    @POST("/service/platform/webhook/v2.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> registerSubscriberToEventV2(@Path("company_id") String companyId, @Body SubscriberConfigRequestV2 payload);

    @GET("/service/platform/webhook/v1.0/company/{company_id}/extension/{extension_id}/subscriber/")
    Call<SubscriberConfigList> getSubscribersByExtensionId(@Path("company_id") String companyId, @Path("extension_id") String extensionId, @Query("page_no") Integer pageNo, @Query("page_size") Integer pageSize);

    @PUT("/service/platform/webhook/v2.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> updateSubscriberV2(@Path("company_id") String companyId, @Body SubscriberConfigRequestV2 payload);

    @POST ("/service/platform/webhook/v1.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> registerSubscriberToEvent(@Path("company_id") String companyId, @Body SubscriberConfig payload);

    @PUT ("/service/platform/webhook/v1.0/company/{company_id}/subscriber/")
    Call<SubscriberConfigResponse> updateSubscriberConfig(@Path("company_id") String companyId, @Body SubscriberConfig payload);

}
