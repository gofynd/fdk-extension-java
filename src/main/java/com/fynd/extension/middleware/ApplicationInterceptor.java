package com.fynd.extension.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.model.Application;
import com.fynd.extension.model.Response;
import com.fynd.extension.model.User;
import com.sdk.application.ApplicationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApplicationInterceptor implements HandlerInterceptor {

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        try {
            if (!StringUtils.isEmpty(request.getHeader(Fields.X_USER_DATA))) {
                User user = objectMapper.readValue(request.getHeader(Fields.X_USER_DATA), User.class);
//                ExtensionContext.set(Fields.X_USER_DATA, user);
//                    req.user.user_id = req.user._id;
            }
            if (!StringUtils.isEmpty(request.getHeader(Fields.X_APPLICATION_DATA))) {
                Application application = objectMapper.readValue(request.getHeader(Fields.X_APPLICATION_DATA),
                                                                 Application.class);
//                ExtensionContext.set(APPLICATION, application);
                ApplicationConfig applicationConfig = new ApplicationConfig(application.getID(),
                                                                            application.getToken());
//                ExtensionContext.set(APPLICATION_CONFIG, applicationConfig);
//                ExtensionContext.set(APPLICATION_CLIENT, new ApplicationClient(applicationConfig));
            }
            return true;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              new Response(false, error.getMessage()).toString());
        }
    }

    interface Fields {
        String X_USER_DATA = "x-user-data";
        String X_APPLICATION_DATA = "x-application-data";
    }
}
