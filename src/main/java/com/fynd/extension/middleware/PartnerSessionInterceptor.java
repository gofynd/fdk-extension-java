package com.fynd.extension.middleware;

import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;

import static com.fynd.extension.middleware.FdkConstants.ADMIN_SESSION_COOKIE_NAME;

@Slf4j
@Component
public class PartnerSessionInterceptor  implements HandlerInterceptor{
    
    @Autowired
    SessionStorage sessionStorage;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info("[PARTNER SESSION INTERCEPTOR]");
        Session fdkSession = null;

        Optional<Cookie> sessionCookie = Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals(ADMIN_SESSION_COOKIE_NAME))
                .findFirst();

        if(sessionCookie.isPresent()){
            String sessionId = sessionCookie.map(Cookie::getValue).orElse(null);
            fdkSession = sessionStorage.getSession(sessionId);
        }

        if (ObjectUtils.isNotEmpty(fdkSession)) {
            request.setAttribute("fdkSession", fdkSession);
            return true;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }
}
