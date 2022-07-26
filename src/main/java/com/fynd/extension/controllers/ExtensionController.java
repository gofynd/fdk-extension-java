package com.fynd.extension.controllers;


import com.fynd.extension.error.FdkInvalidOAuth;
import com.fynd.extension.error.FdkSessionNotFound;
import com.fynd.extension.middleware.AccessMode;
import com.fynd.extension.middleware.FdkConstants;
import com.fynd.extension.model.Client;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.model.Response;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.common.model.AccessTokenDto;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/fp")
@Slf4j
public class ExtensionController {

    @Autowired
    Extension ext;

    @Autowired
    SessionStorage sessionStorage;

    @GetMapping(path = "/install")
    public ResponseEntity<?> install(@RequestParam(value = "company_id") String companyId,
                                     @RequestParam(value = "application_id", required = false) String applicationId,
                                     HttpServletResponse response, HttpServletRequest request) {

        try {
            if (StringUtils.isEmpty(companyId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body("Invalid company id");
            }

            PlatformConfig platformConfig = ext.getPlatformConfig(companyId);
            Session session = new Session(Session.generateSessionId(true, null), true);
            Date sessionExpires = Date.from(Instant.now()
                                                   .plusMillis(Fields.MINUTES_LIMIT));
            if (session.isNew()) {
                session.setCompanyId(companyId);
                session.setScope(Arrays.asList(ext.getExtensionProperties()
                                                  .getScopes()
                                                  .split("\\s*,\\s*")));
                session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                           .format(sessionExpires));
                session.setExpiresIn(sessionExpires.getTime());
                session.setAccessMode(AccessMode.ONLINE.getName()); // Always generate online mode token for extension launch
                session.setExtensionId(ext.getExtensionProperties()
                                          .getApiKey());
            } else {
                if (!StringUtils.isEmpty(session.getExpires())) {
                    session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                               .format(session.getExpires()));
                    session.setExpiresIn(sessionExpires.getTime());
                }
            }
            String compCookieName = FdkConstants.SESSION_COOKIE_NAME + "_" + companyId;
            ResponseCookie resCookie = ResponseCookie.from(compCookieName, session.getId())
                                                     .httpOnly(true)
                                                     .sameSite("None")
                                                     .secure(true)
                                                     .path("/")
                                                     .maxAge(Duration.between(Instant.now(), Instant.ofEpochMilli(
                                                             session.getExpiresIn())))
                                                     .build();


            session.setState(UUID.randomUUID()
                                 .toString());
            String authCallback = ext.getAuthCallback();
            if (!StringUtils.isEmpty(applicationId)) {
                authCallback += "?application_id=" + applicationId;
            }
            String redirectUrl = platformConfig.getPlatformOauthClient()
                                               .getAuthorizationURL(session.getScope(), authCallback,
                                                                    session.getState(),
                                                                    true); // Always generate online mode token for extension launch
            sessionStorage.saveSession(session);
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                 .header(Fields.X_COMPANY_ID, companyId)
                                 .header(HttpHeaders.LOCATION, redirectUrl)
                                 .header(HttpHeaders.SET_COOKIE, resCookie.toString())
                                 .build();
        } catch (Exception error) {
            log.error("Exception in install call ", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }

    }

