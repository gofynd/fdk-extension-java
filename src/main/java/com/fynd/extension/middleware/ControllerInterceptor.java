package com.fynd.extension.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.controllers.BaseApplicationController;
import com.fynd.extension.controllers.BasePlatformController;
import com.fynd.extension.model.*;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.application.ApplicationClient;
import com.sdk.application.ApplicationConfig;
import com.sdk.platform.PlatformClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import lombok.extern.slf4j.Slf4j;

import static com.fynd.extension.controllers.ExtensionController.Fields.X_COMPANY_ID;

import java.util.Arrays;
import java.util.Optional;



@Slf4j
@Component
public class ControllerInterceptor implements HandlerInterceptor {

    @Autowired
    Extension extension;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SessionStorage sessionStorage;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        try {

            if (handler instanceof HandlerMethod) {
                HandlerMethod handlerMethod = (HandlerMethod) handler;
                Object controller = handlerMethod.getBean();

                if (controller instanceof BasePlatformController) {

                    // TODO: use SessionInterceptor class here
                    log.info("[SESSION INTERCEPTOR]");
                    Session fdkSession = null;

                    String companyId = StringUtils.isNotEmpty(request.getHeader(X_COMPANY_ID)) ?
                            request.getHeader(X_COMPANY_ID) : request.getParameter(Fields.COMPANY_ID);

                    if (StringUtils.isNotEmpty(companyId)) {
                        String companyCookieName = FdkConstants.SESSION_COOKIE_NAME + "_" + companyId;
                        Optional<Cookie> sessionCookie = Arrays.stream(request.getCookies())
                                .filter(c -> c.getName().equals(companyCookieName))
                                .findFirst();

                        if (sessionCookie.isPresent()) {
                            String sessionId = sessionCookie.map(Cookie::getValue).orElse(null);
                            fdkSession = sessionStorage.getSession(sessionId);
                        }
                    }

                    if (ObjectUtils.isNotEmpty(fdkSession)) {
                        request.setAttribute("fdkSession", fdkSession);
//                        return true;
                    } else {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
                    }


                    log.info("[PLATFORM INTERCEPTOR]");
//                    fdkSession = (Session) request.getAttribute("fdkSession");
                    PlatformClient platformClient = extension.getPlatformClient(fdkSession.getCompanyId(), fdkSession);

                    request.setAttribute("platformClient", platformClient);
                    request.setAttribute("extension", extension);
                    return true;

                } else if (controller instanceof BaseApplicationController) {
                    log.info("[APPLICATION INTERCEPTOR]");
                    if (StringUtils.isNotEmpty(request.getHeader(Fields.X_USER_DATA))) {
                        User user = objectMapper.readValue(request.getHeader(Fields.X_USER_DATA), User.class);
                        request.setAttribute("user", user);
                        // TODO: add user_id in USER class
                    }

                    if (StringUtils.isNotEmpty(request.getHeader(Fields.X_APPLICATION_DATA))) {
                        Application application = objectMapper.readValue(request.getHeader(Fields.X_APPLICATION_DATA), Application.class);
                        request.setAttribute("application", application);

                        ApplicationConfig applicationConfig = new ApplicationConfig(
                                application.getID(),
                                application.getToken(),
                                extension.getExtensionProperties().getCluster()
                        );
                        request.setAttribute("applicationConfig", applicationConfig);

                        ApplicationClient applicationClient = new ApplicationClient(applicationConfig);
                        request.setAttribute("applicationClient", applicationClient);
                    }
                    return true;
                }
            }


            return true;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, new Response(false, error.getMessage()).toString());
        }
    }


    public interface Fields {
        String X_USER_DATA = "x-user-data";
        String X_APPLICATION_DATA = "x-application-data";
        String COMPANY_ID = "company_id";
    }

}
