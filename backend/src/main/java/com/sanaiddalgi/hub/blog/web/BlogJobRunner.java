package com.sanaiddalgi.hub.blog.web;

import com.sanaiddalgi.hub.blog.service.ContentService;
import com.sanaiddalgi.hub.blog.service.DraftSanitizer;
import com.sanaiddalgi.hub.blog.service.ExportService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 비동기로 Gemini 원고 생성 → Word(docx)보내기까지 수행. */
@Service
public class BlogJobRunner {

    private final ContentService contentService;
    private final ExportService exportService;
    private final DraftSanitizer draftSanitizer;

    public BlogJobRunner(
            ContentService contentService, ExportService exportService, DraftSanitizer draftSanitizer) {
        this.contentService = contentService;
        this.exportService = exportService;
        this.draftSanitizer = draftSanitizer;
    }

    @Async("blogTaskExecutor")
    public void runJob(BlogJob job) {
        job.setStatus(BlogJob.Status.RUNNING);
        job.setMessage("원고 생성 중...");
        try {
            runReviewJob(job);
        } catch (Exception e) {
            job.setStatus(BlogJob.Status.FAILED);
            job.setMessage(e.getMessage() == null ? "생성 실패" : e.getMessage());
        }
    }

    private void runReviewJob(BlogJob job) throws Exception {
        Map<String, List<String>> photoData = job.getPhotoData();
        String draft = contentService.generateBlogPost(
                job.getStoreName(),
                job.getInfoText(),
                job.getLink(),
                job.getPostType(),
                job.getBloggerName(),
                job.getRating(),
                job.getCampaignGuideline(),
                photoData,
                job::setMessage);
        draft = draftSanitizer.sanitizeGeneratedDraft(draft, job.getBloggerName());
        finalizeJob(job, draft, photoData, buildReviewTitle(job), "");
    }


    private void finalizeJob(
            BlogJob job,
            String draft,
            Map<String, List<String>> photoData,
            String title,
            String warning) throws Exception {
        job.setDraft(draft);
        job.setTitle(title);
        job.setMessage("Word 문서 생성 중...");
        Path output = exportService.createDocx(draft, photoData, job.getBloggerName());
        job.setOutputPath(output.toString());
        job.setDownloadUrl("/api/blog/jobs/" + job.getId() + "/download");
        job.setStatus(BlogJob.Status.COMPLETED);
        job.setMessage("완료" + warning);
    }

    private String buildReviewTitle(BlogJob job) {
        String region = job.getRegion() == null ? "" : job.getRegion().trim();
        String store = job.getStoreName() == null ? "" : job.getStoreName().trim();
        if (!region.isBlank() && !"전체".equals(region)) {
            return "[" + region + "] " + store + " 방문 후기";
        }
        return store + " 방문 후기";
    }

}
