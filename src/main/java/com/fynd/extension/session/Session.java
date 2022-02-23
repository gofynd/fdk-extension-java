package com.fynd.extension.session;

import com.fynd.extension.model.Option;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
public class Session {

    private String id;

    private String company_id;

    private String state;

    private List<String> scope;

    private String expires;

    private long expires_in;

    private long access_token_validity;

    private String access_mode = "online";

    private String access_token;

    private String current_user;

    private String refresh_token;

    private boolean isNew = true;

    private String extension_id;

    public Session(String id, boolean isNew) {
        this.id = id;
        this.isNew = isNew;
    }

    public static Session cloneSession(String id, Session session,boolean isNew)
    {
        Session newSession = new Session(id, isNew);
        BeanUtils.copyProperties(session,newSession);
        return newSession;
    }

    public static String generateSessionId(boolean isOnline, Option options)throws Exception
    {
        if(isOnline) {
            return UUID.randomUUID()
                       .toString();
        } else {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest((options.getCluster() + ":" + options.getCompany_id()).getBytes()));
        }
    }
}
