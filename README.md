# fdk-extension-java

FDK Extension Helper Library

#### Initial Setup

1. Create Maven project and add the dependency in the pom.xml

    ```xml
    <dependency>
        <groupId>com.github.gofynd</groupId>
        <artifactId>fdk-extension-java</artifactId>
        <version><LATEST_VERSION></version>
    </dependency>
    ```

2. Add the Jitpack Repo to your root pom.xml:

   ```xml
   <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    ```

3. Add Extension specific Configuration properties in application.yml file

    ```yaml
    redis :
      host : 'redis://127.0.0.1:6379'

    ext :
      api_key : <API_KEY>
      api_secret : <API_SECRET>
      scope : 'company/saleschannel'
      base_url : 'https://test.extension.com'
      access_mode : 'offline'
   
   fdk-extension:
      version: '0.5.0'
    ```

4. Create Main Application class and Initialise the Extension using the properties.

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.fynd.**","com.sdk.**"})
public class EmailExtensionApplication {

   private String CACHE_PREFIX_KEY  = "inv_email";

   @Autowired
   @Qualifier("jedispoolbean")
   JedisPool jedis;

   @Autowired
   ExtensionProperties extensionProperties;

   ExtensionCallback callbacks = new ExtensionCallback((request) -> {
      Session fdkSession = (Session) request.getAttribute("session");
      System.out.println("In Auth callback");
      if(request.getParameter("application_id") != null){
         return extensionProperties.getBaseUrl() + "/company/" + fdkSession.getCompanyId() + "/application/" + request.getParameter("application_id");
      }
      else {
         return extensionProperties.getBaseUrl() + "/company/" + fdkSession.getCompanyId();
      }
   }, (context) -> {
      System.out.println("In install callback");
      return  extensionProperties.getBaseUrl();

   }, (fdkSession) -> {
      System.out.println("In uninstall callback");
      return extensionProperties.getBaseUrl();

   }, (fdkSession) -> {
      System.out.println("In auto-install callback");
      return extensionProperties.getBaseUrl();
   });


   public static void main(String[] args) {
      SpringApplication.run(SampleApplication.class, args);
   }

   @Bean
   public com.fynd.extension.model.Extension getExtension() {
      Extension extension = new Extension();
      return extension.initialize(extensionProperties,
              new RedisStorage(jedis, CACHE_PREFIX_KEY), //BaseStorage is the parent class, any child class can be used here - REDIS / Memory
              callbacks);
   }
    
}
```

5. Define Redis Service which is used to save the intermediate Sessions for each CompanyID

```java
@Service
public class RedisService {

   @Value("${redis.host}")
   private String redisHost;

   JedisPool jedisPool;

