package com.sanaiddalgi.hub.blog.store.web;

import com.sanaiddalgi.hub.blog.store.service.StoreService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 매장 검색·상세·영업시간·네이버 지도 링크 API (공공 API + 네이버 스크래핑). */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/search")
    public List<Map<String, String>> search(
            @RequestParam String storeName,
            @RequestParam(defaultValue = "") String region) {
        return storeService.searchCandidates(storeName, region);
    }

    @GetMapping("/info")
    public Map<String, String> info(
            @RequestParam String storeName,
            @RequestParam(defaultValue = "") String region,
            @RequestParam(required = false) String bplcNm,
            @RequestParam(required = false) String roadNmAddr,
            @RequestParam(required = false) String lotnoAddr) {
        Map<String, String> selected = null;
        if (bplcNm != null && !bplcNm.isBlank()) {
            selected = new LinkedHashMap<>();
            selected.put("BPLC_NM", bplcNm);
            if (roadNmAddr != null) {
                selected.put("ROAD_NM_ADDR", roadNmAddr);
            }
            if (lotnoAddr != null) {
                selected.put("LOTNO_ADDR", lotnoAddr);
            }
        }
        String infoText = storeService.buildStoreInfo(storeName, region, selected);
        return Map.of("infoText", infoText);
    }

    @GetMapping("/hours")
    public Map<String, String> hours(
            @RequestParam String storeName,
            @RequestParam(defaultValue = "") String region,
            @RequestParam(defaultValue = "") String address) {
        return storeService.fetchNaverHours(storeName, region, address);
    }

    @GetMapping("/map-link")
    public Map<String, String> mapLink(
            @RequestParam String storeName,
            @RequestParam(defaultValue = "") String region) {
        return Map.of("url", storeService.buildNaverMapLink(storeName, region));
    }
}
