package com.sanaiddalgi.hub.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.config.StudioProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 사용자 접속 코드의 발급, 암호화 저장, 조회 및 검증을 담당하는 서비스.
 * AES-128 알고리즘을 사용해 코드를 파일에 암호화하여 저장 관리합니다.
 */
@Service
public class CodeService {

    private final StudioProperties properties;
    private final ObjectMapper objectMapper;

    private static final String ALGORITHM = "AES";
    private static final byte[] KEY_BYTES = "ghShinStudioCode".getBytes(StandardCharsets.UTF_8);
    private static final String ADMIN_CODE = "admin-ghShin";

    public CodeService(StudioProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isAdminCode(String code) {
        return ADMIN_CODE.equals(code);
    }

    public List<String> verifyCode(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        if (isAdminCode(code)) {
            return List.of("geulobel", "marketing", "influencer");
        }
        Map<String, List<String>> db = loadCodes();
        return db.getOrDefault(code.trim(), List.of());
    }

    public synchronized void issueCode(String code, List<String> allowedApps) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("코드를 입력해야 합니다.");
        }
        if (isAdminCode(code)) {
            throw new IllegalArgumentException("관리자 코드는 새로 발급할 수 없습니다.");
        }
        Map<String, List<String>> db = new LinkedHashMap<>(loadCodes());
        db.put(code.trim(), allowedApps == null ? List.of() : allowedApps);
        saveCodes(db);
    }

    public synchronized void deleteCode(String code) throws Exception {
        if (code == null || code.isBlank()) {
            return;
        }
        Map<String, List<String>> db = new LinkedHashMap<>(loadCodes());
        if (db.containsKey(code)) {
            db.remove(code);
            saveCodes(db);
        }
    }

    public Map<String, List<String>> getAllCodes() {
        return loadCodes();
    }

    private Map<String, List<String>> loadCodes() {
        Path file = Path.of(properties.getCodeFile());
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            String decryptedJson = decrypt(encrypted);
            return objectMapper.readValue(decryptedJson, new TypeReference<>() {});
        } catch (Exception e) {
            // 복호화 실패 시 빈 데이터베이스 반환
            return Map.of();
        }
    }

    private void saveCodes(Map<String, List<String>> db) throws Exception {
        Path file = Path.of(properties.getCodeFile());
        Files.createDirectories(file.getParent());
        String json = objectMapper.writeValueAsString(db);
        byte[] encrypted = encrypt(json);
        Files.write(file, encrypted);
    }

    private byte[] encrypt(String data) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(KEY_BYTES, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String decrypt(byte[] encryptedData) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(KEY_BYTES, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8);
    }
}
