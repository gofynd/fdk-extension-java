package com.fynd.extension.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.model.Application;
import com.fynd.extension.model.User;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Response;
import com.fynd.extension.utils.ExtensionContext;
import com.sdk.application.ApplicationClient;
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
public class ApplicationInterceptor  implements HandlerInterceptor {

    @Autowired
    Extension extension;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        try {
            if(!StringUtils.isEmpty(request.getHeader("x-user-data"))) {
                User user = objectMapper.readValue(request.getHeader("x-user-data"), User.class);
                ExtensionContext.set("x-user-data", user );
//                    req.user.user_id = req.user._id;
            }
            if(!StringUtils.isEmpty(request.getHeader("x-application-data")))
            {
                Application application = objectMapper.readValue(request.getHeader("x-application-data"), Application.class);
                ExtensionContext.set("application",application) ;
                ApplicationConfig applicationConfig = new ApplicationConfig(application.getID(), application.getToken());
                ExtensionContext.set("application-config",applicationConfig);
                ExtensionContext.set("application-client",new ApplicationClient(applicationConfig));
            }
            return true;
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    new Response(false, error.getMessage()).toString());
        }
    }
}
