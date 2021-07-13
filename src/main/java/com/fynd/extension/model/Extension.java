package com.fynd.extension.model;

import com.fynd.extension.storage.BaseStorage;
import com.fynd.extension.error.FdkInvalidExtensionJson;
import com.sdk.common.AccessToken;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Getter
@Setter
public class Extension {

    private String api_key;

    private String api_secret;

    private BaseStorage storage;

    private List<String> scopes;

    private String base_url;

    private ExtensionCallback callbacks;

    private String access_mode;

    private String cluster = "https://api.fynd.com";

    private Extension() {
    }

    public static Extension initialize(String api_key, String api_secret, BaseStorage storage,List<String> scopes, String base_url,ExtensionCallback callbacks, String access_mode, String cluster)
    {
        Extension extension = new Extension();
        String[] schemes = {"http","https"}; // DEFAULT schemes = "http", "https", "ftp"
        UrlValidator urlValidator = new UrlValidator(schemes);
        extension.setStorage(storage);

        if(StringUtils.isEmpty(api_key)){
            throw new FdkInvalidExtensionJson("Invalid api_key");
        }
        extension.setApi_key(api_key);

        if(StringUtils.isEmpty(api_secret)){
            throw new FdkInvalidExtensionJson("Invalid api_secret");
        }
        extension.setApi_secret(api_secret);

//        if(!urlValidator.isValid(base_url)) {
//            throw new FdkInvalidExtensionJson("Invalid base_url");
//        }

        extension.setBase_url(base_url);
        extension.setScopes(verifyScopes(scopes));

        if(ObjectUtils.isEmpty(callbacks) || ( ObjectUtils.isEmpty(callbacks) && (ObjectUtils.isEmpty(callbacks.getAuth()) || ObjectUtils.isEmpty(callbacks.getInstall()) || ObjectUtils.isEmpty(callbacks.getUninstall())))) {
            throw new FdkInvalidExtensionJson("Missing some of callbacks. Please add all , auth, install and uninstall callbacks.");
        }

        extension.setCallbacks(callbacks);
        if(StringUtils.isEmpty(access_mode))
        {
            extension.setAccess_mode("offline");
        }else{
            extension.setAccess_mode(access_mode);
        }

        if(!StringUtils.isEmpty(cluster)) {
            if(!urlValidator.isValid(cluster)) {
                throw new FdkInvalidExtensionJson("Invalid cluster");
            }
            extension.setCluster(cluster);
        }

        return extension;
    }

    private static List<String> verifyScopes(List<String> scopes) {
        if(CollectionUtils.isEmpty(scopes)) {
            throw new FdkInvalidExtensionJson("Invalid scopes in extension.json");
        }
        return scopes;
    }

    public String getAuthCallback(){
        return String.format("%s%s",this.base_url,"/fp/auth") ;
    }

    public boolean isOnlineAccessMode() {
        return this.access_mode == "online";
    }

    public PlatformConfig getPlatformConfig(String companyId) {
        PlatformConfig platformConfig = new PlatformConfig(companyId,this.api_key,this.api_secret,this.cluster);
        return platformConfig;
    }

    public PlatformClient getPlatformClient(String companyId, AccessToken session) {
        PlatformConfig platformConfig =  this.getPlatformConfig(companyId);
        platformConfig.getPlatformOauthClient().setToken(session);
        return new PlatformClient(platformConfig);
    }
}