    @GetMapping(path = "/auth")
    public ResponseEntity<?> authorize(@RequestParam(value = "company_id") String companyId,
                                       @RequestParam(value = "code", required = false) String code,
                                       @RequestParam(value = "state") String state,
                                       @RequestParam(value = "application_id", required = false) String applicationId,
                                       HttpServletRequest request, HttpServletResponse response) {

        try {
            String sessionIdForCompany = getSessionIdFromCookie(request.getCookies(), companyId);
            Session fdkSession = sessionStorage.getSession(sessionIdForCompany);
            if (Objects.isNull(fdkSession)) {
                throw new FdkSessionNotFound("Can not complete oauth process as session not found");
            }
            if (!fdkSession.getState()
                           .equalsIgnoreCase(state)) {
                throw new FdkInvalidOAuth("Invalid oauth call");
            }
            PlatformConfig platformConfig = ext.getPlatformConfig(fdkSession.getCompanyId());
            platformConfig.getPlatformOauthClient()
                          .verifyCallback(code);

            AccessTokenDto token = platformConfig.getPlatformOauthClient()
                                                 .getRawToken();
            Date sessionExpires = Date.from(Instant.now()
                                                   .plusMillis(token.getExpiresIn() * 1000));
            fdkSession.setExpires(FdkConstants.DATE_FORMAT.get()
                                                          .format(sessionExpires));
            token.setAccessTokenValidity(sessionExpires.getTime());
            Session.updateToken(token, fdkSession);
            sessionStorage.saveSession(fdkSession);
            // Generate separate access token for offline mode
            if (!ext.isOnlineAccessMode()) {
                String sid = Session.generateSessionId(false, new Option(companyId, ext.getExtensionProperties()
                                                                                       .getCluster()));
                Session session = sessionStorage.getSession(sid);
                if (ObjectUtils.isEmpty(session) || (!Objects.equals(session.getExtensionId(), ext.getExtensionProperties()
                                                                                                  .getApiKey()))) {
                    session = new Session(sid, true);
                }
                AccessTokenDto offlineTokenRes = platformConfig.getPlatformOauthClient()
                                                               .getOfflineAccessToken(ext.getExtensionProperties()
                                                                                         .getScopes(), code);
                session.setCompanyId(companyId);
                session.setScope(Arrays.asList(ext.getExtensionProperties()
                                                  .getScopes()
                                                  .split("\\s*,\\s*")));
                session.setState(fdkSession.getState());
                session.setExtensionId(ext.getExtensionProperties()
                                          .getApiKey());
                offlineTokenRes.setAccessTokenValidity(platformConfig.getPlatformOauthClient()
                                                                     .getTokenExpiresAt());
                offlineTokenRes.setAccessMode(AccessMode.OFFLINE.getName());
                Session.updateToken(offlineTokenRes, session);
                sessionStorage.saveSession(session);
            } else {
                fdkSession.setExpires(null);
            }
            String compCookieName = FdkConstants.SESSION_COOKIE_NAME + "_" + fdkSession.getCompanyId();
            ResponseCookie resCookie = ResponseCookie.from(compCookieName, fdkSession.getId())
                                                     .httpOnly(true)
                                                     .sameSite("None")
                                                     .secure(true)
                                                     .path("/")
                                                     .maxAge(Duration.between(Instant.now(), Instant.ofEpochMilli(
                                                             fdkSession.getExpiresIn())))
                                                     .build();
            if(ext.getWebhookService().isInitialized) {
                PlatformClient platformClient = ext.getPlatformClient(companyId, fdkSession);
                ext.getWebhookService()
                   .syncEvents(platformClient, null, true);
            }
            String redirectUrl = ext.getCallbacks()
                                    .getAuth()
                                    .apply(request);
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                 .header(Fields.X_COMPANY_ID, companyId)
                                 .header(HttpHeaders.LOCATION, redirectUrl)
                                 .header(HttpHeaders.SET_COOKIE, resCookie.toString())
                                 .build();
        } catch (Exception error) {
            log.error("Exception in auth call ", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }

    }

    private String getSessionIdFromCookie(Cookie[] cookies, String companyId) {
        Cookie cookieFound = Arrays.stream(cookies)
                                       .filter(cookie -> cookie.getName()
                                                               .split("_")[2].equals(companyId))
                                       .findFirst()
                .orElseThrow();

        return cookieFound.getValue();
    }

    @PostMapping(path = "/auto_install")
    public ResponseEntity<?> autoInstall(@RequestBody Map<String, String> body, HttpServletRequest request,
                                         HttpServletResponse response) {
        try {
            String companyId = body.get("company_id");
            String code = body.get("code");
            log.info("Extension auto install started for company: {} on company creation.", companyId);
            PlatformConfig platformConfig = ext.getPlatformConfig(companyId);
            String sid = Session.generateSessionId(false, new Option(companyId, ext.getExtensionProperties()
                                                                                   .getCluster()));
            Session session = sessionStorage.getSession(sid);
            if (ObjectUtils.isEmpty(session) || (!Objects.equals(session.getExtensionId(), ext.getExtensionProperties()
                                                                                              .getApiKey()))) {
                session = new Session(sid, true);
            }
            AccessTokenDto offlineTokenRes = platformConfig.getPlatformOauthClient()
                                                           .getOfflineAccessToken(ext.getExtensionProperties()
                                                                                     .getScopes(), code);
            session.setCompanyId(companyId);
            session.setScope(Arrays.asList(ext.getExtensionProperties()
                                              .getScopes()
                                              .split("\\s*,\\s*")));
            session.setState(UUID.randomUUID()
                                 .toString());
            session.setExtensionId(ext.getExtensionProperties()
                                      .getApiKey());
            offlineTokenRes.setAccessTokenValidity(platformConfig.getPlatformOauthClient()
                                                                 .getTokenExpiresAt());
            offlineTokenRes.setAccessMode(AccessMode.OFFLINE.getName());
            Session.updateToken(offlineTokenRes, session);
            if (!ext.isOnlineAccessMode()) {
                sessionStorage.saveSession(session);
            }
            if (ext.getWebhookService().isInitialized) {
                PlatformClient platformClient = ext.getPlatformClient(companyId, session);
                ext.getWebhookService()
                   .syncEvents(platformClient, null, true);
            }
            log.info("Extension installed for company: {} on company creation.`", companyId);
            ext.getCallbacks()
               .getAutoInstall()
               .apply(request);
            return ResponseEntity.status(HttpStatus.OK)
                                 .body(new Response(true));
        } catch (Exception e) {
            log.error("Exception in auto-install call ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, e.getMessage()));
        }
    }

    @PostMapping(path = "/uninstall")
    public ResponseEntity<?> uninstall(@RequestBody Client client, HttpServletRequest request,
                                       HttpServletResponse response) {
        try {
            if (!ext.isOnlineAccessMode()) {
                String sid = Session.generateSessionId(false, new Option(client.getCompanyId(),
                                                                         ext.getExtensionProperties()
                                                                            .getCluster()));
                ext.getCallbacks()
                   .getUninstall()
                   .apply(request);
                sessionStorage.deleteSession(sid);
            }
            return ResponseEntity.status(HttpStatus.OK)
                                 .body(new Response(true));
        } catch (Exception error) {
            log.error("Exception in uninstall call ", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }
    }

    public interface Fields {
        int MINUTES_LIMIT = 900000;
        String X_COMPANY_ID = "x-company-id";
    }
}
