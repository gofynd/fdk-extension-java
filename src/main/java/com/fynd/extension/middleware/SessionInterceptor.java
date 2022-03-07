package com.fynd.extension.middleware;

import com.fynd.extension.constant.FdkConstants;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.fynd.extension.utils.ExtensionContext;
import lombok.extern.slf4j.Slf4j;
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

import static com.fynd.extension.controllers.ExtensionController.Fields.X_COMPANY_ID;
import static com.fynd.extension.utils.ExtensionContext.Keys.COMPANY_ID;
import static com.fynd.extension.utils.ExtensionContext.Keys.FDK_SESSION;

@Component
@Slf4j
public class SessionInterceptor implements HandlerInterceptor {

    @Autowired
    SessionStorage sessionStorage;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Session fdkSession = null;
        String companyId = !StringUtils.isEmpty(request.getHeader(X_COMPANY_ID)) ? request.getHeader(
                X_COMPANY_ID) : request.getParameter(COMPANY_ID);
        log.info("Company ID : " + companyId);
        if (!StringUtils.isEmpty(companyId)) {
            String compCookieName = FdkConstants.SESSION_COOKIE_NAME + "_" + companyId;
            Optional<Cookie> sessionCookie = Arrays.stream(request.getCookies())
                                                   .filter(c -> c.getName()
                                                                 .equals(compCookieName))
                                                   .findFirst();
            if (sessionCookie.isPresent()) {
                String sessionId = sessionCookie.map(Cookie::getValue)
                                                .orElse(null);
                fdkSession = sessionStorage.getSession(sessionId);
            }
        }

        if (!ObjectUtils.isEmpty(fdkSession)) {
            ExtensionContext.set(FDK_SESSION, fdkSession);
            return true;
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
    }


}
