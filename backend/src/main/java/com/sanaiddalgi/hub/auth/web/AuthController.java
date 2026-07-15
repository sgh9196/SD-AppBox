package com.sanaiddalgi.hub.auth.web;

import com.sanaiddalgi.hub.auth.service.CodeService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로그인 세션 검증 및 관리자 코드 발급 관련 REST 컨트롤러.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CodeService codeService;

    public AuthController(CodeService codeService) {
        this.codeService = codeService;
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.ok(Map.of("valid", false, "allowedApps", List.of(), "isAdmin", false));
        }
        List<String> allowedApps = codeService.verifyCode(code);
        boolean isAdmin = codeService.isAdminCode(code);
        boolean valid = isAdmin || !allowedApps.isEmpty();
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "allowedApps", allowedApps,
                "isAdmin", isAdmin
        ));
    }

    @PostMapping("/issue")
    public ResponseEntity<Map<String, Object>> issue(@RequestBody IssueRequest request) {
        if (!codeService.isAdminCode(request.adminCode)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "관리자 권한이 없습니다."));
        }
        try {
            codeService.issueCode(request.newCode, request.allowedApps);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "코드 생성에 실패했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/codes")
    public ResponseEntity<?> getCodes(@RequestParam String adminCode) {
        if (!codeService.isAdminCode(adminCode)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "관리자 권한이 없습니다."));
        }
        return ResponseEntity.ok(codeService.getAllCodes());
    }

    @DeleteMapping("/codes/{code}")
    public ResponseEntity<Map<String, Object>> deleteCode(@PathVariable String code, @RequestParam String adminCode) {
        if (!codeService.isAdminCode(adminCode)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "관리자 권한이 없습니다."));
        }
        try {
            codeService.deleteCode(code);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "코드 삭제 실패: " + e.getMessage()));
        }
    }

    public static class IssueRequest {
        public String adminCode;
        public String newCode;
        public List<String> allowedApps;
    }
}
