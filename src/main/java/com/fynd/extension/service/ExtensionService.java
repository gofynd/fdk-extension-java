package com.fynd.extension.service;

import com.fynd.extension.model.Extension;
import com.fynd.extension.model.Option;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.fynd.extension.utils.ExtensionContext;
import com.sdk.application.ApplicationClient;
import com.sdk.application.ApplicationConfig;
import com.sdk.common.AccessToken;
import com.sdk.platform.PlatformClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class ExtensionService {

    @Autowired
    Extension ext;

    @Autowired
    SessionStorage sessionStorage;

    public PlatformClient getPlatformClient(String companyId){
        PlatformClient client = null;
        try {
            if (!ext.isOnlineAccessMode()) {
                String sid = Session.generateSessionId(false, new Option(
                        companyId,
                        ext.getCluster()
                ));
                Session session = sessionStorage.getSession(sid);
                AccessToken rawToken = new AccessToken();
                rawToken.setExpiresIn((session.getExpires_in()));
                rawToken.setToken(session.getAccess_token());
                rawToken.setRefreshToken(session.getRefresh_token());
                client = ext.getPlatformClient(companyId, rawToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return client;
    }



    public ApplicationClient getApplicationClient(String applicationId,String applicationToken){
        ApplicationConfig applicationConfig = new ApplicationConfig(applicationId, applicationToken,
                                                                    ext.getCluster());
        ApplicationClient applicationClient = new ApplicationClient(applicationConfig);
        return applicationClient;
    }

}
