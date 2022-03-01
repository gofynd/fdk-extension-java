package com.fynd.extension.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.constant.FdkConstants;
import com.fynd.extension.model.Extension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

@Component
@Slf4j
public class SessionStorage {

    @Autowired
    Extension extension;

    @Autowired
    ObjectMapper objectMapper;


    public void saveSession(Session session) throws Exception {
        if (!StringUtils.isEmpty(session.getExpires())) {
            int ttl = (int) (new Date().getTime() - FdkConstants.DATE_FORMAT.get()
                                                                            .parse(session.getExpires())
                                                                            .getTime()) / 1000;
            ttl = Math.abs(Math.round(Math.min(ttl, 0)));
            extension.getStorage()
                     .setex(session.getId(), ttl, objectMapper.writeValueAsString(session));
        } else {
            extension.getStorage()
                     .set(session.getId(), objectMapper.writeValueAsString(session));
        }
    }

    public Session getSession(String sessionId) {
        var sessionStr = extension.getStorage()
                                  .get(sessionId);
        Session session = null;
        if (!StringUtils.isEmpty(sessionStr)) {
            try {
                session = objectMapper.readValue(sessionStr, Session.class);
                session = Session.cloneSession(sessionId, session, false);
            } catch (Exception e) {
                log.error("Exception in reading Session from Storage : ", e);
            }
        }
        return session;
    }

    public Object deleteSession(String sessionId) {
        return extension.getStorage()
                        .del(sessionId);
    }
}
