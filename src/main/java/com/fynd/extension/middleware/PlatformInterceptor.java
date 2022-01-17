package com.fynd.extension.middleware;

import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Response;
import com.fynd.extension.session.Session;
import com.fynd.extension.utils.ExtensionContext;
import com.sdk.common.AccessToken;
import com.sdk.platform.PlatformClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class PlatformInterceptor  implements HandlerInterceptor {

    @Autowired
    Extension extension;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        try {

            Session fdkSession = ExtensionContext.get("fdk-session", Session.class);
            AccessToken rawToken = new AccessToken();
            rawToken.setExpiresIn((long)fdkSession.getExpires_in());
            rawToken.setToken(fdkSession.getAccess_token());
            rawToken.setRefreshToken(fdkSession.getRefresh_token());
            PlatformClient platformClient = extension.getPlatformClient(fdkSession.getCompany_id(), rawToken );
            ExtensionContext.set("platform-client",platformClient);
            ExtensionContext.set("extension",extension);
            return true;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    new Response(false, error.getMessage()).toString());
        }
    }
}
