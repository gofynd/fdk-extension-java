package com.fynd.extension.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.middleware.AccessMode;
import com.fynd.extension.model.Option;
import com.sdk.common.model.AccessTokenDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

import java.security.MessageDigest;
import java.util.*;

@NoArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Session {

    @JsonProperty("current_user")
    Map<String, Object> currentUser;

    private String id;

    @JsonProperty("company_id")
    private String companyId;

    private String state;

    private List<String> scope;

    private String expires;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("access_token_validity")
    private Long accessTokenValidity;

    @JsonProperty("access_mode")
    private String accessMode = AccessMode.ONLINE.getName();

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    private boolean isNew = true;

    @JsonProperty("extension_id")
    private String extensionId;

    public Session(String id, boolean isNew) {
        this.id = id;
        this.isNew = isNew;
    }

    public static Session cloneSession(String id, Session session, boolean isNew) {
        Session newSession = new Session(id, isNew);
        BeanUtils.copyProperties(session, newSession);
        return newSession;
    }

    public static String generateSessionId(boolean isOnline, Option options) throws Exception {
        if (isOnline) {
            return UUID.randomUUID()
                       .toString();
        } else {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder()
                         .encodeToString(
                                 digest.digest((options.getCluster() + ":" + options.getCompany_id()).getBytes()));
        }
    }

    public static void updateToken(AccessTokenDto renewToken, Session session) {
        session.setAccessMode(
                Objects.nonNull(renewToken.getAccessMode()) ? renewToken.getAccessMode() : session.getAccessMode());
        session.setAccessToken(renewToken.getAccessToken());
        session.setCurrentUser(renewToken.getCurrentUser());
        session.setRefreshToken(renewToken.getRefreshToken());
        session.setExpiresIn(renewToken.getExpiresIn());
        session.setAccessTokenValidity(renewToken.getAccessTokenValidity());
    }

    public String getUserEmailFromSession(Session session) {
        List<Map<String,Object>> emails = new ObjectMapper().convertValue(session.getCurrentUser().get("emails"), List.class);
        if(!emails.isEmpty()) {
            return emails.stream()
                  .filter(email -> email.get("primary").equals(Boolean.TRUE))
                  .findFirst()
                  .get()
                  .get("email").toString();
        }
        return "No user";
    }
}
