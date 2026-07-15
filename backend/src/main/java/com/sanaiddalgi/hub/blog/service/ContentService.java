package com.sanaiddalgi.hub.blog.service;

import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.repo.ContentRepository;
import com.sanaiddalgi.hub.blog.service.MediaService;
import com.sanaiddalgi.hub.blog.repo.PromptRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Gemini API로 블로그 원고 생성. 테스트 모드면 mock/템플릿 원고 반환. */
@Service
public class ContentService {

    private final StudioProperties properties;
    private final PromptRepository promptRepository;
    private final ContentRepository contentRepository;
    private final MediaService mediaService;

    public ContentService(
            StudioProperties properties,
            PromptRepository promptRepository,
            ContentRepository contentRepository,
            MediaService mediaService) {
        this.properties = properties;
        this.promptRepository = promptRepository;
        this.contentRepository = contentRepository;
        this.mediaService = mediaService;
    }

    public String generateBlogPost(
            String restaurantName,
            String infoText,
            String restaurantLink,
            String postType,
            int rating,
            Map<String, List<String>> photoData) {
        return generateBlogPost(
                restaurantName, infoText, restaurantLink, postType, "글로벌", rating, "", photoData, message -> {});
    }

    public String generateBlogPost(
            String restaurantName,
            String infoText,
            String restaurantLink,
            String postType,
            String bloggerName,
            int rating,
            String campaignGuideline,
            Map<String, List<String>> photoData,
            Consumer<String> progress) {
        if (properties.isUseTestMode()) {
            return promptRepository.loadTestDraft(
                    restaurantName, infoText, restaurantLink, postType, bloggerName, rating, campaignGuideline, photoData);
        }

        String prompt = promptRepository.buildPromptText(
                restaurantName, infoText, restaurantLink, postType, bloggerName, rating, campaignGuideline, photoData);
        try {
            String text = contentRepository.generateContent(prompt, photoData, progress);
            if (text == null || text.isBlank()) {
                throw new RuntimeException("API 응답이 비어 있습니다.");
            }
            return text;
        } catch (RuntimeException e) {
            throw new RuntimeException(formatGeminiError(e), e);
        }
    }


    private String formatGeminiError(Throwable error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("high demand")
                || lower.contains("503")
                || lower.contains("unavailable")) {
            return "Gemini 서버가 일시적으로 과부하 상태입니다. 잠시 후 다시 시도해 주세요.";
        }
        if (lower.contains("not found") || lower.contains("not supported for generatecontent")) {
            return "설정된 Gemini 모델을 사용할 수 없습니다. "
                    + "application.yml 의 gemini-model / gemini-fallback-models 를 "
                    + "gemini-2.5-flash, gemini-2.5-flash-lite 로 맞춰 주세요.";
        }
        if (lower.contains("limit: 0")) {
            return "모델 '" + properties.getGeminiModel()
                    + "'은(는) 현재 API 키의 무료 플랜에서 사용할 수 없습니다 (limit: 0).\n"
                    + "   → GEMINI_MODEL을 'gemini-2.5-flash'로 변경해 보세요.";
        }
        if (message.toLowerCase().contains("exceeded your current quota")) {
            return "Gemini API 할당량이 초과되었습니다. 잠시 후 다시 시도하세요.";
        }
        return message;
    }
}
