package com.fynd.extension.middleware;

import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;

import static com.fynd.extension.controllers.ExtensionController.Fields.X_COMPANY_ID;
import static com.fynd.extension.middleware.ControllerInterceptor.Fields;

@Slf4j
@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Autowired
    SessionStorage sessionStorage;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.debug("[SESSION INTERCEPTOR]");
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
            return true;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }
}
