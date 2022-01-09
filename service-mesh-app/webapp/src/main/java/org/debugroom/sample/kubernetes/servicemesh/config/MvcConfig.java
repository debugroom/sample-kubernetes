package org.debugroom.sample.kubernetes.servicemesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import org.debugroom.sample.kubernetes.servicemesh.app.web.interceptor.SetMenuInterceptor;

@ComponentScan("org.debugroom.sample.kubernetes.servicemesh.app.web")
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Bean
    SetMenuInterceptor setMenuInterceptor(){
        return new SetMenuInterceptor();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(setMenuInterceptor());
    }

}
