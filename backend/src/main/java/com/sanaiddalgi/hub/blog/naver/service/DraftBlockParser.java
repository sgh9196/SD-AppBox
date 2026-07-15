package com.sanaiddalgi.hub.blog.naver.service;

import com.sanaiddalgi.hub.blog.service.DraftSanitizer;
import com.sanaiddalgi.hub.blog.service.PlaceInfoLayout;
import com.sanaiddalgi.hub.blog.service.SectionTitles;
import com.sanaiddalgi.hub.blog.naver.model.DraftBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Gemini 원고 + 사진 마커 → 네이버 에디터용 블록 목록.
 * Word 변환({@link com.sanaiddalgi.hub.blog.service.ExportService})과 동일한 줄바꿈·합침 규칙.
 */
@Service
public class DraftBlockParser {

    private static final Pattern MARKER_RE =
            Pattern.compile("\\[(external|interior|menu|product|photo)_(\\d+)\\]");
    private static final String PLACE_INFO_MARKER = "📍 장소 정보";

    private static final Pattern PLACE_LINE_RE =
            Pattern.compile("(?m)^(상호|주소|영업시간|주차|예약)\\s*:");

    private final DraftSanitizer draftSanitizer;

    public DraftBlockParser(DraftSanitizer draftSanitizer) {
        this.draftSanitizer = draftSanitizer;
    }

    public List<DraftBlock> parse(String draft, Map<String, List<String>> photoData, String bloggerName) {
        if (draft == null || draft.isBlank()) {
            return List.of();
        }
        String introLine = DraftSanitizer.getIntroLine(bloggerName);
        String normalized = normalizeMarkerLayout(draftSanitizer.normalizeDraftLayout(draft, bloggerName));
        List<DraftBlock> blocks = new ArrayList<>();
        Matcher matcher = MARKER_RE.matcher(normalized);
        int lastEnd = 0;
        boolean followsPhoto = false;
        while (matcher.find()) {
            appendTextBlocks(blocks, normalized.substring(lastEnd, matcher.start()), followsPhoto, introLine);
            followsPhoto = false;
            String category = matcher.group(1);
            int index = Integer.parseInt(matcher.group(2)) - 1;
            List<String> paths = photoData.getOrDefault(category, List.of());
            if (index >= 0 && index < paths.size()) {
                Path path = Path.of(paths.get(index));
                if (Files.exists(path)) {
                    blocks.add(DraftBlock.image(path.toAbsolutePath().toString()));
                    followsPhoto = true;
                }
            }
            lastEnd = matcher.end();
        }
        appendTextBlocks(blocks, normalized.substring(lastEnd), followsPhoto, introLine);
        return blocks;
    }

    private void appendTextBlocks(List<DraftBlock> blocks, String raw, boolean followsPhoto, String introLine) {
        String cleaned = cleanText(raw, introLine);
        if (cleaned.isBlank()) {
            return;
        }
        PlaceInfoLayout.Span placeSpan = PlaceInfoLayout.split(cleaned);
        if (placeSpan.hasPlace()) {
            if (!placeSpan.before().isBlank()) {
                appendBodyText(blocks, placeSpan.before(), followsPhoto, introLine);
            }
            blocks.add(DraftBlock.placeInfo(formatPlaceInfoLines(placeSpan.placeBlock())));
            if (!placeSpan.after().isBlank()) {
                appendBodyText(blocks, placeSpan.after(), false, introLine);
            }
            return;
        }
        appendBodyText(blocks, cleaned, followsPhoto, introLine);
    }

