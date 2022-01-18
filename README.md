# fdk-extension-java

FDK Extension Helper Library

#### Initial Setup
1. Create Maven project and add the dependency in the pom.xml 
```
<dependency>
    <groupId>com.github.gofynd</groupId>
    <artifactId>fdk-extension-java</artifactId>
    <version>v0.0.2-RELEASE</version>
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
3. Mention Extension specific Configuration properties in [Interfaces-config Project](https://gitlab.com/fynd/vision/configurations/interfaces-config)
    ```
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

    @Autowired
    ExtensionProperties extensionProperties;

    @Autowired
    @Qualifier("jedispoolbean")
    JedisPool jedis;

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
        return Extension.initialize(extensionProperties.getApi_key(), extensionProperties.getApi_secret(),
                                             new RedisStorage(jedis, "inv_email"),
                                             List.of(extensionProperties.getScope()),
                                             extensionProperties.getBase_url(),
                                             callbacks, extensionProperties.getAccess_mode(),
                                             "https://api.fyndx1.de");  // this is optional by default it points to prod.
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
```java
public class Service {
    PlatformClient platformClient = ExtensionContext.get("platform-client",PlatformClient.class);
    Session session = ExtensionContext.get("fdk-session", Session.class);
    PlatformModels.ResponseEnvelopeListJobConfigDTO listDto = platformClient.inventory.getJobByCompanyAndIntegration(
            Integer.valueOf(session.getCompany_id()),
            extensionProperties.getIntegration_id(), 1, 1);
        
    PlatformModels.ResponseEnvelopeJobConfigDTO configDefaults = platformClient.inventory.getJobConfigDefaults(
            Integer.valueOf(session.getCompany_id()));
}
```