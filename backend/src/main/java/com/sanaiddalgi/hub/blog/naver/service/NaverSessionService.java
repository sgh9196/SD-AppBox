package com.sanaiddalgi.hub.blog.naver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.naver.model.NaverSessionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 네이버 storageState·메타데이터 파일 관리. */
@Service
public class NaverSessionService {

    private final StudioProperties properties;
    private final ObjectMapper objectMapper;

    public NaverSessionService(StudioProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Path sessionFile() {
        return Path.of(properties.getNaverSessionFile());
    }

    public Path metaFile() {
        return Path.of(properties.getNaverSessionMetaFile());
    }

    public boolean hasSessionFile() {
        return Files.exists(sessionFile());
    }

    public NaverSessionStatus getStatus() {
        NaverSessionStatus status = new NaverSessionStatus();
        if (!hasSessionFile()) {
            status.setConnected(false);
            status.setMessage("네이버 로그인 필요");
            return status;
        }
        Map<String, String> meta = loadMeta();
        status.setConnected(true);
        String naverId = meta.getOrDefault("naverId", "");
        String blogId = meta.getOrDefault("blogId", "");
        status.setNaverId(naverId.isBlank() ? blogId : naverId);
        status.setSavedAt(meta.getOrDefault("savedAt", ""));
        String label = !blogId.isBlank() ? blogId : naverId;
        status.setMessage((label.isBlank() ? "연결됨" : label) + " 계정 세션 저장됨");
        return status;
    }

    public void saveMeta(String naverId, String blogId) throws IOException {
        Path meta = metaFile();
        Files.createDirectories(meta.getParent());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("naverId", naverId != null ? naverId : "");
        payload.put("blogId", blogId != null ? blogId : "");
        payload.put("savedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(meta.toFile(), payload);
    }

    public void saveBlogId(String blogId) throws IOException {
        Map<String, String> meta = new LinkedHashMap<>(loadMeta());
        meta.put("blogId", blogId != null ? blogId : "");
        meta.put("savedAt", Instant.now().toString());
        Path file = metaFile();
        Files.createDirectories(file.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), meta);
    }

    public String getBlogId() {
        return loadMeta().getOrDefault("blogId", "");
    }

    public void clearSession() throws IOException {
        Files.deleteIfExists(sessionFile());
        Files.deleteIfExists(metaFile());
    }

    private Map<String, String> loadMeta() {
        if (!Files.exists(metaFile())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metaFile().toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            return Map.of();
        }
    }
}
