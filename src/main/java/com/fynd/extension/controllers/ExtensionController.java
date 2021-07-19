package com.fynd.extension.controllers;


import com.fynd.extension.constant.FdkConstants;
import com.fynd.extension.error.FdkInvalidOAuth;
import com.fynd.extension.error.FdkSessionNotFound;
import com.fynd.extension.model.Client;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.model.Response;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.fynd.extension.utils.ExtensionContext;
import com.sdk.common.AccessToken;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/fp")
public class ExtensionController {


    @Autowired
    Extension ext;

    @Autowired
    SessionStorage sessionStorage;


    @GetMapping(path = "/install")
    public ResponseEntity install(@RequestParam(value = "company_id") String companyId,
                                  HttpServletResponse response,
                                  HttpServletRequest request) {

        try {
            if (StringUtils.isEmpty(companyId)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body("Invalid company id");
            }

            PlatformConfig platformConfig = ext.getPlatformConfig(companyId);
            Session session;
            String sid;
            if (ext.isOnlineAccessMode()) {
                session = new Session(Session.generateSessionId(true, null), true);
            } else {
                sid = Session.generateSessionId(false, new Option(ext.getCluster(), companyId));
                session = sessionStorage.getSession(sid);
                if (ObjectUtils.isEmpty(session)) {
                    session = new Session(sid, true);
                } else if (session.getExtension_id() != ext.getApi_key()) {
                    session = new Session(sid, true);
                }
            }

            Date sessionExpires = Date.from(Instant.now().plusMillis(900000));

            if (session.isNew()) {
                session.setCompany_id(companyId);
                session.setScope(ext.getScopes());
                session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                           .format(sessionExpires));
                session.setExpires_in(sessionExpires.getTime());
                session.setAccess_mode(ext.getAccess_mode());
                session.setExtension_id(ext.getApi_key());
            } else {
                if (!StringUtils.isEmpty(session.getExpires())) {
                    session.setExpires(FdkConstants.DATE_FORMAT.get()
                                                               .format(session.getExpires()));
                    session.setExpires_in(sessionExpires.getTime());
                }
            }

            ExtensionContext.set("fdk-session", session);
            ExtensionContext.set("extension", ext);


            ResponseCookie resCookie = ResponseCookie.from(FdkConstants.SESSION_COOKIE_NAME, session.getId())
                                                     .httpOnly(true)
                                                     .sameSite("None")
                                                     .secure(true)
                                                     .path("/")
                                                     .maxAge(Duration.between(Instant.now() ,Instant.ofEpochMilli(session.getExpires_in())) )
                                                     .build();


            session.setState(UUID.randomUUID()
                                 .toString());
            // start authorization flow
            String redirectUrl = platformConfig.getPlatformOauthClient()
                                               .getAuthorizationURL(session.getScope(), ext.getAuthCallback(),
                                                                    session.getState(),
                                                                    ext.isOnlineAccessMode());
            sessionStorage.saveSession(session);
            return ResponseEntity.status(HttpStatus.MOVED_TEMPORARILY)
                                 .header(HttpHeaders.LOCATION, redirectUrl)
                                 .header(HttpHeaders.SET_COOKIE, resCookie.toString())
                                 .build();
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }

    }

    @GetMapping(path = "/auth")
    public ResponseEntity authorize(@RequestParam(value = "company_id")String companyId,
                                  @RequestParam(value = "code",required = false) String code,
                                  @RequestParam(value = "state") String state,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {

        try {
            if (!ExtensionContext.isPresent("fdk-session")) {
                throw new FdkSessionNotFound(
                        "Can not complete oauth process as session not found");
            }
            Session fdkSession = ExtensionContext.get("fdk-session", Session.class);
            if (!fdkSession.getState()
                           .equalsIgnoreCase(state)) {
                throw new FdkInvalidOAuth("Invalid oauth call");
            }

            PlatformConfig platformConfig = ext.getPlatformConfig(fdkSession.getCompany_id());
            platformConfig.getPlatformOauthClient()
                          .verifyCallback(code);

            AccessToken token = platformConfig.getPlatformOauthClient()
                                              .getRawToken();

            Date sessionExpires = Date.from(Instant.now().plusMillis(token.getExpiresIn() * 1000));

            if (ext.isOnlineAccessMode()) {
                fdkSession.setExpires(FdkConstants.DATE_FORMAT.get()
                                                              .format(sessionExpires));
            } else {
                fdkSession.setExpires(null);
            }

            fdkSession.setAccess_token(token.getToken());
            fdkSession.setExpires_in(token.getExpiresIn());
            fdkSession.setAccess_token_validity("" + sessionExpires.getTime());
            //fdkSession.setCurrent_user(token.current_user);
            fdkSession.setRefresh_token(token.getRefreshToken());
            sessionStorage.saveSession(fdkSession);


            ResponseCookie resCookie = ResponseCookie.from(FdkConstants.SESSION_COOKIE_NAME,
                                                           fdkSession.getId())
                                                     .httpOnly(true)
                                                     .sameSite("None")
                                                     .secure(true)
                                                     .path("/")
                                                     .maxAge(Duration.between(Instant.now() ,Instant.ofEpochMilli(fdkSession.getExpires_in())) )
                                                     .build();

            ExtensionContext.set("fdk-session", fdkSession);
            ExtensionContext.set("extension", ext);

//            session.setState(UUID.randomUUID()
//                                 .toString());
            ExtensionContext.set("fdk-session", fdkSession);
            ExtensionContext.set("extension", ext);


            String redirectUrl = ext.getCallbacks()
                                             .getAuth()
                                             .apply(ExtensionContext.get());

            return ResponseEntity.status(HttpStatus.MOVED_TEMPORARILY)
                                 .header(HttpHeaders.LOCATION, redirectUrl)
                                 .header(HttpHeaders.SET_COOKIE, resCookie.toString())
                                 .build();
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }

    }

    @PostMapping(path = "/uninstall")
    public ResponseEntity uninstall(@RequestBody Client client,
                                    HttpServletRequest request,
                                    HttpServletResponse response
                                  ) {

        try {

            if (!ext.isOnlineAccessMode()) {
                String sid = Session.generateSessionId(false, new Option(client.getCompany_id(), ext.getCluster()));
                Session fdkSession = sessionStorage.getSession(sid);
                AccessToken rawToken = new AccessToken();
                rawToken.setExpiresIn((long) fdkSession.getExpires_in());
                rawToken.setToken(fdkSession.getAccess_token());
                rawToken.setRefreshToken(fdkSession.getRefresh_token());
                PlatformClient platformClient = ext.getPlatformClient(client.getCompany_id(), rawToken);
                ExtensionContext.set("platform-client", platformClient);
                sessionStorage.deleteSession(sid);
            }
            ExtensionContext.set("extension", ext);
            ext.getCallbacks()
               .getUninstall()
               .apply(ExtensionContext.get());
            return ResponseEntity.status(HttpStatus.OK)
                                 .body(new Response(true));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new Response(false, error.getMessage()));
        }

    }




}
