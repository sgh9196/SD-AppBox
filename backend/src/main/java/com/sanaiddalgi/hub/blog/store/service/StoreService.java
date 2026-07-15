package com.sanaiddalgi.hub.blog.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.store.repo.StoreRepository;
import com.sanaiddalgi.hub.blog.store.repo.StoreRepository.NaverHoursCandidate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 공공 API 매장 검색 + 네이버 영업시간·주차·예약 정보를
 * prompt/Word 하단에 넣을 텍스트 형식으로 조합.
 */
@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final StudioProperties properties;

    public StoreService(StoreRepository storeRepository, StudioProperties properties) {
        this.storeRepository = storeRepository;
        this.properties = properties;
    }

    public List<Map<String, String>> searchCandidates(String storeName, String region) {
        String regionFilter = regionFilterValue(region);
        List<Map<String, String>> items = storeRepository.fetchPublicItems(storeName, regionFilter);
        if (!regionFilter.isBlank()) {
            items = items.stream()
                    .filter(item -> getAddress(item).contains(regionFilter))
                    .toList();
        }
        return items;
    }

    /** 전체·빈 값은 지역 필터 없음 — 매장명만 검색 */
    static String regionFilterValue(String region) {
        if (region == null || region.isBlank() || "전체".equals(region.trim())) {
            return "";
        }
        return region.trim();
    }

    public String buildStoreInfo(String storeName, String region, Map<String, String> selectedItem) {
        if (properties.isUseTestMode()) {
            return "테스트 정보입니다.";
        }
        if (properties.isSkipPublicApi() || selectedItem == null || selectedItem.isEmpty()) {
            Map<String, String> naverExtra = fetchNaverHours(storeName, region, "");
            return formatStoreInfo(storeName, "정보 없음", naverExtra);
        }
        String address = getAddress(selectedItem);
        Map<String, String> naverExtra = fetchNaverHours(storeName, region, address);
        return formatInfo(selectedItem, naverExtra);
    }

    public Map<String, String> fetchNaverHours(String storeName, String region, String address) {
        List<String> queries = new ArrayList<>();
        queries.add(storeName);
        String regionFilter = regionFilterValue(region);
        if (!regionFilter.isBlank()) {
            queries.add(storeName + " " + regionFilter);
        }
        queries.add(storeName + " 영업시간");

        NaverHoursCandidate best = null;
        for (String query : queries.stream().distinct().toList()) {
            try {
                String html = storeRepository.fetchNaverSearchHtml(query);
                for (NaverHoursCandidate candidate
                        : storeRepository.parseNaverHoursCandidates(html, storeName, address)) {
                    if (best == null || candidate.getScore() > best.getScore()) {
                        best = candidate;
                    }
                }
            } catch (RuntimeException ignored) {
                // try next query
            }
        }

        Map<String, String> empty = new LinkedHashMap<>();
        empty.put("영업시간", "정보 없음");
        empty.put("주차", "정보 없음");
        empty.put("예약", "정보 없음");
        if (best == null) {
            return empty;
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("영업시간", formatBusinessHours(best.getHoursList()));
        result.put("주차", inferParking(best.getParkingAvailable(), best.getOptions()));
        result.put("예약", inferReservation(best.getHasBooking(), best.getHasBookingUrl(), best.getOptions()));
        return result;
    }

    public String buildNaverMapLink(String storeName, String region) {
        String regionFilter = regionFilterValue(region);
        String query = regionFilter.isBlank() ? storeName : storeName + " " + regionFilter;
        return "https://map.naver.com/v5/search/" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
    }

    public static String getAddress(Map<String, String> item) {
        String road = item.getOrDefault("ROAD_NM_ADDR", "");
        if (!road.isBlank()) {
            return road;
        }
        return item.getOrDefault("LOTNO_ADDR", "정보없음");
    }

    private String formatInfo(Map<String, String> info, Map<String, String> naverExtra) {
        return "상호: " + info.getOrDefault("BPLC_NM", "정보없음") + "\n"
                + "주소: " + getAddress(info) + "\n"
                + "영업시간: " + naverExtra.getOrDefault("영업시간", "정보 없음") + "\n"
                + "주차: " + naverExtra.getOrDefault("주차", "정보 없음") + "\n"
                + "예약: " + naverExtra.getOrDefault("예약", "정보 없음");
    }

    private String formatStoreInfo(String name, String address, Map<String, String> naverExtra) {
        return "상호: " + name + "\n"
                + "주소: " + (address == null || address.isBlank() ? "정보 없음" : address) + "\n"
                + "영업시간: " + naverExtra.getOrDefault("영업시간", "정보 없음") + "\n"
                + "주차: " + naverExtra.getOrDefault("주차", "정보 없음") + "\n"
                + "예약: " + naverExtra.getOrDefault("예약", "정보 없음");
    }

    private String formatBusinessHours(JsonNode hoursList) {
        if (hoursList == null || !hoursList.isArray() || hoursList.isEmpty()) {
            return "정보 없음";
        }
        Map<String, String> dayMap = new LinkedHashMap<>();
        for (JsonNode item : hoursList) {
            String day = item.path("day").asText("");
            JsonNode bh = item.path("businessHours");
            String start = bh.path("start").asText("");
            String end = bh.path("end").asText("");
            if (day.isBlank() || start.isBlank() || end.isBlank()) {
                continue;
            }
            String endLabel = end.compareTo(start) <= 0 ? "익일 " + end : end;
            dayMap.put(day, start + "-" + endLabel);
        }
        List<String> schedules = new ArrayList<>();
        for (String day : StudioProperties.WEEKDAYS) {
            if (dayMap.containsKey(day)) {
                schedules.add(day + " " + dayMap.get(day));
            }
        }
        if (schedules.isEmpty()) {
            return "정보 없음";
        }
        List<String> times = schedules.stream().map(s -> s.substring(s.indexOf(' ') + 1)).distinct().toList();
        if (times.size() == 1 && schedules.size() == 7) {
            return "매일 " + times.get(0);
        }
        if (times.size() == 1) {
            return String.join(", ", schedules.stream().map(s -> s.substring(0, s.indexOf(' '))).toList())
                    + " " + times.get(0);
        }
        return String.join("\n", schedules);
    }

    private String inferParking(String parkingAvailable, String options) {
        String label = parkingAvailable == null ? "" : parkingAvailable.trim();
        if (!label.isBlank()) {
            if (label.contains("불가") || label.contains("없음")) {
                return "불가능";
            }
            if (label.contains("가능")) {
                return "가능";
            }
        }
        for (String token : splitOptions(options)) {
            if (List.of("주차", "주차가능", "발렛", "발렛파킹").contains(token)) {
                return "가능";
            }
            if (token.contains("주차불가") || List.of("주차없음", "주차 불가").contains(token)) {
                return "불가능";
            }
        }
        return "정보 없음";
    }

    private String inferReservation(Boolean hasBooking, Boolean hasBookingUrl, String options) {
        if (Boolean.TRUE.equals(hasBooking) || Boolean.TRUE.equals(hasBookingUrl)) {
            return "가능";
        }
        if (Boolean.FALSE.equals(hasBooking) && Boolean.FALSE.equals(hasBookingUrl)) {
            return "불가능";
        }
        for (String token : splitOptions(options)) {
            if ("예약".equals(token) || token.contains("예약")) {
                return "가능";
            }
        }
        return "정보 없음";
    }

    private List<String> splitOptions(String options) {
        if (options == null || options.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : options.split(",")) {
            if (!token.trim().isBlank()) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }
}
