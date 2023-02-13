package com.fynd.extension.middleware;

import com.fynd.extension.model.ExtensionProperties;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

@Component
public class ExtensionInterceptor implements Interceptor {

    @Value("${fdk-extension.version}")
    String buildVersion;

    private ExtensionProperties extensionProperties;

    public ExtensionInterceptor(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String bearerToken = Base64.getEncoder()
                                   .encodeToString(
                                           (extensionProperties.getApiKey() + ":" + extensionProperties.getApiSecret()).getBytes());
        Request request = chain.request()
                               .newBuilder()
                               .addHeader("Authorization", "Bearer " + bearerToken)
                               .addHeader("Content-Type", "application/json")
                               .addHeader("x-ext-lib-version", "java/" + buildVersion)
                               .build();
        return chain.proceed(request);
    }
}
