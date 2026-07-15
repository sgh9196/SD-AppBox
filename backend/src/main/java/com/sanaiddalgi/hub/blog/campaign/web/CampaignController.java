package com.sanaiddalgi.hub.blog.campaign.web;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignFetchLogEntry;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignPageResult;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignRefreshStatus;
import com.sanaiddalgi.hub.blog.campaign.service.CampaignFetchLogService;
import com.sanaiddalgi.hub.blog.campaign.service.CampaignRefreshService;
import com.sanaiddalgi.hub.blog.campaign.service.CampaignService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 체험단 캠페인 목록·캐시 시각·비동기 갱신 API. */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignRefreshService refreshService;
    private final CampaignFetchLogService logService;

    public CampaignController(
            CampaignService campaignService,
            CampaignRefreshService refreshService,
            CampaignFetchLogService logService) {
        this.campaignService = campaignService;
        this.refreshService = refreshService;
        this.logService = logService;
    }

    @GetMapping("/sorted")
    public CampaignPageResult sorted(
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "전체") String platform,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (refresh && !refreshService.isRunning()) {
            refreshService.startRefresh();
        }
        List<CampaignCard> cards = campaignService.fetchAllCampaigns(false);
        cards = campaignService.sortCampaigns(cards, sortBy, platform);
        if (region != null && !region.isBlank() && !"전체".equals(region)) {
            cards = cards.stream()
                    .filter(c -> campaignService.matchesRegion(c, region))
                    .toList();
        }
        return campaignService.paginate(cards, page, size);
    }

    @GetMapping("/regions")
    public List<String> regions(@RequestParam(defaultValue = "false") boolean refresh) {
        if (refresh && !refreshService.isRunning()) {
            refreshService.startRefresh();
        }
        return campaignService.listRegions(campaignService.fetchAllCampaigns(false));
    }

    @GetMapping("/cache-age")
    public Map<String, String> cacheAge() {
        return Map.of("label", campaignService.cacheAgeLabel());
    }

    @PostMapping("/refresh")
    public Map<String, Object> startRefresh() {
        boolean started = refreshService.startRefresh();
        return Map.of(
                "started", started,
                "status", refreshService.getStatus());
    }

    @GetMapping("/refresh/preview")
    public CampaignPageResult refreshPreview(
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "전체") String platform,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!refreshService.isRunning()) {
            return new CampaignPageResult(page, size, 0, List.of(), false);
        }
        return campaignService.pageFromCards(
                refreshService.getPreviewCards(), sortBy, platform, region, page, size, true);
    }

    @GetMapping("/refresh/status")
    public CampaignRefreshStatus refreshStatus() {
        return refreshService.getStatus();
    }

    @GetMapping("/refresh/logs")
    public List<CampaignFetchLogEntry> refreshLogs(
            @RequestParam(defaultValue = "0") long after) {
        return logService.getLogsAfter(after);
    }
}
