package com.sanaiddalgi.hub.blog.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.sanaiddalgi.hub.blog.service.MediaService;

/** 블로그 생성 Job 생성·조회. Job 상태는 메모리(ConcurrentHashMap)에 보관. */
@Service
public class BlogJobService {

    private final Map<String, BlogJob> jobs = new ConcurrentHashMap<>();
    private final BlogJobRunner blogJobRunner;
    private final MediaService mediaService;

    public BlogJobService(BlogJobRunner blogJobRunner, MediaService mediaService) {
        this.blogJobRunner = blogJobRunner;
        this.mediaService = mediaService;
    }

    public BlogJob createJob(
            String storeName,
            String region,
            String postType,
            String bloggerName,
            int rating,
            String infoText,
            String link,
            String campaignGuideline,
            Map<String, List<MultipartFile>> uploads,
            String apiKey) throws IOException {
        BlogJob job = BlogJob.create();
        job.setContentKind("review");
        job.setStoreName(storeName);
        job.setRegion(region);
        job.setPostType(postType);
        job.setBloggerName(bloggerName);
        job.setRating(rating);
        job.setInfoText(infoText);
        job.setLink(link);
        job.setCampaignGuideline(campaignGuideline);
        job.setGeminiApiKey(apiKey);
        // Tomcat 임시 파일은 요청 종료 후 삭제되므로, 비동기 Job 전에 영구 저장
        job.setPhotoData(mediaService.saveUploadedPhotos(uploads));
        jobs.put(job.getId(), job);
        blogJobRunner.runJob(job);
        return job;
    }



    public BlogJob getJob(String id) {
        return jobs.get(id);
    }
}
