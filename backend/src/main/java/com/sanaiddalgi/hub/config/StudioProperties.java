package com.sanaiddalgi.hub.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml / application-local.yml 의 studio.* 설정 바인딩.
 * 경로(prompt, output, photos 등)는 run.bat 실행 시 user.dir(워크스페이스 루트) 기준.
 */
@ConfigurationProperties(prefix = "studio")
public class StudioProperties {

    private String geminiApiKey = "";
    private String publicApiKey = "";
    private String geminiModel = "gemini-2.5-flash";
    private double geminiTemperature = 0.85;
    private int geminiRetryMaxAttempts = 4;
    private int geminiRetryBackoffMs = 5_000;
    private List<String> geminiFallbackModels = List.of("gemini-2.5-flash-lite");
    private String defaultCampaignRegion = "대전";
    private int campaignCacheTtlMinutes = 30;
    private String promptFile = "";
    private String promptBaseFile = "";
    private String outputDir = "";
    private String photosDir = "";
    private String webUploadDir = "";
    private String defaultDocx = "preview.docx";
    private String campaignCacheFile = "";
    private String mockResponseFile = "";
    private boolean useTestMode;
    private boolean skipPublicApi;
    private String naverSessionFile = "";
    private String naverSessionMetaFile = "";
    private String codeFile = "";
    private boolean naverPlaywrightHeadless;
    private int naverPlaywrightSlowMoMs = 50;
    private int naverLoginTimeoutMs = 180_000;
    private int naverEditorActionTimeoutMs = 20_000;
    private int naverEditorLoadTimeoutMs = 60_000;
    private boolean naverKeepBrowserOpen = true;

    public static final List<String> PHOTO_CATEGORIES = List.of("external", "interior", "menu", "product");
    public static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");
    public static final String TEXT_FONT = "맑은 고딕";
    public static final String EMOJI_FONT = "Segoe UI Emoji";

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getPublicApiKey() {
        return publicApiKey;
    }

    public void setPublicApiKey(String publicApiKey) {
        this.publicApiKey = publicApiKey;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public double getGeminiTemperature() {
        return geminiTemperature;
    }

    public void setGeminiTemperature(double geminiTemperature) {
        this.geminiTemperature = geminiTemperature;
    }

    public int getGeminiRetryMaxAttempts() {
        return geminiRetryMaxAttempts;
    }

    public void setGeminiRetryMaxAttempts(int geminiRetryMaxAttempts) {
        this.geminiRetryMaxAttempts = geminiRetryMaxAttempts;
    }

    public int getGeminiRetryBackoffMs() {
        return geminiRetryBackoffMs;
    }

    public void setGeminiRetryBackoffMs(int geminiRetryBackoffMs) {
        this.geminiRetryBackoffMs = geminiRetryBackoffMs;
    }

    public List<String> getGeminiFallbackModels() {
        return geminiFallbackModels;
    }

    public void setGeminiFallbackModels(List<String> geminiFallbackModels) {
        this.geminiFallbackModels = geminiFallbackModels;
    }

    public String getDefaultCampaignRegion() {
        return defaultCampaignRegion;
    }

    public void setDefaultCampaignRegion(String defaultCampaignRegion) {
        this.defaultCampaignRegion = defaultCampaignRegion;
    }

    public int getCampaignCacheTtlMinutes() {
        return campaignCacheTtlMinutes;
    }

    public void setCampaignCacheTtlMinutes(int campaignCacheTtlMinutes) {
        this.campaignCacheTtlMinutes = campaignCacheTtlMinutes;
    }

    public String getPromptFile() {
        return promptFile;
    }

    public void setPromptFile(String promptFile) {
        this.promptFile = promptFile;
    }

    public String getPromptBaseFile() {
        return promptBaseFile;
    }

    public void setPromptBaseFile(String promptBaseFile) {
        this.promptBaseFile = promptBaseFile;
    }


    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getPhotosDir() {
        return photosDir;
    }

    public void setPhotosDir(String photosDir) {
        this.photosDir = photosDir;
    }

    public String getWebUploadDir() {
        return webUploadDir;
    }

    public void setWebUploadDir(String webUploadDir) {
        this.webUploadDir = webUploadDir;
    }

    public String getDefaultDocx() {
        return defaultDocx;
    }

    public void setDefaultDocx(String defaultDocx) {
        this.defaultDocx = defaultDocx;
    }

    public String getCampaignCacheFile() {
        return campaignCacheFile;
    }

    public void setCampaignCacheFile(String campaignCacheFile) {
        this.campaignCacheFile = campaignCacheFile;
    }

    public String getMockResponseFile() {
        return mockResponseFile;
    }

    public void setMockResponseFile(String mockResponseFile) {
        this.mockResponseFile = mockResponseFile;
    }

    public boolean isUseTestMode() {
        return useTestMode;
    }

    public void setUseTestMode(boolean useTestMode) {
        this.useTestMode = useTestMode;
    }

    public boolean isSkipPublicApi() {
        return skipPublicApi;
    }

    public void setSkipPublicApi(boolean skipPublicApi) {
        this.skipPublicApi = skipPublicApi;
    }

    public String getNaverSessionFile() {
        return naverSessionFile;
    }

    public void setNaverSessionFile(String naverSessionFile) {
        this.naverSessionFile = naverSessionFile;
    }

    public String getNaverSessionMetaFile() {
        return naverSessionMetaFile;
    }

    public void setNaverSessionMetaFile(String naverSessionMetaFile) {
        this.naverSessionMetaFile = naverSessionMetaFile;
    }

    public boolean isNaverPlaywrightHeadless() {
        return naverPlaywrightHeadless;
    }

    public void setNaverPlaywrightHeadless(boolean naverPlaywrightHeadless) {
        this.naverPlaywrightHeadless = naverPlaywrightHeadless;
    }

    public int getNaverPlaywrightSlowMoMs() {
        return naverPlaywrightSlowMoMs;
    }

    public void setNaverPlaywrightSlowMoMs(int naverPlaywrightSlowMoMs) {
        this.naverPlaywrightSlowMoMs = naverPlaywrightSlowMoMs;
    }

    public int getNaverLoginTimeoutMs() {
        return naverLoginTimeoutMs;
    }

    public void setNaverLoginTimeoutMs(int naverLoginTimeoutMs) {
        this.naverLoginTimeoutMs = naverLoginTimeoutMs;
    }

    public int getNaverEditorActionTimeoutMs() {
        return naverEditorActionTimeoutMs;
    }

    public void setNaverEditorActionTimeoutMs(int naverEditorActionTimeoutMs) {
        this.naverEditorActionTimeoutMs = naverEditorActionTimeoutMs;
    }

    public int getNaverEditorLoadTimeoutMs() {
        return naverEditorLoadTimeoutMs;
    }

    public void setNaverEditorLoadTimeoutMs(int naverEditorLoadTimeoutMs) {
        this.naverEditorLoadTimeoutMs = naverEditorLoadTimeoutMs;
    }

    public boolean isNaverKeepBrowserOpen() {
        return naverKeepBrowserOpen;
    }

    public void setNaverKeepBrowserOpen(boolean naverKeepBrowserOpen) {
        this.naverKeepBrowserOpen = naverKeepBrowserOpen;
    }

    public String getCodeFile() {
        return codeFile;
    }

    public void setCodeFile(String codeFile) {
        this.codeFile = codeFile;
    }
}
