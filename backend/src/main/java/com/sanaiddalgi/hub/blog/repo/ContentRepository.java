package com.sanaiddalgi.hub.blog.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanaiddalgi.hub.config.StudioProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Google Gemini generateContent REST 호출. 사진을 inlineData(base64)로 멀티모달 전송. */
@Repository
public class ContentRepository {

    private final StudioProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ContentRepository(
            StudioProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String generateContent(String promptText, Map<String, List<String>> photoData) {
        return generateContent(promptText, photoData, message -> {});
    }

    public String generateContent(
            String promptText, Map<String, List<String>> photoData, Consumer<String> progress) {
        return generateContent(promptText, photoData, progress, properties.getGeminiApiKey());
    }

    public String generateContent(
            String promptText, Map<String, List<String>> photoData, Consumer<String> progress, String apiKey) {
        ObjectNode body = buildRequestBody(promptText, photoData, false);
        return callGeminiWithRetry(body, progress, false, apiKey);
    }


    /** 웹 검색 포함 호출이 한도 등으로 실패했을 때, 검색 없이 재시도할지 판단. */
    public boolean shouldFallbackWithoutWebSearch(Throwable error) {
        if (isModelUnavailable(error)) {
            return false;
        }
        String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (msg.contains("api key not valid") || msg.contains("permission denied")) {
            return false;
        }
        if (msg.contains("ground")
                || msg.contains("grounding")
                || msg.contains("google_search")
                || msg.contains("google search")) {
            return isQuotaOrRateLimit(msg);
        }
        if (msg.contains("exceeded your current quota")
                || msg.contains("prepayment credits")
                || msg.contains("free_tier")) {
            return !msg.contains("limit: 0");
        }
        return msg.contains("429") && isQuotaOrRateLimit(msg);
    }

    private boolean isQuotaOrRateLimit(String lowerMsg) {
        return lowerMsg.contains("quota")
                || lowerMsg.contains("limit")
                || lowerMsg.contains("exceeded")
                || lowerMsg.contains("resource exhausted")
                || lowerMsg.contains("rate limit");
    }

    private String callGeminiWithRetry(ObjectNode body, Consumer<String> progress, boolean webSearch, String apiKey) {
        List<String> models = modelCandidates();
        RuntimeException lastError = null;

        for (String model : models) {
            int maxAttempts = Math.max(1, properties.getGeminiRetryMaxAttempts());
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    if (attempt == 1 && models.indexOf(model) > 0) {
                        progress.accept("Gemini 모델 전환: " + model);
                    }
                    return callGemini(model, body, apiKey);
                } catch (RuntimeException e) {
                    lastError = e;
                    if (isModelUnavailable(e)) {
                        progress.accept("Gemini 모델 미지원: " + model + " — 다음 모델 시도");
                        break;
                    }
                    if (isQuotaExceeded(e)) {
                        break;
                    }
                    if (!isRetryable(e) || attempt >= maxAttempts) {
                        break;
                    }
                    long waitMs = backoffMs(attempt);
                    progress.accept(
                            "Gemini 일시 과부하(" + model + ") — "
                                    + (waitMs / 1000)
                                    + "초 후 재시도 "
                                    + (attempt + 1)
                                    + "/"
                                    + maxAttempts);
                    sleep(waitMs);
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new RuntimeException("Gemini API 호출 실패");
    }

    private List<String> modelCandidates() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(properties.getGeminiModel());
        if (properties.getGeminiFallbackModels() != null) {
            for (String model : properties.getGeminiFallbackModels()) {
                if (model != null && !model.isBlank()) {
                    models.add(model.trim());
                }
            }
        }
        return new ArrayList<>(models);
    }

    private ObjectNode buildRequestBody(
            String promptText, Map<String, List<String>> photoData, boolean googleSearch) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        appendPhotoParts(parts, photoData, googleSearch);
        parts.addObject().put("text", promptText);

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", properties.getGeminiTemperature());
        if (googleSearch) {
            body.putArray("tools").addObject().putObject("google_search");
        }
        return body;
    }

