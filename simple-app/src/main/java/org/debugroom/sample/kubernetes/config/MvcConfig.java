package org.debugroom.sample.kubernetes.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ComponentScan("org.debugroom.sample.kubernetes.app.web")
@Configuration
public class MvcConfig implements WebMvcConfigurer {
}
