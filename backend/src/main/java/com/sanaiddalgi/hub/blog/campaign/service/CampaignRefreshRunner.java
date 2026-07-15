package com.sanaiddalgi.hub.blog.campaign.service;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 체험단 전체 수집을 백그라운드에서 실행. */
@Service
public class CampaignRefreshRunner {

    private final CampaignService campaignService;
    private final CampaignFetchLogService logService;
    private final CampaignRefreshService refreshService;

    public CampaignRefreshRunner(
            CampaignService campaignService,
            CampaignFetchLogService logService,
            CampaignRefreshService refreshService) {
        this.campaignService = campaignService;
        this.logService = logService;
        this.refreshService = refreshService;
    }

    @Async("campaignTaskExecutor")
    public void runRefresh() {
        try {
            List<CampaignCard> merged = campaignService.fetchLiveAllWithLog(logService);
            campaignService.saveLiveCache(merged);
            refreshService.markCompleted(merged.size());
            logService.info("캐시 저장 완료 — 총 " + merged.size() + "건");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "수집 실패";
            refreshService.markFailed(msg);
            logService.warn("수집 실패: " + msg);
        }
    }
}
