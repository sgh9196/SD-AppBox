package com.sanaiddalgi.hub.blog.service;

import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.service.PlaceInfoLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

/**
 * Gemini 원고를 Apache POI로 Word(docx) 변환.
 * [external_1] 등 마커 위치에 실제 사진 삽입, 장소 정보는 줄 단위로 단락 분리.
 */
@Service
public class ExportService {

    private static final Pattern MARKER_RE =
            Pattern.compile("\\[(external|interior|menu|product|photo)_(\\d+)\\]");
    private static final Pattern EMOJI_RE = Pattern.compile(
            "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F\\u200D]+");
    private static final int IMAGE_WIDTH_EMU = Units.toEMU(5.5 * 72);
    /** 본문에 내용이 한 번이라도 들어갔는지 — 섹션(카테고리) 앞 빈 줄 판단용 */
    private boolean bodyStarted;

    private final StudioProperties properties;

    public ExportService(StudioProperties properties) {
        this.properties = properties;
    }

    public Path createDocx(String draft, Map<String, List<String>> photoData, String bloggerName)
            throws IOException, InvalidFormatException {
        Path outputDir = Path.of(properties.getOutputDir());
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve(properties.getDefaultDocx());
        String introLine = DraftSanitizer.getIntroLine(bloggerName);

        try (XWPFDocument doc = new XWPFDocument()) {
            bodyStarted = false;
            insertDraftWithMarkers(doc, normalizeMarkerLayout(draft), photoData, introLine);
            appendMissingPhotos(doc, draft, photoData, introLine);
            try (var out = Files.newOutputStream(output)) {
                doc.write(out);
            }
        }
        return output.toAbsolutePath();
    }

    /** 마커 앞뒤 불필요한 빈 줄 제거 — 사진 직후 설명이 끊기지 않게 */
    private String normalizeMarkerLayout(String draft) {
        String marker = "\\[(?:external|interior|menu|product|photo)_\\d+\\]";
        String normalized = MARKER_RE.matcher(draft).replaceAll("\n\n$0\n");
        normalized = normalized.replaceAll("(\\n\\s*){2,}(" + marker + ")", "\n\n$2");
        normalized = normalized.replaceAll("(" + marker + ")(\\n\\s*){2,}", "$1\n");
        return normalized.replaceAll("\\n{3,}", "\n\n");
    }

    private void insertDraftWithMarkers(
            XWPFDocument doc, String draft, Map<String, List<String>> photoData, String introLine)
            throws IOException, InvalidFormatException {
        Matcher matcher = MARKER_RE.matcher(draft);
        int lastEnd = 0;
        boolean followsPhoto = false;
        while (matcher.find()) {
            String before = draft.substring(lastEnd, matcher.start()).strip();
            if (!before.isBlank()) {
                addText(doc, before, followsPhoto, introLine);
            }
            insertPhoto(doc, matcher.group(1), Integer.parseInt(matcher.group(2)), photoData);
            followsPhoto = true;
            lastEnd = matcher.end();
        }
        String tail = draft.substring(lastEnd).strip();
        if (!tail.isBlank()) {
            addText(doc, tail, followsPhoto, introLine);
        }
    }

    private void insertPhoto(
            XWPFDocument doc, String category, int markerIndex, Map<String, List<String>> photoData)
            throws IOException, InvalidFormatException {
        List<String> paths = photoData.getOrDefault(category, List.of());
        int index = markerIndex - 1;
        if (index < 0 || index >= paths.size()) {
            return;
        }
        Path imagePath = Path.of(paths.get(index));
        if (Files.exists(imagePath)) {
            addImage(doc, imagePath);
        }
    }

    private void addImage(XWPFDocument doc, Path path) throws IOException, InvalidFormatException {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(60);
        XWPFRun run = paragraph.createRun();
        ImagePayload payload = loadImagePayload(path);
        int[] sizeEmu = scaledSizeEmu(payload.widthPx(), payload.heightPx());
        try (InputStream in = new ByteArrayInputStream(payload.bytes())) {
            run.addPicture(
                    in,
                    payload.pictureType(),
                    path.getFileName().toString(),
                    sizeEmu[0],
                    sizeEmu[1]);
        }
        bodyStarted = true;
    }

