package com.sanaiddalgi.hub.blog.campaign.repo;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 체험단 HTML 파싱 공통 유틸 — 지역·음식 키워드, 마감일·경쟁률 추출. */
public final class ParseUtils {

    private static final Pattern BRACKET_TITLE_RE = Pattern.compile("^\\[([^\\]]+)\\]\\s*(.+)$");
    private static final Pattern DAEJEON_RE = Pattern.compile("^\\[대전");
    private static final Pattern SKIP_CHANNEL_RE = Pattern.compile("\\[(릴스|클립|쇬츠|쇼츠|N클립|포토)\\]");
    private static final String[] FOOD_KEYWORDS = {
            "맛집", "카페", "식당", "식사", "디저트", "음료", "양식", "한식", "일식", "중식", "파스타", "브런치"
    };
    private static final Set<String> KNOWN_REGIONS = Set.of(
            "전국",
            "서울", "경기", "경기도", "인천", "부산", "대구", "광주", "대전", "울산", "세종", "세종시",
            "강원", "강원도", "강원특별자치도", "제주", "제주도", "제주특별자치도",
            "충북", "충남", "충청북도", "충청남도",
            "전북", "전남", "전라북도", "전라남도", "전북특별자치",
            "경북", "경남", "경상북도", "경상남도");
    private static final Map<String, String> REGION_ALIASES = Map.ofEntries(
            Map.entry("관악", "서울"), Map.entry("마포", "서울"), Map.entry("강남", "서울"),
            Map.entry("용산", "서울"), Map.entry("송파", "서울"), Map.entry("영등포", "서울"),
            Map.entry("합정", "서울"), Map.entry("이태원", "서울"), Map.entry("하남", "경기"),
            Map.entry("용인", "경기"), Map.entry("수원", "경기"), Map.entry("성남", "경기"),
            Map.entry("분당", "경기"), Map.entry("포천", "경기"), Map.entry("안양", "경기"),
            Map.entry("남양주", "경기"), Map.entry("김포", "경기"), Map.entry("고양", "경기"),
            Map.entry("김해", "경남"), Map.entry("창원", "경남"), Map.entry("청주", "충북"),
            Map.entry("괴산", "충북"), Map.entry("전주", "전북"), Map.entry("천안", "충남"),
            Map.entry("포항", "경북"), Map.entry("목포", "전남"));

    private ParseUtils() {
    }

    public static boolean isKnownRegion(String region) {
        if (region == null || region.isBlank()) {
            return false;
        }
        String value = region.trim();
        if (KNOWN_REGIONS.contains(value)) {
            return true;
        }
        return value.endsWith("광역시")
                || value.endsWith("특별시")
                || value.endsWith("특별자치시")
                || value.endsWith("특별자치도")
                || (value.endsWith("도") && value.length() <= 5);
    }

    public static boolean isDaejeonTitle(String title) {
        return DAEJEON_RE.matcher(title).find();
    }

    public static boolean matchesRegion(String title, String region) {
        if (title == null || title.isBlank() || region == null || region.isBlank()) {
            return false;
        }
        return title.startsWith("[" + region)
                || title.contains("[" + region + "/")
                || title.contains("[" + region + " ");
    }

    /** [지역 구] 매장명 또는 [지역/카테고리] 매장명 → region, district, storeName */
    public static String[] parseRegionStore(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        Matcher bracket = BRACKET_TITLE_RE.matcher(title.trim());
        if (!bracket.find()) {
            return null;
        }
        String inside = bracket.group(1).trim();
        String store = bracket.group(2).trim()
                .replaceAll("\\[[^\\]]+\\]", "")
                .replaceAll("\\s*모집.*$", "")
                .replaceAll("\\s*상시모집.*$", "")
                .trim();
        if (store.isEmpty()) {
            return null;
        }
        if (inside.contains("/")) {
            String[] slash = inside.split("/", 2);
            String region = slash[0].trim();
            if (!isKnownRegion(region)) {
                return null;
            }
            return new String[] {region, slash[1].trim(), store};
        }
        Matcher space = Pattern.compile("^([^\\s]+)\\s+(.+)$").matcher(inside);
        if (space.find()) {
            String region = space.group(1).trim();
            if (!isKnownRegion(region)) {
                return null;
            }
            return new String[] {region, space.group(2).trim(), store};
        }
        if (isKnownRegion(inside)) {
            return new String[] {inside, "", store};
        }
        return null;
    }

    public static boolean isFoodCampaign(String title, String category, String benefit) {
        String blob = title + " " + category + " " + benefit;
        for (String keyword : FOOD_KEYWORDS) {
            if (blob.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBlogChannel(String text) {
        if (SKIP_CHANNEL_RE.matcher(text).find()) {
            return false;
        }
        return text.contains("블로그") || text.toLowerCase().contains("blog");
    }

    public static int[] parseCounts(String text) {
        Matcher pair = Pattern.compile("신청\\s*([\\d,]+)\\s*/\\s*모집\\s*([\\d,]+)").matcher(text);
        if (pair.find()) {
            return new int[] {
                    Integer.parseInt(pair.group(1).replace(",", "")),
                    Integer.parseInt(pair.group(2).replace(",", ""))
            };
        }
        int applied = 0;
        int recruit = 0;
        Matcher appliedMatch = Pattern.compile("신청\\s*<b[^>]*>\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (appliedMatch.find()) {
            applied = Integer.parseInt(appliedMatch.group(1).replace(",", ""));
        } else {
            appliedMatch = Pattern.compile("신청\\s*([\\d,]+)").matcher(text);
            if (appliedMatch.find()) {
                applied = Integer.parseInt(appliedMatch.group(1).replace(",", ""));
            }
        }
        Matcher recruitMatch = Pattern.compile("모집\\s*<b[^>]*>\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (recruitMatch.find()) {
            recruit = Integer.parseInt(recruitMatch.group(1).replace(",", ""));
        } else {
            recruitMatch = Pattern.compile("모집\\s*([\\d,]+)").matcher(text);
            if (recruitMatch.find()) {
                recruit = Integer.parseInt(recruitMatch.group(1).replace(",", ""));
            }
        }
        return new int[] {applied, recruit};
    }

    public static String absUrl(String base, String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return URI.create(base.endsWith("/") ? base : base + "/").resolve(url).toString();
    }

    public static String[] parseDaejeonStore(String title) {
        Matcher match = Pattern.compile("^\\[대전\\s*([^/\\]]+)[/\\]]?\\s*(.+)$").matcher(title.trim());
        if (!match.find()) {
            match = Pattern.compile("^\\[대전/([^\\]]+)\\]\\s*(.+)$").matcher(title.trim());
        }
        if (!match.find()) {
            return new String[] {"", title};
        }
        String district = match.group(1).trim();
        String store = match.group(2).trim()
                .replaceAll("\\s*모집.*$", "")
                .replaceAll("\\s*상시모집.*$", "");
        return new String[] {district, store};
    }

    public static String resolveRegionToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String value = token.trim();
        if (isKnownRegion(value)) {
            return value;
        }
        return REGION_ALIASES.getOrDefault(value, "");
    }

    public static String inferCategory(String text) {
        if (text.contains("카페")) {
            return "카페";
        }
        if (text.contains("맛집")) {
            return "맛집";
        }
        return "식당";
    }
}
