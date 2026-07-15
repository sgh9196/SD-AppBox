package com.sanaiddalgi.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/** 글로벌 (Geul-o-bel) — Spring Boot 진입점. 비동기 블로그 Job 처리를 위해 @EnableAsync 사용. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class HubApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubApplication.class, args);
    }
}