    private void appendBodyText(List<DraftBlock> blocks, String text, boolean followsPhoto, String introLine) {
        if (followsPhoto) {
            appendPhotoCaptionBlocks(blocks, text, introLine);
            return;
        }
        List<String> paragraphs = mergeTrailingEmojiParagraphs(coalesceParagraphs(Arrays.stream(text.strip().split("\n{2,}"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList()));
        for (int i = 0; i < paragraphs.size(); i++) {
            String block = coalesceWithNextParagraph(paragraphs, i);
            if (!block.equals(paragraphs.get(i)) && !SectionTitles.isSectionEmojiOnly(paragraphs.get(i))) {
                i++;
            }
            appendParagraphBlock(blocks, block, false, introLine);
        }
    }

    private String coalesceWithNextParagraph(List<String> paragraphs, int index) {
        String block = paragraphs.get(index);
        if (index + 1 >= paragraphs.size()) {
            return block;
        }
        if (isEmojiOnlyLine(block) && !SectionTitles.isSectionEmojiOnly(block)) {
            return mergeEmojiWithNext(block, paragraphs.get(index + 1));
        }
        if (SectionTitles.isSectionEmojiOnly(block)) {
            return SectionTitles.resolve(block);
        }
        return block;
    }

    private String mergeEmojiWithNext(String emojiLine, String nextBlock) {
        String emoji = emojiLine.strip();
        String next = nextBlock.strip();
        if (next.startsWith(emoji)) {
            return next;
        }
        if (emoji.equals("📍") && (next.startsWith("장소 정보") || PLACE_LINE_RE.matcher(next).find())) {
            return next.startsWith("장소 정보")
                    ? SectionTitles.resolve(emoji) + "\n" + next.replaceFirst("^장소 정보\\s*", "").strip()
                    : SectionTitles.resolve(emoji) + "\n" + next;
        }
        return emoji + " " + next;
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

    /**
     * 사진 직후 설명: 빈 줄로 잘려도 하나의 흐름으로 합침.
     * 다음 섹션 제목(🏠·📋 등) 전까지는 한 문단으로 이어 붙임.
     */
    private void appendPhotoCaptionBlocks(List<DraftBlock> blocks, String text, String introLine) {
        List<String> chunks = coalesceParagraphs(Arrays.stream(text.strip().split("\n{2,}"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList());
        StringBuilder caption = new StringBuilder();
        for (String block : chunks) {
            if (isSectionTitleBlock(block)) {
                flushCaption(blocks, caption);
                blocks.add(DraftBlock.sectionTitle(SectionTitles.resolve(block.strip())));
            } else if (looksLikePlaceInfo(block)) {
                flushCaption(blocks, caption);
                blocks.add(DraftBlock.placeInfo(formatPlaceInfoLines(block)));
            } else {
                appendFlatText(caption, block);
            }
        }
        flushCaption(blocks, caption);
    }

    private List<String> coalesceParagraphs(List<String> paragraphs) {
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String block = coalesceWithNextParagraph(paragraphs, i);
            if (!block.equals(paragraphs.get(i)) && !SectionTitles.isSectionEmojiOnly(paragraphs.get(i))) {
                i++;
            }
            merged.add(block);
        }
        return mergeTrailingEmojiParagraphs(merged);
    }

    /** 장식 이모지만 있는 문단은 앞 문단에 붙임 */
    private List<String> mergeTrailingEmojiParagraphs(List<String> paragraphs) {
        List<String> out = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (isDecorativeEmojiOnly(paragraph) && !out.isEmpty()) {
                int last = out.size() - 1;
                out.set(last, out.get(last) + " " + paragraph.strip());
            } else {
                out.add(paragraph);
            }
        }
        return out;
    }

    private boolean isDecorativeEmojiOnly(String text) {
        return isEmojiOnlyLine(text) && !SectionTitles.isSectionEmojiOnly(text);
    }

    private boolean looksLikePlaceInfo(String block) {
        if (block.contains(PLACE_INFO_MARKER)) {
            return true;
        }
        return PLACE_LINE_RE.matcher(block).find();
    }

    private void flushCaption(List<DraftBlock> blocks, StringBuilder caption) {
        if (!caption.isEmpty()) {
            blocks.add(DraftBlock.caption(caption.toString().strip()));
            caption.setLength(0);
        }
    }

    private void appendFlatText(StringBuilder target, String block) {
        String flat = joinLinesAsParagraph(block);
        if (flat.isEmpty()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(flat);
    }

    private void appendParagraphBlock(List<DraftBlock> blocks, String block, boolean tightAfterPhoto, String introLine) {
        if (block.isBlank()) {
            return;
        }
        if (block.contains(introLine)) {
            int idx = block.indexOf(introLine);
            String beforeIntro = block.substring(0, idx).strip();
            String afterIntro = block.substring(idx + introLine.length()).strip();
            if (!beforeIntro.isEmpty()) {
                appendParagraphBlock(blocks, beforeIntro, tightAfterPhoto, introLine);
            }
            blocks.add(DraftBlock.intro(introLine));
            if (!afterIntro.isEmpty()) {
                appendParagraphBlock(blocks, afterIntro, false, introLine);
            }
            return;
        }
        if (isSectionTitleBlock(block)) {
            blocks.add(DraftBlock.sectionTitle(SectionTitles.resolve(block.strip())));
            return;
        }
        if (looksLikePlaceInfo(block)) {
            blocks.add(DraftBlock.placeInfo(formatPlaceInfoLines(block)));
            return;
        }
        String paragraph = joinLinesAsParagraph(block);
        if (!paragraph.isBlank()) {
            if (tightAfterPhoto) {
                blocks.add(DraftBlock.caption(paragraph));
            } else {
                blocks.add(DraftBlock.text(paragraph));
            }
        }
    }

    private boolean isSectionTitleBlock(String block) {
        String line = SectionTitles.resolve(block.strip());
        if (!line.matches("^[🏠📋✨💡📍].*")) {
            return false;
        }
        if (block.lines().filter(l -> !l.isBlank()).count() != 1) {
            return false;
        }
        String afterEmoji = line.replaceFirst("^[🏠📋✨💡📍]\\s*", "").trim();
        return !afterEmoji.isEmpty();
    }

    private String formatPlaceInfoLines(String block) {
        String formatted = SectionTitles.resolve(block).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (formatted.isBlank()) {
            return formatted;
        }
        if (formatted.startsWith("📍")) {
            return formatted;
        }
        if (PLACE_LINE_RE.matcher(formatted).find()) {
            return "📍 장소 정보\n" + formatted;
        }
        return formatted;
    }

    private String joinLinesAsParagraph(String block) {
        return block.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private String cleanText(String text, String introLine) {
        if (text == null) {
            return "";
        }
        String cleaned = text;
        cleaned = cleaned.replaceAll("<a href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2 ($1)");
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        cleaned = cleaned.replaceAll("\\[(?:external|interior|menu|product)_\\d+\\]\\s*", "");
        cleaned = draftSanitizer.unwrapStrikethroughMarkdown(cleaned);
        cleaned = ensureIntroSeparation(cleaned, introLine);
        cleaned = draftSanitizer.sanitizeForNaverEditor(cleaned);
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        return cleaned.strip();
    }

    /** 인트로 문구 뒤에는 항상 빈 줄 1개(\n\n) */
    private String ensureIntroSeparation(String text, String introLine) {
        int idx = text.indexOf(introLine);
        if (idx < 0) {
            return text;
        }
        String before = text.substring(0, idx);
        String after = text.substring(idx + introLine.length()).replaceFirst("^\\s+", "");
        return before + introLine + "\n\n" + after;
    }

    private String normalizeMarkerLayout(String draft) {
        String marker = "\\[(?:external|interior|menu|product|photo)_\\d+\\]";
        String normalized = MARKER_RE.matcher(draft).replaceAll("\n\n$0\n");
        normalized = normalized.replaceAll("(\\n\\s*){2,}(" + marker + ")", "\n\n$2");
        normalized = normalized.replaceAll("(" + marker + ")(\\n\\s*){2,}", "$1\n");
        return normalized.replaceAll("\\n{3,}", "\n\n");
    }
}
