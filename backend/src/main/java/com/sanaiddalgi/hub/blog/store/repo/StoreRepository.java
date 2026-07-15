package com.sanaiddalgi.hub.blog.store.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.config.StudioProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

/** 식품안전나라(공공데이터포털) REST + 네이버 검색 HTML 스크래핑. */
@Repository
public class StoreRepository {

    private static final String PUBLIC_API_BASE =
            "https://apis.data.go.kr/1741000/general_restaurants/info";

    private final RestClient restClient;
    private final StudioProperties properties;
    private final ObjectMapper objectMapper;

    public StoreRepository(RestClient restClient, StudioProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, String>> fetchPublicItems(String storeName, String region) {
        String url = buildPublicApiUrl(storeName, region);
        String body = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            String resultCode = root.path("response").path("header").path("resultCode").asText("");
            if (!"0".equals(resultCode) && !"00".equals(resultCode)) {
                return List.of();
            }
            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (items.isMissingNode() || items.isNull()) {
                return List.of();
            }
            List<Map<String, String>> result = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    result.add(toStringMap(item));
                }
            } else {
                result.add(toStringMap(items));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("공공 API 응답 파싱 실패", e);
        }
    }

    /** UriComponentsBuilder가 cond[BPLC_NM::LIKE] 형식을 거부하므로 URL 수동 조립 */
    private String buildPublicApiUrl(String storeName, String region) {
        StringBuilder sb = new StringBuilder(PUBLIC_API_BASE);
        sb.append("?serviceKey=").append(encode(properties.getPublicApiKey()));
        sb.append("&pageNo=1&numOfRows=10&returnType=json");
        sb.append("&cond%5BBPLC_NM%3A%3ALIKE%5D=").append(encode(storeName.trim()));
        if (region != null && !region.isBlank()) {
            sb.append("&cond%5BROAD_NM_ADDR%3A%3ALIKE%5D=").append(encode(region));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> toStringMap(JsonNode node) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), entry.getValue().asText(""));
        }
        return map;
    }

    public String fetchNaverSearchHtml(String query) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("search.naver.com")
                        .path("/search.naver")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .body(String.class);
    }

    public List<NaverHoursCandidate> parseNaverHoursCandidates(String html, String storeName, String address) {
        List<NaverHoursCandidate> candidates = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"businessHours\"\\s*:\\s*\\[").matcher(html);
        while (matcher.find()) {
            String arrText = extractJsonArray(html, matcher.start());
            if (arrText == null) {
                continue;
            }
            try {
                JsonNode hoursList = objectMapper.readTree(arrText);
                int start = Math.max(0, matcher.start() - 8000);
                String ctx = html.substring(start, matcher.start());
                NaverHoursCandidate candidate = new NaverHoursCandidate();
                candidate.setScore(addressMatchScore(
                        findJsonFieldBefore(ctx, "roadAddress"),
                        findJsonFieldBefore(ctx, "name"),
                        storeName,
                        address));
                candidate.setHoursList(hoursList);
                candidate.setPhone(findJsonFieldBefore(ctx, "phone"));
                candidate.setDayOff(findJsonFieldBefore(ctx, "dayOff"));
                candidate.setOptions(findJsonFieldBefore(ctx, "options"));
                candidate.setParkingAvailable(findJsonFieldBefore(ctx, "parking_available"));
                candidate.setHasBooking(findJsonBoolBefore(ctx, "hasBooking"));
                candidate.setHasBookingUrl(hasBookingUrlBefore(ctx));
                candidates.add(candidate);
            } catch (Exception ignored) {
                // skip malformed block
            }
        }
        return candidates;
    }

    private String extractJsonArray(String text, int bracketPos) {
        int start = text.indexOf('[', bracketPos);
        if (start == -1) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String findJsonFieldBefore(String ctx, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(ctx);
        String last = "";
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    private Boolean findJsonBoolBefore(String ctx, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false|null)").matcher(ctx);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        if (last == null) {
            return null;
        }
        if ("true".equals(last)) {
            return true;
        }
        if ("false".equals(last)) {
            return false;
        }
        return null;
    }

    private Boolean hasBookingUrlBefore(String ctx) {
        Matcher matcher = Pattern.compile("\"bookingUrl\"\\s*:\\s*(null|\\{)").matcher(ctx);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        if (last == null) {
            return null;
        }
        return !"null".equals(last);
    }

    private int addressMatchScore(String road, String placeName, String storeName, String address) {
        int score = 0;
        if (storeName != null && placeName != null
                && placeName.replace(" ", "").contains(storeName.replace(" ", ""))) {
            score += 3;
        }
        if (address == null || address.isBlank()) {
            return score;
        }
        for (String part : address.split("[\\s,()]+")) {
            if (part.length() >= 3 && (road.contains(part) || address.contains(part))) {
                score++;
            }
        }
        if (!road.isBlank() && (address.contains(road) || road.contains(address))) {
            score += 5;
        }
        return score;
    }

    public static class NaverHoursCandidate {
        private int score;
        private JsonNode hoursList;
        private String phone = "";
        private String dayOff = "";
        private String options = "";
        private String parkingAvailable = "";
        private Boolean hasBooking;
        private Boolean hasBookingUrl;

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public JsonNode getHoursList() {
            return hoursList;
        }

        public void setHoursList(JsonNode hoursList) {
            this.hoursList = hoursList;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getDayOff() {
            return dayOff;
        }

        public void setDayOff(String dayOff) {
            this.dayOff = dayOff;
        }

        public String getOptions() {
            return options;
        }

        public void setOptions(String options) {
            this.options = options;
        }

        public String getParkingAvailable() {
            return parkingAvailable;
        }

        public void setParkingAvailable(String parkingAvailable) {
            this.parkingAvailable = parkingAvailable;
        }

        public Boolean getHasBooking() {
            return hasBooking;
        }

        public void setHasBooking(Boolean hasBooking) {
            this.hasBooking = hasBooking;
        }

        public Boolean getHasBookingUrl() {
            return hasBookingUrl;
        }

        public void setHasBookingUrl(Boolean hasBookingUrl) {
            this.hasBookingUrl = hasBookingUrl;
        }
    }
}
