package com.sanaiddalgi.hub.blog.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원고에서 📍 장소 정보 블록 경계 분리 (인트로 직후·하단 배치 모두 지원). */
public final class PlaceInfoLayout {

    private static final String PLACE_INFO_MARKER = "📍 장소 정보";
    private static final Pattern PLACE_LINE_RE =
            Pattern.compile("(?m)^(상호|주소|영업시간|주차|예약)\\s*:");
    private static final Pattern NEXT_SECTION_RE = Pattern.compile("\n\n[🏠📋✨💡]");

    private PlaceInfoLayout() {}

    public record Span(String before, String placeBlock, String after) {
        public boolean hasPlace() {
            return placeBlock != null && !placeBlock.isBlank();
        }
    }

    public static Span split(String text) {
        if (text == null || text.isBlank()) {
            return new Span(text == null ? "" : text, "", "");
        }
        int start = findPlaceInfoStart(text);
        if (start < 0) {
            return new Span(text, "", "");
        }
        String before = text.substring(0, start).strip();
        String tail = text.substring(start);
        int endInTail = findPlaceBlockEnd(tail);
        if (endInTail < 0) {
            return new Span(before, tail.strip(), "");
        }
        String placeBlock = tail.substring(0, endInTail).strip();
        String after = tail.substring(endInTail).strip();
        return new Span(before, placeBlock, after);
    }

    private static int findPlaceInfoStart(String text) {
        int marker = text.indexOf(PLACE_INFO_MARKER);
        if (marker >= 0) {
            return expandPlaceStart(text, marker);
        }
        Matcher lineMatcher = PLACE_LINE_RE.matcher(text);
        if (!lineMatcher.find()) {
            return -1;
        }
        return expandPlaceStart(text, lineMatcher.start());
    }

    private static int expandPlaceStart(String text, int start) {
        int pos = start;
        while (pos > 0) {
            int prevBreak = text.lastIndexOf("\n\n", pos - 1);
            int segmentStart = prevBreak < 0 ? 0 : prevBreak + 2;
            String segment = text.substring(segmentStart, pos).strip();
            if (segment.isEmpty()) {
                pos = prevBreak < 0 ? 0 : prevBreak;
                continue;
            }
            if (segment.equals("장소 정보")
                    || (SectionTitles.isSectionEmojiOnly(segment) && segment.strip().startsWith("📍"))) {
                pos = segmentStart;
                continue;
            }
            break;
        }
        return pos;
    }

    private static int findPlaceBlockEnd(String tail) {
        int mapLineEnd = findMapLinkLineEnd(tail);
        if (mapLineEnd >= 0) {
            return skipTrailingNewlines(tail, mapLineEnd);
        }
        Matcher section = NEXT_SECTION_RE.matcher(tail);
        if (section.find()) {
            return section.start();
        }
        return -1;
    }

    private static int findMapLinkLineEnd(String tail) {
        int offset = 0;
        for (String line : tail.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("🔗") || trimmed.contains("네이버 지도")) {
                return offset + line.length();
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    private static int skipTrailingNewlines(String text, int pos) {
        while (pos < text.length() && (text.charAt(pos) == '\n' || text.charAt(pos) == '\r')) {
            pos++;
        }
        return pos;
    }
}
