package com.fynd.extension.middleware;

import com.fynd.extension.constant.FdkConstants;
import com.fynd.extension.model.Extension;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.fynd.extension.utils.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;

@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Autowired
    Extension extension;

    @Autowired
    SessionStorage sessionStorage;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Session fdkSession = null;
        String companyId = !StringUtils.isEmpty(request.getHeader("x-company-id")) ? request.getHeader(
                "x-company-id") : request.getParameter("company_id");
        if (!StringUtils.isEmpty(companyId)) {
            String compCookieName = FdkConstants.SESSION_COOKIE_NAME + "_" + companyId;
            Optional<Cookie> sessionCookie = Arrays.stream(request.getCookies())
                                                   .filter(c -> c.getName()
                                                                 .equals(compCookieName))
                                                   .findFirst();
            if (sessionCookie.isPresent()) {
                String sessionId = sessionCookie
                        .map(Cookie::getValue)
                        .orElse(null);
                fdkSession = sessionStorage.getSession(sessionId);
            }
        }

        if (!ObjectUtils.isEmpty(fdkSession)) {
            ExtensionContext.set("fdk-session", fdkSession);
            return true;
        } else {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized");
        }
    }


}
