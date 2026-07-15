package com.sanaiddalgi.hub.blog.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.repo.ContentRepository;
import com.sanaiddalgi.hub.blog.service.MediaService;
import com.sanaiddalgi.hub.blog.repo.PromptRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private PromptRepository promptRepository;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private MediaService mediaService;

    private StudioProperties properties;
    private ContentService contentService;

    @BeforeEach
    void setUp() {
        properties = new StudioProperties();
        contentService = new ContentService(properties, promptRepository, contentRepository, mediaService);
    }

    @Test
    void useTestModeSkipsGemini() {
        properties.setUseTestMode(true);
        when(promptRepository.loadTestDraft(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyMap()))
                .thenReturn("테스트 원고");

        String draft = contentService.generateBlogPost(
                "테스트매장", "정보", "http://link", "협찬", 5, Map.of(), "mock-api-key");

        assertTrue(draft.contains("테스트 원고"));
    }

    @Test
    void liveModeCallsGemini() {
        properties.setUseTestMode(false);
        when(promptRepository.buildPromptText(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyMap()))
                .thenReturn("prompt");
        when(contentRepository.generateContent(anyString(), anyMap(), any(), anyString()))
                .thenReturn("Gemini 원고");

        String draft = contentService.generateBlogPost(
                "매장", "정보", "link", "협찬", "글로벌", 5, "",
                Map.of("external", List.of("/tmp/1.jpg")), message -> {}, "mock-api-key");

        assertFalse(draft.isBlank());
    }
}
