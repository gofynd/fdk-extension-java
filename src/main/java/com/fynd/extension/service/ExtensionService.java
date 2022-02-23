package com.fynd.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.application.ApplicationClient;
import com.sdk.application.ApplicationConfig;
import com.sdk.common.AccessToken;
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

    public PlatformClient getPlatformClient(String companyId){
        PlatformClient client = null;
        try {
            if (!this.ext.isOnlineAccessMode()) {
                log.info("CompanyId : "+companyId);
                String sid = Session.generateSessionId(false, new Option(
                        companyId,
                        this.ext.getExtensionProperties().getCluster()
                ));
                log.info("Session ID : "+ sid);
                Session session = sessionStorage.getSession(sid);
                if(Objects.nonNull(session)) {
                    AccessToken rawToken = new AccessToken();
                    rawToken.setExpiresIn((session.getExpires_in()));
                    rawToken.setToken(session.getAccess_token());
                    rawToken.setRefreshToken(session.getRefresh_token());
                    client = this.ext.getPlatformClient(companyId, rawToken);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return client;
    }



    public ApplicationClient getApplicationClient(String applicationId,String applicationToken){
        ApplicationConfig applicationConfig = new ApplicationConfig(applicationId, applicationToken,
                                                                    ext.getExtensionProperties().getCluster());
        return new ApplicationClient(applicationConfig);
    }

}
