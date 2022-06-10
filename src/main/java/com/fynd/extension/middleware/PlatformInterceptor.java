package com.fynd.extension.middleware;

import com.fynd.extension.model.Response;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.common.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.fynd.extension.controllers.ExtensionController.Fields.COMPANY_ID;
import static com.fynd.extension.controllers.ExtensionController.Fields.X_COMPANY_ID;


@Component
public class PlatformInterceptor implements HandlerInterceptor {

    @Autowired
    SessionStorage sessionStorage;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String companyId = !StringUtils.isEmpty(request.getHeader(X_COMPANY_ID)) ? request.getHeader(
                    X_COMPANY_ID) : request.getParameter(COMPANY_ID);
            Session fdkSession = sessionStorage.getSessionFromCompany(companyId);
            AccessToken rawToken = new AccessToken();
            rawToken.setExpiresIn(fdkSession.getExpires_in());
            rawToken.setToken(fdkSession.getAccess_token());
            rawToken.setRefreshToken(fdkSession.getRefresh_token());
//            extension.getPlatformClient(fdkSession.getCompany_id(), rawToken);
            return true;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              new Response(false, error.getMessage()).toString());
        }
    }
}
