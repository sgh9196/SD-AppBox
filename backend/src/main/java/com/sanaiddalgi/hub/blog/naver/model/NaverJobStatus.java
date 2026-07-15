package com.sanaiddalgi.hub.blog.naver.model;

/** 네이버 로그인·발행 Job 상태. */
public class NaverJobStatus {

    private String jobId;
    private String type;
    private String status;
    private String message;
    private String blogJobId;
    private String resultUrl;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBlogJobId() {
        return blogJobId;
    }

    public void setBlogJobId(String blogJobId) {
        this.blogJobId = blogJobId;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }
}
