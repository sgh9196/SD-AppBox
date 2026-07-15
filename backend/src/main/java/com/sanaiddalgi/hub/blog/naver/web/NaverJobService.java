package com.sanaiddalgi.hub.blog.naver.web;

import com.sanaiddalgi.hub.blog.naver.model.NaverJobStatus;
import com.sanaiddalgi.hub.blog.naver.model.NaverSessionStatus;
import com.sanaiddalgi.hub.blog.naver.service.NaverSessionService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 네이버 Job 생성·조회. */
@Service
public class NaverJobService {

    private final Map<String, NaverJob> jobs = new ConcurrentHashMap<>();
    private final NaverJobRunner runner;
    private final NaverSessionService sessionService;

    public NaverJobService(NaverJobRunner runner, NaverSessionService sessionService) {
        this.runner = runner;
        this.sessionService = sessionService;
    }

    public NaverSessionStatus sessionStatus() {
        return sessionService.getStatus();
    }

    public NaverJob startBrowserLogin(String blogJobId) {
        NaverJob job = new NaverJob(NaverJob.Type.LOGIN_BROWSER);
        if (blogJobId != null && !blogJobId.isBlank()) {
            job.setBlogJobId(blogJobId.trim());
        }
        jobs.put(job.getId(), job);
        runner.runBrowserLogin(job);
        return job;
    }

    public NaverJob startPublish(String blogJobId) {
        NaverJob job = new NaverJob(NaverJob.Type.PUBLISH);
        job.setBlogJobId(blogJobId);
        jobs.put(job.getId(), job);
        runner.runPublish(job);
        return job;
    }

    public NaverJob getJob(String id) {
        return jobs.get(id);
    }

    public NaverJobStatus toStatus(NaverJob job) {
        if (job == null) {
            return null;
        }
        NaverJobStatus dto = new NaverJobStatus();
        dto.setJobId(job.getId());
        dto.setType(job.getType().name());
        dto.setStatus(job.getStatus().name());
        dto.setMessage(job.getMessage());
        dto.setBlogJobId(job.getBlogJobId());
        dto.setResultUrl(job.getResultUrl());
        return dto;
    }

    public void logout() throws java.io.IOException {
        sessionService.clearSession();
    }
}
