package com.sanaiddalgi.hub.blog.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignPageResult;
import com.sanaiddalgi.hub.blog.campaign.repo.DinnerqueenFetcher;
import com.sanaiddalgi.hub.blog.campaign.repo.GabojaFetcher;
import com.sanaiddalgi.hub.blog.campaign.repo.GangnamMatzipFetcher;
import com.sanaiddalgi.hub.blog.campaign.repo.ParseUtils;
import com.sanaiddalgi.hub.config.StudioProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 3플랫폼(디너의여왕·가보자·강남맛집) 체험단 병합, JSON 캐시, 필터·정렬. */
@Service
public class CampaignService {

    private static final String CACHE_ALL = "전체";

    private final StudioProperties properties;
    private final GabojaFetcher gabojaFetcher;
    private final DinnerqueenFetcher dinnerqueenFetcher;
    private final GangnamMatzipFetcher gangnamMatzipFetcher;
    private final ObjectMapper objectMapper;
    private final CampaignRefreshService refreshService;

    public CampaignService(
            StudioProperties properties,
            GabojaFetcher gabojaFetcher,
            DinnerqueenFetcher dinnerqueenFetcher,
            GangnamMatzipFetcher gangnamMatzipFetcher,
            ObjectMapper objectMapper,
            @Lazy CampaignRefreshService refreshService) {
        this.properties = properties;
        this.gabojaFetcher = gabojaFetcher;
        this.dinnerqueenFetcher = dinnerqueenFetcher;
        this.gangnamMatzipFetcher = gangnamMatzipFetcher;
        this.objectMapper = objectMapper;
        this.refreshService = refreshService;
    }

    /** TTL 내 캐시 우선, refresh=true 또는 만료 시 전 지역 재스크래핑 후 병합 */
    public List<CampaignCard> fetchAllCampaigns(boolean refresh) {
        if (!refresh) {
            List<CampaignCard> cached = loadCache(CACHE_ALL);
            if (cached != null) {
                return cached;
            }
        }
        List<CampaignCard> merged = fetchLiveAll();
        saveCache(merged, CACHE_ALL);
        return merged;
    }

    /** 수집 중 화면 미리보기 — 음식 필터·중복 제거·우선 지역 정렬 */
    public List<CampaignCard> prepareDisplayList(List<CampaignCard> raw) {
        List<CampaignCard> filtered = raw.stream()
                .filter(c -> ParseUtils.isFoodCampaign(c.getTitle(), c.getCategory(), c.getBenefit()))
                .toList();
        return prioritizeRegion(dedupe(filtered));
    }

    public CampaignPageResult pageFromCards(
            List<CampaignCard> cards,
            String sortBy,
            String platform,
            String region,
            int page,
            int size,
            boolean partial) {
        List<CampaignCard> sorted = sortCampaigns(cards, sortBy, platform);
        if (region != null && !region.isBlank() && !"전체".equals(region)) {
            sorted = sorted.stream().filter(c -> matchesRegion(c, region)).toList();
        }
        CampaignPageResult result = paginate(sorted, page, size);
        result.setPartial(partial);
        return result;
    }

    /** 비동기 갱신 Job에서 호출 — 진행 로그와 함께 수집. */
    public List<CampaignCard> fetchLiveAllWithLog(CampaignFetchLogService log) {
        List<CampaignCard> cards = new ArrayList<>();
        collectPlatform(log, cards, "강남맛집", gangnamMatzipFetcher::fetchCampaigns);
        collectPlatform(log, cards, "가보자", gabojaFetcher::fetchCampaigns);

        log.info("디너의여왕 목록 수집 중…");
        try {
            List<CampaignCard> dq = dinnerqueenFetcher.fetchCampaigns();
            log.info("디너의여왕 목록 " + dq.size() + "건");
            dinnerqueenFetcher.enrichBenefits(dq, log::info, loadCachedBenefits("디너의여왕"));
            log.info("디너의여왕 혜택 " + countWithBenefit(dq) + "/" + dq.size() + "건");
            cards.addAll(dq);
            publishPreview(cards);
        } catch (RuntimeException e) {
            log.warn("디너의여왕 수집 실패: " + e.getMessage());
        }

        log.info("음식 캠페인 필터·중복 제거 중…");
        List<CampaignCard> merged = prepareDisplayList(cards);
        log.info("병합 완료: 총 " + merged.size() + "건");
        return merged;
    }

