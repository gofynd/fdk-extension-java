# fdk-extension-java

FDK Extension Helper Library

#### Initial Setup
1. Create Maven project and add the dependency in the pom.xml 
```
<dependency>
    <groupId>com.github.gofynd</groupId>
    <artifactId>fdk-extension-java</artifactId>
    <version><LATEST_VERSION></version>
</dependency>
```
2. Add the Jitpack Repo to your root pom.xml:
   ```
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
      integration_id : <INTEGRATION_ID>
      api_key : <API_KEY>
      api_secret : <API_SECRET>
      scope : 'company/saleschannel'
      base_url : 'https://test.extension.com'
      access_mode : 'offline'
    ```
4. Create Main Application class and Initialise the Extension using the properties.
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.fynd.**", "com.fynd.**","com.gofynd","com.sdk.**"})
public class EmailExtensionApplication {
    
    private String CACHE_PREFIX_KEY  = "inv_email";

    @Autowired
    ExtensionProperties extensionProperties;

    @Autowired
    @Qualifier("jedispoolbean")
    JedisPool jedis; //Library to connect with Redis Cache

    ExtensionCallback callbacks = new ExtensionCallback(
            (context) ->
            {
                Session fdkSession = (Session) context.get("fdk-session");
                return extensionProperties.getBase_url() + "/company/" + fdkSession.getCompany_id();
            }, (context) ->
                System.out.println("in auth callback")
            , (context) ->
                System.out.println("in uninstall callback")
            );


    public static void main(String[] args) {
        SpringApplication.run(EmailExtensionApplication.class, args);
    }

    @Bean
    public com.fynd.extension.model.Extension getExtension() {
        return Extension.initialize(extensionProperties,
                new RedisStorage(jedis, CACHE_PREFIX_KEY), //BaseStorage is the parent class, any child class can be used here - REDIS / Memory
                callbacks);
    }
    
    
}
```

5. Define Redis Service which is used to save the intermediate Sessions for each CompanyID
```java
public class RedisService {

    @Autowired
    RedisConfig redisConfig;

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
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60)
                                                         .toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30)
                                                            .toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        URI redisUri = new URI(redisConfig.getRedisHost());
        jedisPool = new JedisPool(poolConfig, redisUri); //Use SSL if necessary in envs
        return jedisPool;
    }


    @PreDestroy
    public void destroy() {
        logger.info("Closing the jedis pool connection");
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

#### How to register for Webhook Events?
Webhook events can be helpful to handle tasks when certain events occur on platform. You can subscribe to such events by passing **webhook** in Extension Configuration Property

1. Add the Configuration property in Extension yaml File

```yaml
webhook:
  api_path: "/webhook" #<POST API URL>
  notification_email: "<EMAIL_ID>"
  subscribe_on_install: false, #optional. Default true
  subscribed_saleschannel: 'all' #Can be 'SPECIFIC'/'EMPTY'
  event_map:
    -
      name: 'extension/install'
      handler: extensionInstallHandler #Make sure this matches the Component Bean name
    -
      name: 'product/update'
      handler: productCreateHandler
    -
      name: 'product/update'
      handler: productCreateApplicationHandler
      category: 'application' # optional unless multiple event with same name are present at company and saleschannel

```

2. Create Handlers for each event which is mentioned in the Event Map (as specified above)

```java
import com.fynd.extension.middleware.EventHandler;

@Component("extensionInstallHandler")
public class ExtensionInstallHandler extends EventHandler {

   @java.lang.Override
   public void handle(String eventName, Object body, String companyId, String applicationId) {
      //Write Business logic here
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
         return new Collections.singletonMap("success", true);
      } catch (Exception e) {
         log.error("Exception occurred", e);
         return new Collections.singletonMap("success", false);
      }
   }
}
```

> Setting `subscribed_saleschannel` as "specific" means, you will have to manually subscribe saleschannel level event for individual saleschannel. Default value here is "all" and event will be subscribed for all sales channels. 

> For enabling events manually use function `enableSalesChannelWebhook`
> 
> To disable receiving events for a saleschannel use function `disableSalesChannelWebhook`.


##### How webhook registry subscribes to webhooks on Fynd Platform?
After webhook config is passed to initialize whenever extension is launched to any of companies where extension is installed or to be installed, webhook config data is used to create webhook subscriber on Fynd Platform for that company.

> Any update to webhook config will not automatically update subscriber data on Fynd Platform for a company until extension is opened atleast once after the update.

Other way to update webhook config manually for a company is to call `syncEvents` function of webhookRegistery.   

