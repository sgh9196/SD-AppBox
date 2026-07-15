package com.sanaiddalgi.hub.blog.naver.web;

import com.sanaiddalgi.hub.blog.naver.model.NaverJobStatus;
import com.sanaiddalgi.hub.blog.naver.model.NaverLogEntry;
import com.sanaiddalgi.hub.blog.naver.model.NaverSessionStatus;
import com.sanaiddalgi.hub.blog.naver.service.NaverLogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 네이버 로그인·블로그 자동 작성 API. */
@RestController
@RequestMapping("/api/naver")
public class NaverController {

    private final NaverJobService jobService;
    private final NaverLogService logService;

    public NaverController(NaverJobService jobService, NaverLogService logService) {
        this.jobService = jobService;
        this.logService = logService;
    }

    @GetMapping("/session")
    public NaverSessionStatus session() {
        return jobService.sessionStatus();
    }

    @PostMapping("/login/browser")
    public Map<String, Object> loginBrowser(@RequestBody(required = false) Map<String, String> body) {
        String blogJobId = body != null ? body.getOrDefault("blogJobId", "").trim() : "";
        NaverJob job = jobService.startBrowserLogin(blogJobId.isBlank() ? null : blogJobId);
        return Map.of("started", true, "jobId", job.getId(), "status", jobService.toStatus(job));
    }

    @PostMapping("/logout")
    public Map<String, String> logout() throws java.io.IOException {
        jobService.logout();
        return Map.of("message", "로그아웃 완료");
    }

    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestBody Map<String, String> body) {
        String blogJobId = body.getOrDefault("blogJobId", "").trim();
        if (blogJobId.isBlank()) {
            return Map.of("started", false, "message", "blogJobId가 필요합니다.");
        }
        NaverJob job = jobService.startPublish(blogJobId);
        return Map.of("started", true, "jobId", job.getId(), "status", jobService.toStatus(job));
    }

    @GetMapping("/jobs/{id}")
    public NaverJobStatus jobStatus(@PathVariable String id) {
        NaverJob job = jobService.getJob(id);
        if (job == null) {
            NaverJobStatus missing = new NaverJobStatus();
            missing.setStatus("FAILED");
            missing.setMessage("작업을 찾을 수 없습니다.");
            return missing;
        }
        return jobService.toStatus(job);
    }

    @GetMapping("/jobs/{id}/logs")
    public List<NaverLogEntry> jobLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") long after) {
        return logService.getLogsAfter(after);
    }
}
