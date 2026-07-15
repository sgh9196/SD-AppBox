package com.sanaiddalgi.hub.blog.naver.web;

import com.sanaiddalgi.hub.blog.naver.model.DraftBlock;
import com.sanaiddalgi.hub.blog.naver.service.DraftBlockParser;
import com.sanaiddalgi.hub.blog.naver.service.NaverBlogAutomation;
import com.sanaiddalgi.hub.blog.naver.service.NaverLogService;
import com.sanaiddalgi.hub.blog.web.BlogJob;
import com.sanaiddalgi.hub.blog.web.BlogJobService;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 네이버 브라우저 로그인·발행 Job 비동기 실행. */
@Service
public class NaverJobRunner {

    private final NaverBlogAutomation automation;
    private final NaverLogService logService;
    private final DraftBlockParser blockParser;
    private final BlogJobService blogJobService;

    public NaverJobRunner(
            NaverBlogAutomation automation,
            NaverLogService logService,
            DraftBlockParser blockParser,
            BlogJobService blogJobService) {
        this.automation = automation;
        this.logService = logService;
        this.blockParser = blockParser;
        this.blogJobService = blogJobService;
    }

    @Async("naverTaskExecutor")
    public void runBrowserLogin(NaverJob job) {
        job.setStatus(NaverJob.Status.RUNNING);
        job.setMessage("브라우저 준비 중…");
        logService.clear();
        try {
            String blogJobId = job.getBlogJobId();
            if (blogJobId != null && !blogJobId.isBlank()) {
                BlogJob blogJob = requireBlogJob(blogJobId);
                Map<String, List<String>> photoData = blogJob.getPhotoData();
                List<DraftBlock> blocks = blockParser.parse(blogJob.getDraft(), photoData, blogJob.getBloggerName());
                if (blocks.isEmpty()) {
                    throw new IllegalStateException("발행할 본문 블록이 없습니다.");
                }
                String url = automation.loginInBrowserAndPublish(
                        blogJob.getTitle(), blocks, logService::info);
                job.setResultUrl(url);
                job.setStatus(NaverJob.Status.COMPLETED);
                job.setMessage("로그인 후 네이버 임시저장 완료");
            } else {
                automation.waitForBrowserLogin(logService::info);
                job.setStatus(NaverJob.Status.COMPLETED);
                job.setMessage("네이버 로그인 완료");
            }
            logService.info("Job 완료");
        } catch (Exception e) {
            job.setStatus(NaverJob.Status.FAILED);
            job.setMessage(sanitizeError(e));
            logService.warn("실패: " + job.getMessage());
        }
    }

    @Async("naverTaskExecutor")
    public void runPublish(NaverJob job) {
        job.setStatus(NaverJob.Status.RUNNING);
        job.setMessage("네이버 발행 중…");
        logService.clear();
        try {
            BlogJob blogJob = requireBlogJob(job.getBlogJobId());
            List<DraftBlock> blocks = blockParser.parse(blogJob.getDraft(), blogJob.getPhotoData(), blogJob.getBloggerName());
            if (blocks.isEmpty()) {
                throw new IllegalStateException("발행할 본문 블록이 없습니다.");
            }
            logService.info("제목: " + blogJob.getTitle() + ", 블록 " + blocks.size() + "개");
            String url = automation.publish(blogJob.getTitle(), blocks, logService::info);
            job.setResultUrl(url);
            job.setStatus(NaverJob.Status.COMPLETED);
            job.setMessage("네이버 임시저장 완료");
            logService.info("Job 완료");
        } catch (Exception e) {
            job.setStatus(NaverJob.Status.FAILED);
            job.setMessage(sanitizeError(e));
            logService.warn("발행 실패: " + job.getMessage());
        }
    }

    private BlogJob requireBlogJob(String blogJobId) {
        BlogJob blogJob = blogJobService.getJob(blogJobId);
        if (blogJob == null) {
            throw new IllegalStateException("원고 Job을 찾을 수 없습니다.");
        }
        if (blogJob.getDraft() == null || blogJob.getDraft().isBlank()) {
            throw new IllegalStateException("원고가 없습니다. 먼저 원고 생성을 완료하세요.");
        }
        return blogJob;
    }

    private String sanitizeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "작업 실패";
        }
        if (msg.contains("Execution context was destroyed")) {
            return "페이지 이동 중 일시 오류 — 다시 시도해 주세요.";
        }
        if (msg.contains("Timeout") && msg.contains("exceeded")) {
            return "네이버 에디터 로딩 지연 — 페이지가 완전히 뜬 뒤 다시 시도하거나, Chrome 창을 가리지 말고 1분 정도 기다려 주세요.";
        }
        if (msg.contains("iframe") || msg.contains("에디터")) {
            return msg;
        }
        int stackIdx = msg.indexOf("name='Error");
        if (stackIdx > 0) {
            msg = msg.substring(0, stackIdx).trim();
        }
        if (msg.startsWith("Error { message='")) {
            msg = msg.substring("Error { message='".length());
            int end = msg.indexOf('\'');
            if (end > 0) {
                msg = msg.substring(0, end);
            }
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
