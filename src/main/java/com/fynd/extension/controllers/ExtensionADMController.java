package com.fynd.extension.controllers;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fynd.extension.error.FdkInvalidOAuth;
import com.fynd.extension.error.FdkSessionNotFound;
import com.fynd.extension.middleware.AccessMode;
import com.fynd.extension.middleware.FdkConstants;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.model.Response;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.common.model.AccessTokenDto;
import com.sdk.partner.PartnerConfig;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/adm")
@Slf4j
public class ExtensionADMController {
     @Autowired
    Extension ext;

    @Autowired
    SessionStorage sessionStorage;

    @GetMapping(path = "/install")
    public ResponseEntity<?> install(@RequestParam(value = "organization_id") String organizationId,
            HttpServletResponse response, HttpServletRequest request) {

        try {
            log.info("/adm/install invoked");
            if (StringUtils.isEmpty(organizationId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid organization id");
            }

            PartnerConfig partnerConfig = ext.getPartnerConfig(organizationId);
            Session session = new Session(Session.generateSessionId(true, null), true);
            Date sessionExpires = Date.from(Instant.now()
                    .plusMillis(Fields.MINUTES_LIMIT));
            if (session.isNew()) {
                session.setOrganizationId(organizationId);
                session.setScope(ext.getExtensionProperties().getScopes());
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
            ResponseCookie resCookie = ResponseCookie.from(FdkConstants.ADMIN_SESSION_COOKIE_NAME, session.getId())
                    .httpOnly(true)
                    .sameSite("None")
                    .secure(true)
                    .path("/")
                    .maxAge(Duration.between(Instant.now(), Instant.ofEpochMilli(
                            session.getExpiresIn())))
                    .build();

            session.setState(UUID.randomUUID()
                    .toString());
            String baseUrl = ext.getExtensionProperties().getBaseUrl();
            var uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
            String authCallback = uriBuilderFactory.builder().pathSegment("adm/auth").build().toString();
            System.out.println("authCallback " + authCallback);
            String redirectUrl = partnerConfig.getPartnerOauthClient()
                    .getAuthorizationURL(session.getScope(), authCallback,
                            session.getState(),
                            true); // Always generate online mode token for extension launch
            sessionStorage.saveSession(session);
            request.setAttribute("session", session);
            return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
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
    public ResponseEntity<?> authorize(@RequestParam(value = "organization_id") String organizationId,
                                       @RequestParam(value = "code", required = false) String code,
                                       @RequestParam(value = "state") String state,
                                       HttpServletRequest request, HttpServletResponse response) {

        try {
            String sessionIdForOrganization = ext.getCookieValue(request.getCookies());
            if (StringUtils.isNotEmpty(sessionIdForOrganization)) {
                Session fdkSession = sessionStorage.getSession(sessionIdForOrganization);
                if (Objects.isNull(fdkSession)) {
                    throw new FdkSessionNotFound("Can not complete oauth process as session not found");
                }
                if (!fdkSession.getState()
                               .equalsIgnoreCase(state)) {
                    throw new FdkInvalidOAuth("Invalid oauth call");
                }
                PartnerConfig partnerConfig = ext.getPartnerConfig(fdkSession.getOrganizationId());
                partnerConfig.getPartnerOauthClient()
                              .verifyCallback(code);

                AccessTokenDto token = partnerConfig.getPartnerOauthClient()
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
                    String sid = Session.generateSessionId(false, new Option(organizationId, ext.getExtensionProperties()
                                                                                           .getCluster()));
                    Session session = sessionStorage.getSession(sid);
                    log.debug("Retrieving session in ExtensionController.authorize() : {}", session);
                    if (ObjectUtils.isEmpty(session) || (!Objects.equals(session.getExtensionId(),
                                                                         ext.getExtensionProperties()
                                                                            .getApiKey()))) {
                        session = new Session(sid, true);
                    }
                    AccessTokenDto offlineTokenRes = partnerConfig.getPartnerOauthClient()
                                                                   .getOfflineAccessToken(String.join(",", ext.getExtensionProperties().getScopes()), code);
                    session.setOrganizationId(organizationId);
                    session.setScope(ext.getExtensionProperties().getScopes());
                    session.setState(fdkSession.getState());
                    session.setExtensionId(ext.getExtensionProperties()
                                              .getApiKey());
                    offlineTokenRes.setAccessTokenValidity(partnerConfig.getPartnerOauthClient()
                                                                         .getTokenExpiresAt());
                    offlineTokenRes.setAccessMode(AccessMode.OFFLINE.getName());
                    Session.updateToken(offlineTokenRes, session);
                    log.debug("Saving session from ExtensionController.authorize() : {}", session);
                    sessionStorage.saveSession(session);
                } else {
                    fdkSession.setExpires(null);
                }
                ResponseCookie resCookie = ResponseCookie.from(FdkConstants.ADMIN_SESSION_COOKIE_NAME, fdkSession.getId())
                                                         .httpOnly(true)
                                                         .sameSite("None")
                                                         .secure(true)
                                                         .path("/")
                                                         .maxAge(Duration.between(Instant.now(), Instant.ofEpochMilli(
                                                                 fdkSession.getExpiresIn())))
                                                         .build();
                String baseUrl = ext.getExtensionProperties().getBaseUrl();
                var uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
                String admLaunchCallback = uriBuilderFactory.builder().pathSegment("admin").build().toString();
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                                     .header(HttpHeaders.LOCATION, admLaunchCallback)
                                     .header(HttpHeaders.SET_COOKIE, resCookie.toString())
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

        public interface Fields {
            int MINUTES_LIMIT = 900000;
            String DELIMITER = "_";
        }
    
}