    private String callGemini(String model, ObjectNode body, String apiKey) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException(extractApiErrorMessage(e), e);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                String text = extractResponseText(candidates.get(0));
                if (!text.isBlank()) {
                    return text.trim();
                }
            }
            String errorMessage = root.path("error").path("message").asText("API 응답이 비어 있습니다.");
            throw new RuntimeException(errorMessage);
        } catch (IOException e) {
            throw new RuntimeException("Gemini 응답 파싱 실패", e);
        }
    }

    private String extractResponseText(JsonNode candidate) {
        StringBuilder builder = new StringBuilder();
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        for (JsonNode part : parts) {
            if (part.has("text")) {
                builder.append(part.path("text").asText(""));
            }
        }
        return builder.toString();
    }

    private String extractApiErrorMessage(RestClientResponseException e) {
        String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                String message = root.path("error").path("message").asText("");
                if (!message.isBlank()) {
                    return message;
                }
            } catch (IOException ignored) {
                // use status line below
            }
        }
        return e.getStatusCode().value() + " " + e.getStatusText() + ": " + shorten(body, 240);
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private boolean isModelUnavailable(Throwable error) {
        String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return msg.contains("not found")
                || msg.contains("is not supported for generatecontent")
                || msg.contains("not supported for");
    }

    private boolean isQuotaExceeded(Throwable error) {
        String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return msg.contains("exceeded your current quota")
                || msg.contains("limit: 0")
                || msg.contains("prepayment credits")
                || msg.contains("free_tier");
    }

    private boolean isRetryable(Throwable error) {
        String msg = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (isQuotaExceeded(error)) {
            return false;
        }
        if (msg.contains("high demand")
                || msg.contains("unavailable")
                || msg.contains("overloaded")
                || msg.contains("try again later")
                || msg.contains("rate limit")
                || msg.contains("resource exhausted")) {
            return true;
        }
        return msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("429")
                || msg.contains("500");
    }

    private long backoffMs(int attempt) {
        long base = Math.max(1_000, properties.getGeminiRetryBackoffMs());
        long wait = base * (1L << Math.min(attempt - 1, 3));
        return Math.min(wait, 30_000);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini 재시도가 중단되었습니다.", e);
        }
    }

    private void appendPhotoParts(ArrayNode parts, Map<String, List<String>> photoData, boolean googleSearch)
            throws RuntimeException {
        if (countPhotos(photoData) == 0) {
            return;
        }
        appendReviewPhotoParts(parts, photoData);
    }


    private void appendReviewPhotoParts(ArrayNode parts, Map<String, List<String>> photoData) throws RuntimeException {
        Map<String, String> labels = Map.of(
                "external", "외관", "interior", "내부", "menu", "메뉴", "product", "상품");
        parts.addObject().put("text",
                "아래 사진을 각각 분석하세요. "
                        + "각 사진 바로 위 라벨(예: [product_1])이 본문에 넣을 마커입니다. "
                        + "마커 뒤 글은 사진 설명·캡션이 아니라, 그 장면이 이어지는 경험담으로 자연스럽게 써. "
                        + "'사진', '찍은', '나온 사진' 같은 표현은 쓰지 마. "
                        + "사진에 보이는 내용만 근거로 하고, 보이지 않는 메뉴·상품·약품은 지어내지 마세요.");
        for (String cat : StudioProperties.PHOTO_CATEGORIES) {
            List<String> paths = photoData.getOrDefault(cat, List.of());
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                if (!Files.exists(Path.of(path))) {
                    continue;
                }
                String marker = "[" + cat + "_" + (i + 1) + "]";
                parts.addObject().put("text", marker + " (" + labels.get(cat) + ")");
                appendImagePart(parts, path);
            }
        }
    }

    private void appendImagePart(ArrayNode parts, String path) throws RuntimeException {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            ObjectNode inline = parts.addObject().putObject("inlineData");
            inline.put("mimeType", photoMimeType(path));
            inline.put("data", Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            throw new RuntimeException("사진 읽기 실패: " + path, e);
        }
    }

    private int countPhotos(Map<String, List<String>> photoData) {
        return photoData.values().stream().mapToInt(List::size).sum();
    }

    private String photoMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
