package com.sanaiddalgi.hub.blog.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 섹션 이모지(🏠·📋 등) → 기본 카테고리 제목. */
public final class SectionTitles {

    private static final Pattern SECTION_EMOJI_ONLY =
            Pattern.compile("^(🏠|📋|✨|💡|📍|🔗|🍓)$");

    private static final Map<String, String> DEFAULT_TITLES = Map.of(
            "🏠", "🏠 장소 분위기",
            "📋", "📋 메뉴·가격",
            "✨", "✨ 이용·상품 후기",
            "💡", "💡 총평",
            "📍", "📍 장소 정보");

    private static final Pattern SECTION_HEADER =
            Pattern.compile("^(🏠|📋|✨|💡|📍)\\s*(.*)$", Pattern.DOTALL);

    private SectionTitles() {}

    /** 섹션 제목을 이모지 + 카테고리명으로 분리 (네이버 에디터는 한꺼번에 입력 시 이모지만 남음). */
    public record SectionHeader(String emoji, String label) {
        public boolean hasEmoji() {
            return emoji != null && !emoji.isEmpty();
        }
    }

    public static SectionHeader parseHeader(String text) {
        if (text == null || text.isBlank()) {
            return new SectionHeader("", "");
        }
        String resolved = resolve(text).strip();
        Matcher matcher = SECTION_HEADER.matcher(resolved);
        if (matcher.matches()) {
            return new SectionHeader(matcher.group(1), matcher.group(2).strip());
        }
        return new SectionHeader("", resolved);
    }

    public static boolean hasLeadingSectionEmoji(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return SECTION_HEADER.matcher(resolve(text).strip()).matches();
    }

    public static boolean isEmojiOnly(String text) {
        if (text == null) {
            return false;
        }
        return SECTION_EMOJI_ONLY.matcher(text.strip()).matches();
    }

    public static boolean isSectionEmojiOnly(String text) {
        if (!isEmojiOnly(text)) {
            return false;
        }
        return DEFAULT_TITLES.containsKey(text.strip());
    }

    /** 이모지만 있으면 기본 카테고리명을 붙임. 이미 제목이 있으면 그대로 반환. */
    public static String resolve(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String trimmed = text.strip();
        if (isEmojiOnly(trimmed)) {
            return DEFAULT_TITLES.getOrDefault(trimmed, trimmed);
        }
        return trimmed;
    }

    public static String expandEmojiOnlyLines(String draft) {
        if (draft == null || draft.isBlank()) {
            return draft == null ? "" : draft;
        }
        String[] lines = draft.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String resolved = resolve(line);
            out.append(resolved);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
