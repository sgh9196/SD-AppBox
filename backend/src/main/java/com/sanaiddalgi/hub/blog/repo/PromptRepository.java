package com.sanaiddalgi.hub.blog.repo;

import com.sanaiddalgi.hub.config.StudioProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;

/**
 * prompt.txt 읽기·변수 치환·포스팅 유형(협찬/내돈내산) 블록 조립.
 * {rating_display}, {photo_markers} 등 Gemini에 전달할 최종 프롬프트 생성.
 */
@Repository
public class PromptRepository {

    private static final Map<String, List<String>> EMOJI_SUGGESTION_POOL = Map.of(
            "food_drink", List.of("🍖", "🥩", "🍚", "🍜", "☕", "🧋", "🍰", "🍕", "🍣", "🥗"),
            "health", List.of("💊", "🩺", "🌿", "💆", "🧴"),
            "taste", List.of("🤤", "😋", "🫠", "😌", "😊", "😆", "😅"),
            "mood", List.of("✨", "💫", "🌿", "🪴", "💭"),
            "reaction", List.of("👍", "👏", "🙌", "🫶", "👌", "🤗")
    );

    private final StudioProperties properties;

    public PromptRepository(StudioProperties properties) {
        this.properties = properties;
    }

    public String buildPromptText(
            String restaurantName,
            String infoText,
            String restaurantLink,
            String postType,
            String bloggerName,
            int rating,
            String campaignGuideline,
            Map<String, List<String>> photoData) {
        int clampedRating = clampRating(rating);
        String[] parts = getPostTypeParts(postType);
        String template = loadPromptTemplate();
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("restaurant_name", restaurantName);
        vars.put("blogger_name", bloggerName);
        vars.put("info_text", infoText);
        vars.put("restaurant_link", restaurantLink);
        vars.put("post_type", postType);
        vars.put("post_type_guide", parts[0]);
        vars.put("disclosure_block", parts[1].isEmpty() ? "" : parts[1] + "\n\n");
        vars.put("campaign_guideline", formatCampaignGuideline(campaignGuideline));
        vars.put("emoji_suggestions", pickEmojiSuggestions());
        vars.put("rating_display", formatRatingDisplay(clampedRating));
        vars.put("rating_guide", ratingGuide(clampedRating));
        vars.putAll(photoMarkerVars(photoData));
        return fillPrompt(template, vars);
    }


    public String loadTestDraft(
            String restaurantName,
            String infoText,
            String restaurantLink,
            String postType,
            String bloggerName,
            int rating,
            String campaignGuideline,
            Map<String, List<String>> photoData) {
        Path mockFile = Path.of(properties.getMockResponseFile());
        if (Files.exists(mockFile)) {
            try {
                return Files.readString(mockFile).trim();
            } catch (IOException e) {
                // fall through
            }
        }
        String promptText = buildPromptText(
                restaurantName, infoText, restaurantLink, postType, bloggerName, rating, campaignGuideline, photoData);
        int idx = promptText.indexOf("[템플릿]");
        if (idx == -1) {
            return promptText.trim();
        }
        return promptText.substring(idx + "[템플릿]".length()).trim();
    }

    private String loadPrompt() {
        try {
            return Files.readString(Path.of(properties.getPromptFile()));
        } catch (IOException e) {
            throw new RuntimeException("prompt.txt 읽기 실패: " + properties.getPromptFile(), e);
        }
    }

    /** Gemini에 보낼 본문 — prompt-base(공통 말투) + prompt.txt(리뷰) 조립 */
    private String loadPromptTemplate() {
        String raw = loadPromptBase() + "\n\n" + loadReviewPrompt();
        String splitMarker = "--- 아래는 프로그램 설정용";
        if (raw.contains(splitMarker)) {
            raw = raw.split(splitMarker, 2)[0].trim();
        }
        for (String header : List.of(
                "내돈내산 - 규칙", "내돈내산 - 공정위 표시",
                "협찬 - 규칙", "협찬 - 공정위 표시")) {
            raw = removeBlock(raw, header);
        }
        return raw;
    }

    private String loadPromptBase() {
        Path baseFile = Path.of(properties.getPromptBaseFile());
        if (baseFile.toString().isBlank()) {
            return "";
        }
        try {
            return Files.readString(baseFile).trim();
        } catch (IOException e) {
            throw new RuntimeException("prompt-base.txt 읽기 실패: " + baseFile, e);
        }
    }

    private String loadReviewPrompt() {
        try {
            return Files.readString(Path.of(properties.getPromptFile())).trim();
        } catch (IOException e) {
            throw new RuntimeException("prompt.txt 읽기 실패: " + properties.getPromptFile(), e);
        }
    }


    private String[] getPostTypeParts(String postType) {
        String raw = loadPrompt();
        String guide = extractBlock(raw, postType + " - 규칙");
        String disclosure = extractBlock(raw, postType + " - 공정위 표시");
        if (!disclosure.isEmpty()) {
            disclosure = disclosure + "\n\n";
        }
        return new String[] {guide, disclosure};
    }

    private String extractBlock(String text, String header) {
        String marker = "[" + header + "]";
        int start = text.indexOf(marker);
        if (start == -1) {
            return "";
        }
        start += marker.length();
        if (start < text.length() && text.charAt(start) == '\n') {
            start++;
        }
        Matcher match = Pattern.compile("\n\\[").matcher(text.substring(start));
        int end = match.find() ? start + match.start() : text.length();
        return text.substring(start, end).trim();
    }

