package com.fynd.extension.controllers;


import static com.fynd.extension.controllers.ExtensionController.Fields.DELIMITER;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                           .format(sessionExpires));
                session.setExpiresIn(sessionExpires.getTime());
                session.setAccessMode(
                        AccessMode.ONLINE.getName()); // Always generate online mode token for extension launch
                session.setExtensionId(ext.getExtensionProperties()
                                          .getApiKey());
            } else {
                if (!StringUtils.isEmpty(session.getExpires())) {
                    session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                               .format(session.getExpires()));
                    session.setExpiresIn(sessionExpires.getTime());
                }
            }
            String compCookieName = FdkConstants.SESSION_COOKIE_NAME + DELIMITER + companyId;
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
                                               .getAuthorizationURL(ext.getExtensionProperties().getScopes(), authCallback,
                                                                    session.getState(),
                                                                    true); // Always generate online mode token for extension launch
            sessionStorage.saveSession(session);
            request.setAttribute("session", session);
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                 .header(Fields.X_COMPANY_ID, companyId)
                                 .header(HttpHeaders.LOCATION, redirectUrl)
                                 .header(HttpHeaders.SET_COOKIE, resCookie.toString()+ "; Partitioned;")
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
            String sessionIdForCompany = ext.getSessionIdFromCookie(request.getCookies(), companyId);
            if (StringUtils.isNotEmpty(sessionIdForCompany)) {
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
                request.setAttribute("session", fdkSession);
                // Generate separate access token for offline mode
                if (!ext.isOnlineAccessMode()) {
                    String sid = Session.generateSessionId(false, new Option(companyId, ext.getExtensionProperties()
                                                                                           .getCluster()));
                    Session session = sessionStorage.getSession(sid);
                    log.debug("Retrieving session in ExtensionController.authorize() : {}", session);
                    if (ObjectUtils.isEmpty(session) || (!Objects.equals(session.getExtensionId(),
                                                                         ext.getExtensionProperties()
                                                                            .getApiKey()))) {
                        session = new Session(sid, true);
                    }
                    AccessTokenDto offlineTokenRes = platformConfig.getPlatformOauthClient()
                                                                   .getOfflineAccessToken(null, code);
                    session.setCompanyId(companyId);
                    session.setState(fdkSession.getState());
                    session.setExtensionId(ext.getExtensionProperties()
                                              .getApiKey());
                    offlineTokenRes.setAccessTokenValidity(platformConfig.getPlatformOauthClient()
                                                                         .getTokenExpiresAt());
                    offlineTokenRes.setAccessMode(AccessMode.OFFLINE.getName());
                    Session.updateToken(offlineTokenRes, session);
                    log.debug("Saving session from ExtensionController.authorize() : {}", session);
                    sessionStorage.saveSession(session);
                } else {
                    fdkSession.setExpires(null);
                }
                String compCookieName = FdkConstants.SESSION_COOKIE_NAME + DELIMITER + fdkSession.getCompanyId();
                ResponseCookie resCookie = ResponseCookie.from(compCookieName, fdkSession.getId())
                                                         .httpOnly(true)
                                                         .sameSite("None")
                                                         .secure(true)
                                                         .path("/")
                                                         .maxAge(Duration.between(Instant.now(), Instant.ofEpochMilli(
                                                                 fdkSession.getExpiresIn())))
                                                         .build();
                if (ext.getWebhookService().isInitialized) {
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
                                     .header(HttpHeaders.SET_COOKIE, resCookie.toString()+ "; Partitioned;")
                                     .build();
            }
        } catch (Exception error) {
            log.error("Exception in auth call ", error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(new Response(false, "Failed due to empty Session ID"));
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
            log.debug("Retrieving session in ExtensionController.autoInstall() : {}", session);
            if (ObjectUtils.isEmpty(session) || (!Objects.equals(session.getExtensionId(), ext.getExtensionProperties()
                                                                                              .getApiKey()))) {
                session = new Session(sid, true);
            }
            AccessTokenDto offlineTokenRes = platformConfig.getPlatformOauthClient()
                                                           .getOfflineAccessToken(
                                                                    null,
                                                                   code);
            session.setCompanyId(companyId);
            session.setState(UUID.randomUUID()
                                 .toString());
            session.setExtensionId(ext.getExtensionProperties()
                                      .getApiKey());
            offlineTokenRes.setAccessTokenValidity(platformConfig.getPlatformOauthClient()
                                                                 .getTokenExpiresAt());
            offlineTokenRes.setAccessMode(AccessMode.OFFLINE.getName());
            Session.updateToken(offlineTokenRes, session);
            if (!ext.isOnlineAccessMode()) {
                log.debug("Saving session from ExtensionController.autoInstall() : {}", session);
                sessionStorage.saveSession(session);
            }
            if (ext.getWebhookService().isInitialized) {
                PlatformClient platformClient = ext.getPlatformClient(companyId, session);
                ext.getWebhookService()
                   .syncEvents(platformClient, null, true);
            }
            request.setAttribute("session", session);
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
                Session session = sessionStorage.getSession(sid);
                request.setAttribute("session", session);
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
        String DELIMITER = "_";
    }
}
