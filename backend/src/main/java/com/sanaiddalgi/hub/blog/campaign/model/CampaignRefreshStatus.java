package com.sanaiddalgi.hub.blog.campaign.model;

/** 체험단 캐시 갱신 Job 상태. */
public class CampaignRefreshStatus {

    private String status;
    private String message;
    private String startedAt;
    private String finishedAt;
    private int totalFetched;

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

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getTotalFetched() {
        return totalFetched;
    }

    public void setTotalFetched(int totalFetched) {
        this.totalFetched = totalFetched;
    }
}
