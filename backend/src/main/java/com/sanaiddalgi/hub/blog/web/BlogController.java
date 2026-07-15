package com.sanaiddalgi.hub.blog.web;

import com.sanaiddalgi.hub.config.StudioProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * 블로그 원고 생성 REST API.
 * POST /generate → Job ID 반환, GET /jobs/{id} 폴링, GET /jobs/{id}/download → preview.docx
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    private final BlogJobService blogJobService;

    public BlogController(BlogJobService blogJobService) {
        this.blogJobService = blogJobService;
    }

    @PostMapping("/generate")
    public Map<String, String> generate(
            @RequestParam(defaultValue = "review") String contentKind,
            @RequestParam(defaultValue = "") String storeName,
            @RequestParam(defaultValue = "") String region,
            @RequestParam(defaultValue = "협찬") String postType,
            @RequestParam(defaultValue = "글로벌") String bloggerName,
            @RequestParam(defaultValue = "5") int rating,
            @RequestParam(defaultValue = "") String infoText,
            @RequestParam(defaultValue = "") String link,
            @RequestParam(defaultValue = "") String campaignGuideline,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String userNotes,
            HttpServletRequest request) throws IOException {
        Map<String, List<MultipartFile>> uploads = collectReviewUploads(request);
        BlogJob job = blogJobService.createJob(
                storeName, region, postType, bloggerName, rating, infoText, link, campaignGuideline, uploads);
        return Map.of("jobId", job.getId());
    }

    @GetMapping("/jobs/{id}")
    public Map<String, String> jobStatus(@PathVariable String id) {
        BlogJob job = blogJobService.getJob(id);
        if (job == null) {
            return Map.of("status", "FAILED", "message", "작업을 찾을 수 없습니다.");
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", job.getStatus().name());
        result.put("message", job.getMessage() == null ? "" : job.getMessage());
        if (job.getDownloadUrl() != null) {
            result.put("downloadUrl", job.getDownloadUrl());
        }
        result.put("jobId", job.getId());
        return result;
    }

    @GetMapping("/jobs/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        BlogJob job = blogJobService.getJob(id);
        if (job == null || job.getOutputPath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Path.of(job.getOutputPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=preview.docx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /** multipart 필드명 = 사진 카테고리(external, interior, menu, product). */
    private Map<String, List<MultipartFile>> collectReviewUploads(HttpServletRequest request) {
        Map<String, List<MultipartFile>> uploads = new LinkedHashMap<>();
        for (String category : StudioProperties.PHOTO_CATEGORIES) {
            uploads.put(category, new ArrayList<>());
        }
        if (request instanceof MultipartHttpServletRequest multipart) {
            for (String category : StudioProperties.PHOTO_CATEGORIES) {
                List<MultipartFile> files = multipart.getFiles(category);
                if (files != null) {
                    uploads.put(category, files);
                }
            }
        }
        return uploads;
    }


}
