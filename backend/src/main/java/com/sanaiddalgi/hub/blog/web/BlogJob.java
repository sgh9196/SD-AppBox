package com.sanaiddalgi.hub.blog.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 블로그 원고 생성 작업 상태. 프론트가 jobId로 폴링. */
public class BlogJob {

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    private final String id;
    private volatile Status status = Status.PENDING;
    private volatile String message = "대기 중";
    private volatile String downloadUrl;
    private volatile String outputPath;
    private final Instant createdAt = Instant.now();
    private String storeName;
    private String region;
    private String postType;
    private int rating = 5;
    private String infoText;
    private String link;
    private String campaignGuideline;
    private String contentKind = "review";
    private String userNotes;
    private String bloggerName;
    private String draft;
    private String title;
    private Map<String, List<String>> photoData = Map.of();

    public BlogJob(String id) {
        this.id = id;
    }

    public static BlogJob create() {
        return new BlogJob(UUID.randomUUID().toString());
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPostType() {
        return postType;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getInfoText() {
        return infoText;
    }

    public void setInfoText(String infoText) {
        this.infoText = infoText;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getCampaignGuideline() {
        return campaignGuideline;
    }

    public void setCampaignGuideline(String campaignGuideline) {
        this.campaignGuideline = campaignGuideline;
    }

    public String getContentKind() {
        return contentKind;
    }

    public void setContentKind(String contentKind) {
        this.contentKind = contentKind;
    }

    public String getUserNotes() {
        return userNotes;
    }

    public void setUserNotes(String userNotes) {
        this.userNotes = userNotes;
    }

    public String getDraft() {
        return draft;
    }

    public void setDraft(String draft) {
        this.draft = draft;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, List<String>> getPhotoData() {
        return photoData;
    }

    public void setPhotoData(Map<String, List<String>> photoData) {
        this.photoData = photoData;
    }

    public String getBloggerName() {
        return bloggerName;
    }

    public void setBloggerName(String bloggerName) {
        this.bloggerName = bloggerName;
    }
}
