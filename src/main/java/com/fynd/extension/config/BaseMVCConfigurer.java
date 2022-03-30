package com.fynd.extension.config;

import com.fynd.extension.middleware.ApplicationInterceptor;
import com.fynd.extension.middleware.PlatformInterceptor;
import com.fynd.extension.middleware.SessionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BaseMVCConfigurer implements WebMvcConfigurer {

    @Autowired
    SessionInterceptor sessionInterceptor;

    @Autowired
    PlatformInterceptor platformInterceptor;

    @Autowired
    ApplicationInterceptor applicationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/fp/auth", "/platform/**")
                .order(Ordered.HIGHEST_PRECEDENCE);
        registry.addInterceptor(platformInterceptor)
                .addPathPatterns("/platform/**")
                .order(Ordered.LOWEST_PRECEDENCE);
        registry.addInterceptor(applicationInterceptor)
                .addPathPatterns("/application/**");
    }
}

