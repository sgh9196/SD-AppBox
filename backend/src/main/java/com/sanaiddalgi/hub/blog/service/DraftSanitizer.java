package com.sanaiddalgi.hub.blog.service;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Gemini 원고 후처리 — 줄바꿈·이모지·네이버 마크다운 등. */
@Service
public class DraftSanitizer {

    public static String getIntroLine(String bloggerName) {
        String name = (bloggerName == null || bloggerName.isBlank()) ? "글로벌" : bloggerName;
        return name + " 등장!! 오늘 리뷰 지금 시작합니다. Let's get it!";
    }

    public static Pattern getIntroPattern(String bloggerName) {
        String name = (bloggerName == null || bloggerName.isBlank()) ? "글로벌" : bloggerName;
        return Pattern.compile(
                Pattern.quote(name) + " 등장!!\\s*오늘 리뷰 지금 시작합니다\\.\\s*Let's get it!?~*",
                Pattern.CASE_INSENSITIVE);
    }

    private static final Set<String> SECTION_EMOJIS = Set.of("🏠", "📋", "✨", "💡", "📍", "🔗", "🍓", "✍️");

    /** Word·네이버 공통 — 생성 직후 원고 정리. */
    public String sanitizeGeneratedDraft(String draft, String bloggerName) {
        if (draft == null || draft.isBlank()) {
            return draft == null ? "" : draft;
        }
        String cleaned = normalizeDraftLayout(draft, bloggerName);
        cleaned = dedupeEmojis(cleaned);
        return sanitizeForNaverEditor(cleaned);
    }

    /** 인트로·섹션·장소 정보 줄바꿈 정규화. */
    public String normalizeDraftLayout(String draft, String bloggerName) {
        String cleaned = draft.replace("\r\n", "\n");
        String introLine = getIntroLine(bloggerName);
        Pattern introPattern = getIntroPattern(bloggerName);
        cleaned = introPattern.matcher(cleaned).replaceAll(introLine);

        int introIdx = cleaned.indexOf(introLine);
        if (introIdx >= 0) {
            String before = cleaned.substring(0, introIdx);
            String after = cleaned.substring(introIdx + introLine.length());
            if (!after.isEmpty() && !after.startsWith("\n")) {
                after = "\n\n" + after.stripLeading();
            } else if (after.startsWith("\n") && !after.startsWith("\n\n")) {
                after = "\n\n" + after.substring(1).stripLeading();
            }
            cleaned = before + introLine + after;
        }

        cleaned = cleaned.replaceAll("(?i)Let's get it(?![!\\s\\n])(\\S)", "Let's get it!\n\n$1");
        cleaned = cleaned.replaceAll("(?i)(Let's get it!?)([가-힣\\[])", "$1\n\n$2");
        cleaned = cleaned.replaceAll("([^\n])\n(🏠|📋|✨|💡|📍)", "$1\n\n$2");
        cleaned = SectionTitles.expandEmojiOnlyLines(cleaned);
        cleaned = collapseSectionEmojiLineBreaks(cleaned);
        cleaned = mergeStandaloneEmojiLines(cleaned);
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        return cleaned.strip();
    }

    /** 섹션 이모지와 카테고리명이 다른 줄에 있으면 한 줄로 합침 */
    private String collapseSectionEmojiLineBreaks(String text) {
        String cleaned = text.replaceAll("(?m)^(🏠|📋|✨|💡|📍)\\s*\\n(?=\\S)", "$1 ");
        cleaned = cleaned.replaceAll("(?m)^📍\\s*\\n(?=장소 정보)", "📍 ");
        cleaned = cleaned.replaceAll("(?m)^📍\\s*\\n(?=상호)", "📍 장소 정보\n");
        return cleaned;
    }

