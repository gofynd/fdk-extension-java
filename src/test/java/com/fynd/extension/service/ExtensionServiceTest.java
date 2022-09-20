package com.fynd.extension.service;

import com.fynd.extension.model.Extension;
import com.fynd.extension.model.ExtensionProperties;
import com.fynd.extension.service.ExtensionService;
import com.fynd.extension.session.Session;
import com.fynd.extension.session.SessionStorage;
import com.sdk.platform.PlatformClient;
import com.sdk.platform.PlatformConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ExtensionServiceTest {

    @Mock
    Extension ext;

    @Mock
    SessionStorage sessionStorage;

    @InjectMocks
    ExtensionService extensionService;

    Session session;

    ExtensionProperties extensionProperties;

    PlatformConfig platformConfig;

    PlatformClient platformClient;

    @Before
    public void beforeEach() {
        session = new Session();
        session.setExtensionId("TEST_EXTENSION_ID");
        session.setCompanyId("1");
        session.setAccessToken("TEST_ACCESS_TOKEN");
        session.setId("ID");
        extensionProperties = new ExtensionProperties();
        extensionProperties.setBaseUrl("TEST_BASE_URL");
        extensionProperties.setCluster("TEST_CLUSTER");
        extensionProperties.setApiKey("TEST_API_KEY");
        extensionProperties.setScopes("TEST_SCOPE");

        platformConfig = new PlatformConfig("1", "TEST_API_KEY", "TEST_API_SECRET", "http://localhost:8080",Boolean.FALSE);
        platformClient = new PlatformClient(platformConfig);
    }

    @Test
    public void testGetPlatformClient() {
        when(sessionStorage.getSession(anyString())).thenReturn(session);

        when(ext.getExtensionProperties()).thenReturn(extensionProperties);
        when(ext.getExtensionProperties()).thenReturn(extensionProperties);
        when(ext.getPlatformClient(anyString(), any())).thenReturn(platformClient);
        PlatformClient result = extensionService.getPlatformClient("1");
        verify(ext, times(1)).getPlatformClient(anyString(), any());
        Assert.assertNotNull(result);
        Assert.assertEquals(result, platformClient);
    }
}
