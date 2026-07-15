package com.sanaiddalgi.hub.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.config.StudioProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeServiceTest {

    private StudioProperties properties;
    private ObjectMapper objectMapper;
    private CodeService codeService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new StudioProperties();
        properties.setCodeFile(tempDir.resolve("codes.enc").toString());
        objectMapper = new ObjectMapper();
        codeService = new CodeService(properties, objectMapper);
    }

    @Test
    void verifyAdminCode() {
        assertTrue(codeService.isAdminCode("admin-ghShin"));
        assertFalse(codeService.isAdminCode("wrong-admin"));
        
        List<String> apps = codeService.verifyCode("admin-ghShin");
        assertTrue(apps.contains("geulobel"));
        assertTrue(apps.contains("marketing"));
    }

    @Test
    void issueAndVerifyAndCleanCode() throws Exception {
        // 1. Initial State
        List<String> verifyEmpty = codeService.verifyCode("myUserCode");
        assertTrue(verifyEmpty.isEmpty());

        // 2. Issue a code
        codeService.issueCode("myUserCode", List.of("geulobel"));

        // 3. Verify the code
        List<String> verifySuccess = codeService.verifyCode("myUserCode");
        assertEquals(1, verifySuccess.size());
        assertTrue(verifySuccess.contains("geulobel"));
        assertFalse(verifySuccess.contains("marketing"));

        // 4. Retrieve all codes
        Map<String, List<String>> allCodes = codeService.getAllCodes();
        assertEquals(1, allCodes.size());
        assertTrue(allCodes.containsKey("myUserCode"));

        // 5. Delete the code
        codeService.deleteCode("myUserCode");
        assertTrue(codeService.verifyCode("myUserCode").isEmpty());
    }
}
