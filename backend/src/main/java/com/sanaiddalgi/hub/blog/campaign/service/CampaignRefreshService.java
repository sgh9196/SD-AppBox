package com.sanaiddalgi.hub.blog.campaign.service;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignRefreshStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 체험단 캐시 갱신 Job 상태 관리. */
@Service
public class CampaignRefreshService {

    public static final String IDLE = "idle";
    public static final String RUNNING = "running";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";

    private final CampaignFetchLogService logService;
    private final CampaignRefreshRunner runner;

    private volatile String status = IDLE;
    private volatile String message = "";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile int totalFetched;
    private volatile List<CampaignCard> previewCards = List.of();

    public CampaignRefreshService(
            CampaignFetchLogService logService,
            @Lazy CampaignRefreshRunner runner) {
        this.logService = logService;
        this.runner = runner;
    }

    public synchronized boolean startRefresh() {
        if (RUNNING.equals(status)) {
            return false;
        }
        status = RUNNING;
        message = "수집 준비 중…";
        startedAt = Instant.now();
        finishedAt = null;
        totalFetched = 0;
        previewCards = List.of();
        logService.clear();
        logService.info("체험단 캐시 갱신을 시작합니다.");
        runner.runRefresh();
        return true;
    }

    public void markCompleted(int count) {
        totalFetched = count;
        status = COMPLETED;
        message = "수집 완료: " + count + "건";
        finishedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        status = FAILED;
        message = errorMessage;
        finishedAt = Instant.now();
    }

    public void updatePreview(List<CampaignCard> cards) {
        previewCards = List.copyOf(cards);
        totalFetched = cards.size();
    }

    public void clearPreview() {
        previewCards = List.of();
        totalFetched = 0;
    }

    public List<CampaignCard> getPreviewCards() {
        return previewCards;
    }

    public CampaignRefreshStatus getStatus() {
        CampaignRefreshStatus dto = new CampaignRefreshStatus();
        dto.setStatus(status);
        dto.setMessage(message);
        dto.setStartedAt(startedAt != null ? startedAt.toString() : null);
        dto.setFinishedAt(finishedAt != null ? finishedAt.toString() : null);
        dto.setTotalFetched(totalFetched);
        return dto;
    }

    public boolean isRunning() {
        return RUNNING.equals(status);
    }
}