    /** 줄에 장식 이모지만 있으면 앞·뒤 문장과 한 줄로 합침 (섹션 이모지 🏠·📋 등 제외) */
    private String mergeStandaloneEmojiLines(String text) {
        String[] lines = text.split("\n", -1);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (isDecorativeEmojiOnlyLine(trimmed)) {
                int next = i + 1;
                while (next < lines.length && lines[next].isBlank()) {
                    next++;
                }
                if (next < lines.length && !lines[next].strip().startsWith("[")) {
                    out.add(trimmed + " " + lines[next].strip());
                    i = next;
                    continue;
                }
                if (!out.isEmpty()) {
                    int last = out.size() - 1;
                    out.set(last, out.get(last) + " " + trimmed);
                    continue;
                }
            }
            out.add(lines[i]);
        }
        return String.join("\n", out);
    }

    private boolean isDecorativeEmojiOnlyLine(String line) {
        return isEmojiOnlyLine(line) && !SectionTitles.isSectionEmojiOnly(line);
    }

    private boolean isEmojiOnlyLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); ) {
            int codePoint = trimmed.codePointAt(i);
            if (!Character.isWhitespace(codePoint) && !isEmojiCodePoint(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean isEmojiCodePoint(int codePoint) {
        if (codePoint >= 0x1F300 && codePoint <= 0x1FAFF) {
            return true;
        }
        if (codePoint >= 0x2600 && codePoint <= 0x27BF) {
            return true;
        }
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    /** 네이버 스마트에디터 입력 직전 — 마크다운 서식 문자 제거. */
    public String sanitizeForNaverEditor(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text;
        cleaned = cleaned.replaceAll("(?i)Let's get it~+", "Let's get it!");
        cleaned = unwrapStrikethroughMarkdown(cleaned);
        cleaned = cleaned.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1");
        cleaned = cleaned.replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", "$1");
        cleaned = cleaned.replaceAll("`([^`\\n]+)`", "$1");
        cleaned = cleaned.replaceAll("_{2,}", "");
        cleaned = cleaned.replaceAll("</?[sS](?:trike)?[^>]*>", "");
        cleaned = cleaned.replaceAll("</?del[^>]*>", "");
        return cleaned;
    }

    /**
     * ~~취소선~~ 마크다운 — 내용은 유지, 구분자만 제거.
     * 단독 줄에 있는 취소선도 본문 흐름에 합침(맥락 없는 줄바꿈 방지).
     */
    public String unwrapStrikethroughMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String cleaned = text.replace("\r\n", "\n");
        cleaned = cleaned.replaceAll("(?m)^\\s*~~([^~\\n]*)~~\\s*$", "$1");
        String prev;
        do {
            prev = cleaned;
            cleaned = cleaned.replaceAll("~~([^~\\n]+)~~", "$1");
        } while (!cleaned.equals(prev));
        cleaned = cleaned.replace("~~", "");
        cleaned = cleaned.replaceAll("[ \\t]*\\n[ \\t]*~~([^~\\n]*)~~[ \\t]*\\n[ \\t]*", " $1 ");
        return cleaned;
    }

    public boolean isPlaceInfoText(String text) {
        return text != null && text.contains("📍 장소 정보");
    }

    private String dedupeEmojis(String text) {
        Set<String> used = new HashSet<>();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (isEmojiCodePoint(codePoint)) {
                String emoji = new String(Character.toChars(codePoint));
                boolean sectionLead = isSectionLeadEmoji(text, i, emoji);
                if (sectionLead || !used.contains(emoji)) {
                    out.append(emoji);
                    used.add(emoji);
                }
            } else {
                out.appendCodePoint(codePoint);
            }
            i += charCount;
        }
        return out.toString();
    }

    private boolean isSectionLeadEmoji(String text, int emojiStart, String emoji) {
        if (!SECTION_EMOJIS.contains(emoji)) {
            return false;
        }
        int lineStart = text.lastIndexOf('\n', Math.max(0, emojiStart - 1)) + 1;
        String before = text.substring(lineStart, emojiStart).trim();
        return before.isEmpty();
    }
}
