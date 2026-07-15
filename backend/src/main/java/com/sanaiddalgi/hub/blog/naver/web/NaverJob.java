package com.sanaiddalgi.hub.blog.naver.web;

import java.time.Instant;
import java.util.UUID;

/** 네이버 로그인·발행 Job. */
public class NaverJob {

    public enum Type {
        LOGIN_BROWSER, PUBLISH
    }

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    private final String id;
    private final Type type;
    private volatile Status status = Status.PENDING;
    private volatile String message = "대기 중";
    private volatile String resultUrl;
    private final Instant createdAt = Instant.now();
    private String naverId;
    private String blogJobId;

    public NaverJob(Type type) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
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

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getNaverId() {
        return naverId;
    }

    public void setNaverId(String naverId) {
        this.naverId = naverId;
    }

    public String getBlogJobId() {
        return blogJobId;
    }

    public void setBlogJobId(String blogJobId) {
        this.blogJobId = blogJobId;
    }
}