    private String removeBlock(String text, String header) {
        String marker = "[" + header + "]";
        int start = text.indexOf(marker);
        if (start == -1) {
            return text;
        }
        Matcher match = Pattern.compile("\n\\[").matcher(text.substring(start + marker.length()));
        int end = match.find() ? start + marker.length() + match.start() : text.length();
        return (text.substring(0, start) + text.substring(end)).trim();
    }

    private String pickEmojiSuggestions() {
        List<String> picked = new ArrayList<>();
        for (List<String> emojis : EMOJI_SUGGESTION_POOL.values()) {
            picked.addAll(emojis.subList(0, Math.min(2, emojis.size())));
        }
        return String.join(" ", picked.subList(0, Math.min(12, picked.size())));
    }

    private Map<String, String> photoMarkerVars(Map<String, List<String>> photoData) {
        Map<String, String> vars = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        Map<String, String> labels = Map.of(
                "external", "외관", "interior", "내부", "menu", "메뉴", "product", "상품");
        for (String cat : StudioProperties.PHOTO_CATEGORIES) {
            List<String> paths = photoData.getOrDefault(cat, List.of());
            StringBuilder markerBuilder = new StringBuilder();
            for (int i = 0; i < paths.size(); i++) {
                if (i > 0) {
                    markerBuilder.append(' ');
                }
                markerBuilder.append('[').append(cat).append('_').append(i + 1).append(']');
            }
            String markers = markerBuilder.toString();
            vars.put(cat + "_markers", markers);
            String label = labels.get(cat);
            if (paths.isEmpty()) {
                lines.add("- " + label + ": (없음)");
            } else {
                lines.add("- " + label + " (" + paths.size() + "장): " + markers);
            }
        }
        vars.put("photo_markers", String.join("\n", lines));
        vars.put("photo_sections_guide", photoSectionsGuide(photoData));
        return vars;
    }


    private String photoSectionsGuide(Map<String, List<String>> photoData) {
        List<String> lines = new ArrayList<>();
        if (!photoData.getOrDefault("external", List.of()).isEmpty()
                || !photoData.getOrDefault("interior", List.of()).isEmpty()) {
            lines.add("- 🏠 장소 분위기: external·interior 마커 사용");
        } else {
            lines.add("- 🏠 장소 분위기: 해당 사진 없음 → 가능한 범위에서만 짧게");
        }
        if (!photoData.getOrDefault("menu", List.of()).isEmpty()) {
            lines.add("- 📋 메뉴·가격: menu 마커 사용");
        } else {
            lines.add("- 📋 메뉴·가격: 사진 없음 → **이 섹션 생략** (약국 등)");
        }
        if (!photoData.getOrDefault("product", List.of()).isEmpty()) {
            lines.add("- ✨ 이용·상품 후기: product 마커 사용 "
                    + "(사진이 음식·음료·약품 등 무엇이든 그에 맞게 묘사)");
        } else {
            lines.add("- ✨ 이용·상품 후기: 사진 없음 → **이 섹션 생략**");
        }
        return String.join("\n", lines);
    }

    private String fillPrompt(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String formatCampaignGuideline(String campaignGuideline) {
        if (campaignGuideline == null || campaignGuideline.isBlank()) {
            return "(없음 — 이 섹션 무시)";
        }
        return campaignGuideline.trim();
    }

    private int clampRating(int rating) {
        if (rating < 1) {
            return 1;
        }
        if (rating > 5) {
            return 5;
        }
        return rating;
    }

    private String formatRatingDisplay(int rating) {
        return "★".repeat(rating) + "☆".repeat(5 - rating) + " (" + rating + "/5)";
    }

    private String ratingGuide(int rating) {
        return switch (rating) {
            case 5 -> """
                    - 5점: 매우 만족. 칭찬·재방문 의사를 분명히. 아쉬운 점은 없거나 아주 가볍게 1줄.
                    - 총평은 '또 가고 싶다', '강추' 수준. 부정 표현·실망 톤 금지.""";
            case 4 -> """
                    - 4점: 전반적으로 좋음. 장점을 구체적으로, 아쉬운 점 1가지는 부드럽게.
                    - 총평은 '다시 올 것 같다' 수준. 과장된 찬사보다 솔직한 만족.""";
            case 3 -> """
                    - 3점: 보통. 장단점을 균형 있게. 좋았던 점과 아쉬웠던 점을 비슷한 비중으로.
                    - 총평은 '나쁘지 않다', '상황에 따라 재방문' 정도. 극단적 칭찬·비난 금지.""";
            case 2 -> """
                    - 2점: 기대보다 아쉬움. 부족한 점은 구체적으로, **괜찮았던 점(분위기·서비스·한 메뉴 등) 1~2가지**는 꼭 언급.
                    - 총평은 '아쉽다', '상황에 따라' 정도. 욕설·연쇄 비난 금지.""";
            default -> """
                    - 1점: 가장 아쉬운 방문이지만 **완전 부정 톤은 금지**. 분위기·동행·직원·한 가지 나은 점 등 긍정 요소 1~2개는 반드시.
                    - 음식·가격·서비스 아쉬움은 사진·경험 근거로 부드럽게. '최악'·'돈 아까움' 반복·인신공격 금지.
                    - 총평은 '다시는 안 갈 것 같다' 수준의 솔직함. 그래도 블로그 말투·예의 유지.""";
        };
    }
}
