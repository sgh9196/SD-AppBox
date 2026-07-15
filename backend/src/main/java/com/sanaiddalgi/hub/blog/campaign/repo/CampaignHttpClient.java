package com.sanaiddalgi.hub.blog.campaign.repo;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 체험단 사이트 HTTP GET — EUC-KR/UTF-8 인코딩 자동 판별. */
@Component
public class CampaignHttpClient {

    private final RestClient restClient;

    public CampaignHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String get(String url) {
        byte[] bytes = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(byte[].class);
        return decodeHtml(bytes);
    }

    public String post(String url, String formBody) {
        return post(url, formBody, url.substring(0, url.indexOf('/', 8)) + "/");
    }

    public String post(String url, String formBody, String referer) {
        byte[] bytes = restClient.post()
                .uri(URI.create(url))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Referer", referer)
                .body(formBody)
                .retrieve()
                .body(byte[].class);
        return decodeHtml(bytes);
    }

    private String decodeHtml(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("MS949"));
    }
}