   @Bean(name = "jedispoolbean")
   JedisPool getJedis() throws URISyntaxException {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(1100);
      poolConfig.setMaxIdle(16);
      poolConfig.setMinIdle(16);
      poolConfig.setTestOnBorrow(true);
      poolConfig.setTestOnReturn(true);
      poolConfig.setTestWhileIdle(true);
      poolConfig.setJmxEnabled(false);
      poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60)
              .toMillis());
      poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30)
              .toMillis());
      poolConfig.setNumTestsPerEvictionRun(3);
      poolConfig.setBlockWhenExhausted(true);
      jedisPool = new JedisPool(poolConfig, redisHost); //Use SSL if necessary in envs
      return jedisPool;
   }


   @PreDestroy
   public void destroy() {
      System.out.println("Closing the jedis pool connection");
      if (jedisPool != null)
         jedisPool.close();
      jedisPool = null;
   }
}
```

#### How to call platform apis?

To call platform api you need to have instance of `PlatformClient`. Instance holds methods for SDK classes.

extend `BasePlatformController` class to create controller which will add `PlatformClient` in request.

```java
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class PlatformController extends BasePlatformController {


    @GetMapping(value = "/applications", produces = "application/json")
    public ConfigurationPlatformModels.ApplicationsResponse getApplications(HttpServletRequest request) {
        try {
            PlatformClient platformClient = (PlatformClient) request.getAttribute("platformClient");
            ConfigurationPlatformModels.ApplicationsResponse applications
                    = platformClient.configuration.getApplications(1, 100, "");

            return applications;

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

    }
}
```

#### How to get platformClient for offline access_mode?

To obtain the `PlatformClient` for offline access mode in your Java extension, use the provided `ExtensionService` class.
This example demonstrates how to retrieve applications using the obtained `PlatformClient`

```java
@RestController
@RequestMapping("/api/v1")
public class ExampleOfflineAccessMode {

    @Autowired
    ExtensionService extensionService;

    @GetMapping(value = "/applications", produces = "application/json")
    public ConfigurationPlatformModels.ApplicationsResponse getApplications() {
        try {
            String companyId = "1";
            PlatformClient platformClient = extensionService.getPlatformClient(companyId);

            return platformClient.configuration.getApplications(1, 100, "");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
```

#### How to call partner apis?

To call partner api you need to have instance of `PartnerClient`. Instance holds methods for SDK classes.

extend `BasePartnerController` class to create controller which will add `PartnerClient` in request.

```java
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class PartnerController extends BasePartnerController {    

    @GetMapping(value = "/orgThemes", produces = "application/json")
    public ThemePartnerModels.MarketplaceThemeSchema getOrgThemes(HttpServletRequest request) {
        try {
            PartnerClient partnerClient = (PartnerClient) request.getAttribute("partnerClient");
            ThemePartnerModels.MarketplaceThemeSchema orgThemes = partnerClient.theme.getOrganizationThemes("published", null, null);

            return orgThemes;

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

    }
}
```

#### How to register for Webhook Events?

Webhook events can be helpful to handle tasks when certain events occur on platform. You can subscribe to such events by passing **webhook** in Extension Configuration Property

1. Add the Configuration property in application yaml File

```yaml
ext :
   api_key : <API_KEY>
   api_secret : <API_SECRET>
   scope : ""
   base_url : "https://test.extension.com"
   access_mode : "offline"
   webhook:
      api_path: "/webhook" #<POST API URL>
      notification_email: <EMAIL_ID>
      subscribe_on_install: false, #optional. Default true
      subscribed_saleschannel: 'specific' #Optional. Default all
      marketplace: true, # to receive marketplace saleschannel events. Only allowed when subscribed_saleschannel is set to specific
      event_map:
         - name: 'product/update'
           handler: productCreateHandler #Make sure this matches the Component Bean name
           category: 'company'
           version: '1'
           provider: 'rest' # If not provided, Default is 'rest'
         - name: 'product/delete'
           handler: productDeleteHandler
           category: 'company'
           version: '1'
           provider: 'kafka'
         - name: "brand/create"
           topic: "company-brand-create"
           category: "company"
           version: 1
           provider: "pub_sub"
         - name: "extension/install"
           queue: "extension-install"
           workflow_name: "extension"
           version: 1
           provider: "temporal"
         - name: "location/create"
           queue: "company-location-create"
           category: "company"
           version: 1
           provider: "sqs"
         - name: "product-size/create"
           event_bridge_name: "company-product-size-create"
           category: "company"
           version: 1
           provider: "event_bridge"
```

2. Create Handlers for each event which is mentioned in the Event Map (as specified above)

```java
@Component("productCreateHandler")
public class ProductCreateHandler implements com.fynd.extension.middleware.EventHandler {

   @Override
   public void handle(String eventName, Object body, String companyId, String applicationId) {
      // Code to handle webhook event
   }
}
```

> By default all webhook events all subscribed for all companies whenever they are installed. To disable this behavior set `subscribe_on_install` to `false`. If `subscribe_on_install` is set to false, you need to manually enable webhook event subscription by calling `syncEvents` method of `webhookRegistry`

There should be view on given api path to receive webhook call. It should be `POST` api path. Api view should call `processWebhook` method of `webhookRegistry` object available under `fdkClient` here.

> Here `processWebhook` will do payload validation with signature and calls individual handlers for event passed with webhook config.

```java
@CrossOrigin(origins = "*")
@RestController
@RequestMapping()
@Slf4j
public class WebhookController {

   @Autowired
   WebhookService webhookService;

   @PostMapping(path = "/webhook")
   public Map<String, Boolean> receiveWebhookEvents(HttpServletRequest httpServletRequest) {
      try {
         webhookService.processWebhook(httpServletRequest);
         return Collections.singletonMap("success", true);
      } catch (Exception e) {
         log.error("Exception occurred", e);
         return Collections.singletonMap("success", false);
      }
   }
}
```

> Setting `subscribed_saleschannel` as "specific" means, you will have to manually subscribe saleschannel level event for individual saleschannel. Default value here is "all" and event will be subscribed for all sales channels.

> For enabling events manually use function `enableSalesChannelWebhook`
>
> To disable receiving events for a saleschannel use function `disableSalesChannelWebhook`.


```java
   import com.fynd.extension.model.Extension;
   import com.sdk.platform.PlatformClient;
   import com.fynd.extension.service.WebhookService;
   import jakarta.servlet.http.HttpServletRequest;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.web.bind.annotation.GetMapping;
   import org.springframework.web.bind.annotation.RequestMapping;
   import org.springframework.web.bind.annotation.RestController;
   
   @RestController
   @RequestMapping("/api")
   @Slf4j
   public class PlatformController extends BasePlatformController {
      @GetMapping(value = "/enableSalesChannelWebhook", produces = "application/json")
      public String getProducts(HttpServletRequest request) {
         try {
            PlatformClient platformClient = (PlatformClient) request.getAttribute("platformClient");
            Extension extension = (Extension) request.getAttribute("extension");
            WebhookService webhookService = extension.getWebhookService();
            webhookService.enableSalesChannelWebhook(platformClient,"66e3b32eca4335d0feff486c");
            return "Webhook enabled successfully!";
         } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
         }

      }
    }

```

##### How webhook registry subscribes to webhooks on Fynd Platform?

After webhook config is passed to initialize whenever extension is launched to any of companies where extension is installed or to be installed, webhook config data is used to create webhook subscriber on Fynd Platform for that company.

> Any update to webhook config will not automatically update subscriber data on Fynd Platform for a company until extension is opened atleast once after the update.

Other way to update webhook config manually for a company is to call `syncEvents` function of webhookRegistry.

#### How to create custom storage class?
Custom storage classes expand data storage options beyond default choices like Redis and in-memory storage. You would required to create a custom storage class by extending the base storage class provided by fdk extension java library and implementing required methods as per your chosen database.

```java
import com.fynd.extension.storage.BaseStorage;
public class MyCustomStorage extends BaseStorage {
    private StorgeClient storgeClient;
    // StorgeClient is connection variable to your storage.
    public MyCustomStorage(StorgeClient storgeClient, String prefixKey) {
        super(prefixKey);
        this.storgeClient = storgeClient;
    }

    @Override
    public String get(String key) {
        try (StorgeClient storgeClient = storgeClient.getResource()) {
            return storgeClient.get(super.prefixKey + key);
        }
    }
    
    //  All of the below methods need to be implemented as per your chosen databse

    public String set(String key, String value) {
        // Implementation of a set method
    }

    public Object del(String key) {
        // Implementation of a del method
    }

    public String setex(String key, int ttl, String value) {
        // Implementation of a setex method
    }

    public String hget(String key, String hashKey) {
        // Implementation of a hget method
    }

    public Object hset(String key, String hashKey, String value) {
        // Implementation of a hset method       
    }

    public Map<String, Object> hgetall(String key) {
        // Implementation of a hgetall method
    }
}

```

