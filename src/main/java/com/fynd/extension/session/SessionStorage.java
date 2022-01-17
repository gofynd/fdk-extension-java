package com.fynd.extension.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.constant.FdkConstants;
import com.fynd.extension.model.Extension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

@Component
public class SessionStorage {

    @Autowired
     private Extension extension;

    @Autowired
     private ObjectMapper objectMapper;


    public String saveSession(Session session) throws Exception{
        if(!StringUtils.isEmpty(session.getExpires())) {
            int ttl = (int)(new Date().getTime() - FdkConstants.DATE_FORMAT.get().parse(session.getExpires()).getTime()) / 1000;
            ttl = Math.abs(Math.round(Math.min(ttl, 0)));
            return extension.getStorage().setex(session.getId(), ttl ,objectMapper.writeValueAsString(session));
        } else {
            return extension.getStorage().set(session.getId(), objectMapper.writeValueAsString(session));
        }
    }

    public Session getSession(String sessionId) {
        var sessionStr = extension.getStorage().get(sessionId);
        Session session = null;
        if(!StringUtils.isEmpty(sessionStr)) {
            try {
                session = objectMapper.readValue(sessionStr, Session.class);
                session = Session.cloneSession(sessionId, session, false);
            }catch (Exception e)
            {}
        }
        return session;
    }

    public Object deleteSession(String sessionId) {
        return extension.getStorage().del(sessionId);
    }
}