    private int[] scaledSizeEmu(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) {
            return new int[] {IMAGE_WIDTH_EMU, IMAGE_WIDTH_EMU};
        }
        int heightEmu = (int) ((long) IMAGE_WIDTH_EMU * heightPx / widthPx);
        return new int[] {IMAGE_WIDTH_EMU, heightEmu};
    }

    private ImagePayload loadImagePayload(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            BufferedImage image = ImageIO.read(path.toFile());
            return new ImagePayload(
                    Files.readAllBytes(path),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                    image.getWidth(),
                    image.getHeight());
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            BufferedImage image = ImageIO.read(path.toFile());
            return new ImagePayload(
                    Files.readAllBytes(path),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG,
                    image.getWidth(),
                    image.getHeight());
        }
        if (fileName.endsWith(".webp")) {
            BufferedImage image = ImageIO.read(path.toFile());
            return convertToJpegPayload(image);
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("지원하지 않는 이미지 형식: " + path);
        }
        return convertToJpegPayload(image);
    }

    private ImagePayload convertToJpegPayload(BufferedImage source) throws IOException {
        if (source == null) {
            throw new IOException("이미지를 읽을 수 없습니다.");
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(source, 0, 0, Color.WHITE, null);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", out);
        return new ImagePayload(
                out.toByteArray(),
                org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG,
                source.getWidth(),
                source.getHeight());
    }

    private void addText(XWPFDocument doc, String text, boolean followsPhoto, String introLine) {
        String cleaned = normalizeDraftText(text, introLine);
        PlaceInfoLayout.Span placeSpan = PlaceInfoLayout.split(cleaned);
        if (placeSpan.hasPlace()) {
            if (!placeSpan.before().isBlank()) {
                addBodyText(doc, placeSpan.before(), followsPhoto, introLine);
            }
            addLineBreakText(doc, placeSpan.placeBlock(), true);
            if (!placeSpan.after().isBlank()) {
                addCategoryGap(doc);
                addBodyText(doc, placeSpan.after(), false, introLine);
            }
            return;
        }
        addBodyText(doc, cleaned, followsPhoto, introLine);
    }

    private String normalizeDraftText(String text, String introLine) {
        String cleaned = text.replace("\r\n", "\n");
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("<a href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2 ($1)");
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        cleaned = ensureIntroSeparation(cleaned, introLine);
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

    private void addBodyText(XWPFDocument doc, String text, boolean followsPhoto, String introLine) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (followsPhoto) {
            addPhotoCaptionText(doc, text, introLine);
            return;
        }
        for (String block : text.strip().split("\n{2,}")) {
            addParagraphFromBlock(doc, block, false, introLine);
        }
    }

    /**
     * 사진 직후 설명: 빈 줄로 잘려도 하나의 흐름으로 합침.
     * 다음 섹션 제목(🏠·📋 등) 전까지는 한 문단으로 이어 붙임.
     */
    private void addPhotoCaptionText(XWPFDocument doc, String text, String introLine) {
        List<String> blocks = Arrays.stream(text.strip().split("\n{2,}"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
        StringBuilder caption = new StringBuilder();
        for (String block : blocks) {
            if (isSectionTitleBlock(block)) {
                flushCaption(doc, caption);
                addParagraphFromBlock(doc, block, true, introLine);
            } else {
                appendFlatText(caption, block);
            }
        }
        flushCaption(doc, caption);
    }

    private void flushCaption(XWPFDocument doc, StringBuilder caption) {
        if (!caption.isEmpty()) {
            addParagraph(doc, caption.toString().strip(), true);
            caption.setLength(0);
        }
    }

    private void appendFlatText(StringBuilder target, String block) {
        String flat = block.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        if (flat.isEmpty()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(flat);
    }

    private void addParagraphFromBlock(XWPFDocument doc, String block, boolean tightAfterPhoto, String introLine) {
        if (isSectionTitleBlock(block)) {
            addCategoryGap(doc);
        }
        if (block.contains(introLine)) {
            int idx = block.indexOf(introLine);
            String beforeIntro = block.substring(0, idx).strip();
            String afterIntro = block.substring(idx + introLine.length()).strip();
            if (!beforeIntro.isEmpty()) {
                addParagraphFromBlock(doc, beforeIntro, tightAfterPhoto, introLine);
            }
            addParagraph(doc, introLine, false);
            addIntroLineBreak(doc);
            if (!afterIntro.isEmpty()) {
                addParagraphFromBlock(doc, afterIntro, tightAfterPhoto, introLine);
            }
            return;
        }
        String paragraphText = block.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        if (!paragraphText.isEmpty()) {
            addParagraph(doc, paragraphText, tightAfterPhoto);
        }
    }

    /** 인트로 문구 직후 — 빈 줄 1줄(빈 단락 1개) */
    private void addIntroLineBreak(XWPFDocument doc) {
        XWPFParagraph gap = doc.createParagraph();
        gap.setSpacingBefore(0);
        gap.setSpacingAfter(0);
    }

    /** 섹션(🏠·📋·✨·💡·📍) 앞 — 빈 줄 2줄(빈 단락 2개) */
    private void addCategoryGap(XWPFDocument doc) {
        if (!bodyStarted) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            XWPFParagraph gap = doc.createParagraph();
            gap.setSpacingBefore(0);
            gap.setSpacingAfter(0);
        }
    }

    private boolean isSectionTitleBlock(String block) {
        String line = block.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("");
        return line.matches("^[🏠📋✨💡📍].*") && block.lines().filter(l -> !l.isBlank()).count() == 1;
    }

    private void addLineBreakText(XWPFDocument doc, String text, boolean categoryBoundary) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (categoryBoundary) {
            addCategoryGap(doc);
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                addParagraph(doc, trimmed, false);
            }
        }
    }

    private void addParagraph(XWPFDocument doc, String paragraphText, boolean tightAfterPhoto) {
        if (paragraphText == null || paragraphText.isEmpty()) {
            return;
        }
        XWPFParagraph paragraph = doc.createParagraph();
        if (tightAfterPhoto) {
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(100);
        } else {
            paragraph.setSpacingAfter(100);
        }
        addTextRuns(paragraph, paragraphText, StudioProperties.TEXT_FONT);
        bodyStarted = true;
    }

    private void addTextRuns(XWPFParagraph paragraph, String text, String defaultFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] parts = EMOJI_RE.split(text);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            XWPFRun run = paragraph.createRun();
            run.setText(parts[i]);
            run.setFontSize(11);
            setRunFont(run, i % 2 == 1 ? StudioProperties.EMOJI_FONT : defaultFont);
        }
    }

    private void setRunFont(XWPFRun run, String fontName) {
        run.setFontFamily(fontName);
    }

    private void appendMissingPhotos(XWPFDocument doc, String draft, Map<String, List<String>> photoData, String introLine)
            throws IOException, InvalidFormatException {
        Set<String> used = new HashSet<>();
        Matcher matcher = MARKER_RE.matcher(draft);
        while (matcher.find()) {
            used.add(matcher.group(1) + ":" + matcher.group(2));
        }
        List<Path> missing = new ArrayList<>();
        for (String cat : StudioProperties.PHOTO_CATEGORIES) {
            appendMissingForCategory(photoData, used, cat, missing);
        }
        if (missing.isEmpty()) {
            return;
        }
        addText(doc, "── 추가 사진 ──", false, introLine);
        for (Path path : missing) {
            addImage(doc, path);
        }
    }

    private void appendMissingForCategory(
            Map<String, List<String>> photoData,
            Set<String> used,
            String category,
            List<Path> missing) {
        List<String> paths = photoData.getOrDefault(category, List.of());
        for (int i = 0; i < paths.size(); i++) {
            String key = category + ":" + (i + 1);
            Path path = Path.of(paths.get(i));
            if (!used.contains(key) && Files.exists(path)) {
                missing.add(path);
            }
        }
    }

    private record ImagePayload(byte[] bytes, int pictureType, int widthPx, int heightPx) {
    }
}