    private void publishPreview(List<CampaignCard> raw) {
        if (refreshService.isRunning()) {
            refreshService.updatePreview(prepareDisplayList(raw));
        }
    }

    public void saveLiveCache(List<CampaignCard> cards) {
        saveCache(cards, CACHE_ALL);
    }

    private void collectPlatform(
            CampaignFetchLogService log,
            List<CampaignCard> cards,
            String name,
            java.util.function.Supplier<List<CampaignCard>> fetcher) {
        log.info(name + " 수집 중…");
        try {
            List<CampaignCard> batch = fetcher.get();
            cards.addAll(batch);
            log.info(name + " " + batch.size() + "건 (혜택 " + countWithBenefit(batch) + "건)");
            publishPreview(cards);
        } catch (RuntimeException e) {
            log.warn(name + " 수집 실패: " + e.getMessage());
        }
    }

    private int countWithBenefit(List<CampaignCard> cards) {
        int count = 0;
        for (CampaignCard card : cards) {
            if (card.getBenefit() != null && !card.getBenefit().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private Map<String, String> loadCachedBenefits(String platform) {
        List<CampaignCard> cached = loadCache(CACHE_ALL);
        if (cached == null) {
            return Map.of();
        }
        Map<String, String> benefits = new HashMap<>();
        for (CampaignCard card : cached) {
            if (!platform.equals(card.getPlatform())) {
                continue;
            }
            String benefit = card.getBenefit();
            if (benefit != null && !benefit.isBlank()) {
                benefits.put(card.getCampaignId(), benefit);
            }
        }
        return benefits;
    }

    public List<String> listRegions(List<CampaignCard> cards) {
        String priority = properties.getDefaultCampaignRegion();
        Set<String> seen = new LinkedHashSet<>();
        seen.add("전체");
        if (priority != null && !priority.isBlank()) {
            seen.add(priority.trim());
        }
        for (CampaignCard card : cards) {
            String region = card.getRegion();
            if (region != null && !region.isBlank() && ParseUtils.isKnownRegion(region)) {
                seen.add(region.trim());
            }
        }
        List<String> regions = new ArrayList<>(seen);
        regions.sort((a, b) -> {
            if ("전체".equals(a)) {
                return -1;
            }
            if ("전체".equals(b)) {
                return 1;
            }
            if (priority != null && priority.equals(a)) {
                return -1;
            }
            if (priority != null && priority.equals(b)) {
                return 1;
            }
            return a.compareTo(b);
        });
        return regions;
    }

    public List<CampaignCard> sortCampaigns(
            List<CampaignCard> cards, String sortBy, String platform) {
        List<CampaignCard> result = new ArrayList<>(cards);
        if (!"전체".equals(platform)) {
            result.removeIf(c -> !platform.equals(c.getPlatform()));
        }
        Comparator<CampaignCard> regionFirst = regionPriorityComparator();
        if ("competition".equals(sortBy)) {
            result.sort(regionFirst.thenComparingDouble(CampaignCard::getCompetition));
        } else if ("recruit".equals(sortBy)) {
            result.sort(regionFirst.thenComparingInt(CampaignCard::getRecruit).reversed());
        } else {
            result.sort(regionFirst.thenComparingInt(c -> deadlineSortKey(c.getDeadline())));
        }
        return result;
    }

    public CampaignPageResult paginate(
            List<CampaignCard> cards, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int from = (safePage - 1) * safeSize;
        long total = cards.size();
        if (from >= total) {
            return new CampaignPageResult(safePage, safeSize, total, List.of());
        }
        int to = Math.min(from + safeSize, cards.size());
        return new CampaignPageResult(
                safePage, safeSize, total, new ArrayList<>(cards.subList(from, to)));
    }

    public String cacheAgeLabel() {
        return cacheAgeLabel(CACHE_ALL);
    }

    public String cacheAgeLabel(String region) {
        Path cacheFile = Path.of(properties.getCampaignCacheFile());
        if (!Files.exists(cacheFile)) {
            return "캐시 없음";
        }
        try {
            Map<String, Object> cached = objectMapper.readValue(cacheFile.toFile(), new TypeReference<>() {});
            if (!region.equals(cached.get("region"))) {
                return "캐시 없음";
            }
            String fetchedAt = (String) cached.get("fetched_at");
            if (fetchedAt == null || fetchedAt.isBlank()) {
                return "캐시 있음";
            }
            Instant ts = Instant.parse(fetchedAt);
            long minutes = Duration.between(ts, Instant.now()).toMinutes();
            return minutes + "분 전 갱신";
        } catch (IOException e) {
            return "캐시 있음";
        }
    }

    private List<CampaignCard> loadCache(String region) {
        Path cacheFile = Path.of(properties.getCampaignCacheFile());
        if (!Files.exists(cacheFile)) {
            return null;
        }
        try {
            Map<String, Object> cached = objectMapper.readValue(cacheFile.toFile(), new TypeReference<>() {});
            if (!region.equals(cached.get("region"))) {
                return null;
            }
            String fetchedAt = (String) cached.get("fetched_at");
            if (fetchedAt == null) {
                return null;
            }
            Instant ts = Instant.parse(fetchedAt);
            long ageMinutes = Duration.between(ts, Instant.now()).toMinutes();
            if (ageMinutes >= properties.getCampaignCacheTtlMinutes()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) cached.get("items");
            return objectMapper.convertValue(items, new TypeReference<List<CampaignCard>>() {});
        } catch (IOException e) {
            return null;
        }
    }

    private void saveCache(List<CampaignCard> cards, String region) {
        try {
            Path cacheFile = Path.of(properties.getCampaignCacheFile());
            Files.createDirectories(cacheFile.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fetched_at", Instant.now().toString());
            payload.put("region", region);
            payload.put("items", cards);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), payload);
        } catch (IOException ignored) {
            // cache write failure is non-fatal
        }
    }

    private List<CampaignCard> fetchLiveAll() {
        List<CampaignCard> cards = new ArrayList<>();
        try {
            cards.addAll(gangnamMatzipFetcher.fetchCampaigns());
        } catch (RuntimeException ignored) {
            // skip platform
        }
        try {
            cards.addAll(gabojaFetcher.fetchCampaigns());
        } catch (RuntimeException ignored) {
            // skip platform
        }
        try {
            cards.addAll(dinnerqueenFetcher.fetchCampaigns());
        } catch (RuntimeException ignored) {
            // skip platform
        }
        List<CampaignCard> filtered = cards.stream()
                .filter(c -> ParseUtils.isFoodCampaign(c.getTitle(), c.getCategory(), c.getBenefit()))
                .toList();
        return prioritizeRegion(dedupe(filtered));
    }

    public boolean matchesRegion(CampaignCard card, String region) {
        if (region == null || region.isBlank() || "전체".equals(region)) {
            return true;
        }
        if (region.equals(card.getRegion())) {
            return true;
        }
        return ParseUtils.matchesRegion(card.getTitle(), region);
    }

    private List<CampaignCard> dedupe(List<CampaignCard> cards) {
        Set<String> seen = new HashSet<>();
        List<CampaignCard> unique = new ArrayList<>();
        for (CampaignCard card : cards) {
            String key = card.getPlatform() + ":" + card.getCampaignId();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            unique.add(card);
        }
        unique.sort(Comparator.comparing(CampaignCard::getPlatform).thenComparing(CampaignCard::getStoreName));
        return unique;
    }

    private List<CampaignCard> prioritizeRegion(List<CampaignCard> cards) {
        List<CampaignCard> result = new ArrayList<>(cards);
        result.sort(regionPriorityComparator()
                .thenComparing(CampaignCard::getPlatform)
                .thenComparing(CampaignCard::getStoreName));
        return result;
    }

    private Comparator<CampaignCard> regionPriorityComparator() {
        String priority = properties.getDefaultCampaignRegion();
        return Comparator.comparingInt(card -> matchesRegion(card, priority) ? 0 : 1);
    }

    private int deadlineSortKey(String deadline) {
        if (deadline == null || deadline.isBlank() || deadline.contains("상시")) {
            return 9999;
        }
        if (deadline.contains("오늘") || deadline.contains("D-1")) {
            return 1;
        }
        Matcher dayMatch = Pattern.compile("(\\d+)").matcher(deadline);
        if (dayMatch.find()) {
            return Integer.parseInt(dayMatch.group(1));
        }
        Matcher dMatch = Pattern.compile("D-(\\d+)").matcher(deadline);
        if (dMatch.find()) {
            return Integer.parseInt(dMatch.group(1));
        }
        return 5000;
    }
}
