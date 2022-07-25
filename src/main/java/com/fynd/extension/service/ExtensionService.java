package com.fynd.extension.service;

import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.application.ApplicationClient;
import com.sdk.application.ApplicationConfig;
import com.sdk.common.model.AccessTokenDto;
import com.sdk.platform.PlatformClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class ExtensionService {

    @Autowired
    Extension ext;

    @Autowired
    SessionStorage sessionStorage;

    public PlatformClient getPlatformClient(String companyId) {
        log.debug("Fetching PlatformClient for companyId : {}", companyId);
        PlatformClient client = null;
        try {
            if (!this.ext.isOnlineAccessMode()) {
                String sid = Session.generateSessionId(false, new Option(companyId, this.ext.getExtensionProperties()
                                                                                            .getCluster()));
                Session session = sessionStorage.getSession(sid);
                if (Objects.nonNull(session)) {
                    AccessTokenDto rawToken = new AccessTokenDto();
                    rawToken.setExpiresIn((session.getExpiresIn()));
                    rawToken.setAccessToken(session.getAccessToken());
                    rawToken.setRefreshToken(session.getRefreshToken());
                    client = this.ext.getPlatformClient(companyId, session);
                }
                log.info("Platform Client for Company Id : {} and Session Id :{}", companyId, sid);
            }
        } catch (Exception e) {
            log.error("Exception in getting Platform Client : ", e);
        }

        return client;
    }


    public ApplicationClient getApplicationClient(String applicationId, String applicationToken) {
        log.debug("Fetching ApplicationClient for applicationId : {}", applicationId);
        ApplicationConfig applicationConfig = new ApplicationConfig(applicationId, applicationToken,
                                                                    ext.getExtensionProperties()
                                                                       .getCluster());
        return new ApplicationClient(applicationConfig);
    }

}
