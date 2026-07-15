package com.sanaiddalgi.hub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/** 공통 Bean: 외부 HTTP 클라이언트, 블로그 원고 생성용 비동기 스레드풀. */
@Configuration
@EnableAsync
public class AppConfig {

    /** 공공 API·네이버·Gemini 호출에 사용. 브라우저 User-Agent로 위장. */
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                .build();
    }

    /** BlogJobRunner @Async 전용 실행기. */
    @Bean(name = "blogTaskExecutor")
    public ThreadPoolTaskExecutor blogTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("blog-job-");
        executor.initialize();
        return executor;
    }

    /** 체험단 캐시 갱신 @Async 전용 실행기. */
    @Bean(name = "campaignTaskExecutor")
    public ThreadPoolTaskExecutor campaignTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("campaign-refresh-");
        executor.initialize();
        return executor;
    }

    /** 체험단 사이트 병렬 HTTP 수집용 실행기. */
    @Bean(name = "campaignFetchExecutor")
    public ThreadPoolTaskExecutor campaignFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("campaign-fetch-");
        executor.initialize();
        return executor;
    }

    /** 네이버 Playwright Job 전용 실행기 (동시 1건). */
    @Bean(name = "naverTaskExecutor")
    public ThreadPoolTaskExecutor naverTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("naver-job-");
        executor.initialize();
        return executor;
    }
}
