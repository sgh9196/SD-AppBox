package com.sanaiddalgi.hub.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React SPA 라우트를 index.html로 포워드 (새로고침 시 404 방지). */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/campaign", "/blog"})
    public String forwardApp() {
        return "forward:/index.html";
    }
}
