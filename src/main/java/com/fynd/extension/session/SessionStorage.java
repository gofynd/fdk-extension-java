package com.fynd.extension.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fynd.extension.middleware.FdkConstants;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
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

    private void storeSession(Session session, Extension extension) {
        try {
            if (!StringUtils.isEmpty(session.getExpires())) {
                int ttl = (int) (new Date().getTime() - FdkConstants.DATE_FORMAT.get()
                                                                                .parse(session.getExpires())
                                                                                .getTime()) / 1000;
                ttl = Math.abs(Math.round(Math.min(ttl, 0)));
                extension.getStorage()
                         .setex(session.getId(), ttl, objectMapper.writeValueAsString(session));
                log.debug("Saving session Id {} with ttl {}", session.getId(), ttl);
            } else {
                extension.getStorage()
                         .set(session.getId(), objectMapper.writeValueAsString(session));
                log.debug("Saving session Id {} without ttl ", session.getId());
            }
        } catch (Exception e) {
            log.error("Error in saving session", e);
        }
    }

    public void saveSession(Session session) {
        storeSession(session, extension);
    }

    public void saveSession(Session session, Extension extension) {
        objectMapper = new ObjectMapper();
        storeSession(session, extension);
    }

    public Session getSessionFromCompany(String companyId) {
        try {
            String sid = Session.generateSessionId(false, new Option(companyId, extension.getExtensionProperties()
                                                                            .getCluster()));
            return getSession(sid);
        } catch (Exception e) {
            log.error("Exception in getting session for company ID : {}", companyId, e);
            return null;
        }
    }

    public Session getSession(String sessionId) {
        log.debug("Retrieving session for Session ID : {}", sessionId);
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
