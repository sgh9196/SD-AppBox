package com.sanaiddalgi.hub.blog.naver.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.sanaiddalgi.hub.config.StudioProperties;
import com.sanaiddalgi.hub.blog.service.DraftSanitizer;
import com.sanaiddalgi.hub.blog.service.SectionTitles;
import com.sanaiddalgi.hub.blog.naver.model.DraftBlock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Playwright로 네이버 로그인·블로그 글쓰기 자동화. */
@Service
public class NaverBlogAutomation {

    private static final String LOGIN_URL = "https://nid.naver.com/nidlogin.login?mode=form";
    private static final String BLOG_HOME_URL = "https://blog.naver.com";
    private static final String BLOG_SECTION_HOME = "https://section.blog.naver.com/BlogHome.naver";

    private static final Set<String> BLOG_HOSTS = Set.of(
            "blog.naver.com",
            "m.blog.naver.com",
            "section.blog.naver.com");
    private static final Set<String> RESERVED_BLOG_PATHS = Set.of(
            "PostWriteForm.naver",
            "PostView.naver",
            "MyBlog.naver",
            "BlogHome.naver",
            "WidgetListAsync.naver",
            "RabbitWrite.naver",
            "PostWriteFormSeOptions.naver");
    private static final Pattern BLOG_ID_JSON = Pattern.compile("\"blogId\"\\s*:\\s*\"([^\"]+)\"");

    private final StudioProperties properties;
    private final NaverSessionService sessionService;
    private final DraftSanitizer draftSanitizer;

    public NaverBlogAutomation(
            StudioProperties properties, NaverSessionService sessionService, DraftSanitizer draftSanitizer) {
        this.properties = properties;
        this.sessionService = sessionService;
        this.draftSanitizer = draftSanitizer;
    }

    /** 브라우저 창을 띄우고 사용자가 직접 로그인할 때까지 대기한 뒤 세션을 저장합니다. */
    public void waitForBrowserLogin(Consumer<String> log) throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright, false);
            BrowserContext context = newBrowserContext(browser, null);
            Page page = context.newPage();
            openLoginPage(page, log);
            waitForManualLoginComplete(context, page, log, properties.getNaverLoginTimeoutMs());
            persistSession(context, page, log);
            browser.close();
        }
    }

    /** 브라우저에서 수동 로그인 후 같은 세션으로 블로그 임시저장까지 진행합니다. */
    public String loginInBrowserAndPublish(
            String title, List<DraftBlock> blocks, Consumer<String> log) throws Exception {
        Playwright playwright = Playwright.create();
        Browser browser = launchBrowser(playwright, false);
        try {
            BrowserContext context = newBrowserContext(browser, null);
            Page page = context.newPage();
            openLoginPage(page, log);
            waitForManualLoginComplete(context, page, log, properties.getNaverLoginTimeoutMs());
            persistSession(context, page, log);
            log.accept("로그인 확인 — 블로그 작성 시작");
            String url = fillAndSaveDraft(context, page, title, blocks, log);
            persistSession(context, page, log);
            releaseBrowserAfterWrite(playwright, browser, false, log);
            return url;
        } catch (Exception e) {
            closeBrowserQuietly(browser, playwright);
            throw e;
        }
    }

    public String publish(String title, List<DraftBlock> blocks, Consumer<String> log) throws Exception {
        if (!sessionService.hasSessionFile()) {
            throw new IllegalStateException("네이버 로그인 세션이 없습니다. 먼저 로그인 창을 열어 주세요.");
        }
        log.accept("저장된 세션으로 브라우저 실행");
        boolean headless = properties.isNaverPlaywrightHeadless();
        Playwright playwright = Playwright.create();
        Browser browser = launchBrowser(playwright, headless);
        try {
            BrowserContext context = newBrowserContext(browser, sessionService.sessionFile());
            Page page = context.newPage();
            String url = fillAndSaveDraft(context, page, title, blocks, log);
            persistSession(context, page, log);
            releaseBrowserAfterWrite(playwright, browser, headless, log);
            return url;
        } catch (Exception e) {
            closeBrowserQuietly(browser, playwright);
            throw e;
        }
    }

    private void releaseBrowserAfterWrite(
            Playwright playwright, Browser browser, boolean headless, Consumer<String> log) {
        if (!headless && properties.isNaverKeepBrowserOpen()) {
            log.accept("작성 완료 — 브라우저 창에서 직접 발행해 주세요. 확인 후 창을 닫으면 됩니다.");
            return;
        }
        closeBrowserQuietly(browser, playwright);
    }

    private void closeBrowserQuietly(Browser browser, Playwright playwright) {
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (RuntimeException ignored) {
            // already closed
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (RuntimeException ignored) {
            // already closed
        }
    }

    private void openLoginPage(Page page, Consumer<String> log) {
        log.accept("Chrome 창이 열렸습니다 — 네이버 로그인을 직접 완료해 주세요");
        log.accept("캡차·2단계 인증이 있어도 브라우저 창에서 처리하면 됩니다");
        page.navigate(LOGIN_URL);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(1_000);
    }

    private String fillAndSaveDraft(
            BrowserContext context, Page page, String title, List<DraftBlock> blocks, Consumer<String> log)
            throws Exception {
        navigateToWriteForm(page, log);
        log.accept("에디터 준비 중… (현재 URL: " + page.url() + ")");

        if (isLoginPage(page.url())) {
            throw new IllegalStateException("세션이 만료되었습니다. 다시 로그인 창을 열어 주세요.");
        }
        if (isBlogAccessErrorPage(page)) {
            saveDebugScreenshot(page, "write-access-error");
            throw new IllegalStateException(
                    "블로그 글쓰기 페이지 접근 실패. 네이버 블로그가 개설되어 있는지, 블로그 ID가 맞는지 확인하세요.");
        }

        log.accept("팝업·도움말 처리 중…");
        dismissShellPopups(page, log);
        log.accept("에디터 영역 탐색");
        Frame editor = resolveEditorFrameWithFallback(page, log);
        dismissPopupsWithRetry(page, editor, log);

        dismissHelpPanelEverywhere(page, log);
        log.accept("제목 입력: " + title);
        fillTitle(editor, page, title, log);

        log.accept("본문 입력 영역 준비");
        prepareBodyForInput(editor, page, log);
        dismissPopupsWithRetry(page, editor, log);
        moveFocusToBody(editor, page, log);

        log.accept("본문 블록 " + blocks.size() + "개 입력 시작");
        long textBlocks = blocks.stream().filter(b -> b.getType() == DraftBlock.Type.TEXT).count();
        log.accept("텍스트 " + textBlocks + "개 · 이미지 " + (blocks.size() - textBlocks) + "개");
        if (textBlocks == 0) {
            log.accept("경고: 텍스트 블록이 없습니다. 원고 내용을 확인하세요.");
        }
        ensureBodyCursorAtEnd(page);
        boolean firstTextBlock = true;
        List<DraftBlock> blockList = blocks;
        for (int i = 0; i < blockList.size(); i++) {
            DraftBlock block = blockList.get(i);
            int index = i + 1;
            if (block.getType() == DraftBlock.Type.IMAGE) {
                log.accept("[" + index + "/" + blockList.size() + "] 이미지 업로드");
                uploadImage(editor, page, block.getImagePath(), log);
                if (i + 1 < blockList.size()) {
                    DraftBlock next = blockList.get(i + 1);
                    if (next.getType() == DraftBlock.Type.TEXT && next.isTightAfterPhoto()) {
                        String caption = draftSanitizer.sanitizeForNaverEditor(next.getText());
                        String preview = caption;
                        if (preview != null && preview.length() > 40) {
                            preview = preview.substring(0, 40) + "…";
                        }
                        log.accept("[" + (index + 1) + "/" + blockList.size() + "] 사진 설명: " + preview);
                        fillImageCaption(editor, page, caption, log);
                        firstTextBlock = false;
                        i++;
                    }
                }
                continue;
            }
            String preview = block.getText();
            if (preview != null && preview.length() > 40) {
                preview = preview.substring(0, 40) + "…";
            }
            log.accept("[" + index + "/" + blockList.size() + "] 문단 입력 (" + block.getTextRole() + "): " + preview);
            boolean afterPlaceInfo = i > 0
                    && blockList.get(i - 1).getType() == DraftBlock.Type.TEXT
                    && blockList.get(i - 1).getTextRole() == DraftBlock.TextRole.PLACE_INFO;
            typeTextBlock(editor, page, block, firstTextBlock, afterPlaceInfo, log);
            firstTextBlock = false;
        }

        log.accept("임시저장 클릭");
        clickTempSave(editor, page);
        page.waitForTimeout(3_000);
        log.accept("임시저장 완료");
        return page.url();
    }

    private void navigateToWriteForm(Page page, Consumer<String> log) throws Exception {
        String blogId = resolveBlogId(page, log, false);
        if (blogId != null) {
            openWriteUrl(page, blogId, log);
            if (!isBlogAccessErrorPage(page)) {
                return;
            }
            log.accept("blogId가 유효하지 않아 재조회합니다: " + blogId);
        }

        blogId = resolveBlogId(page, log, true);
        if (blogId != null) {
            openWriteUrl(page, blogId, log);
            if (!isBlogAccessErrorPage(page)) {
                sessionService.saveBlogId(blogId);
                return;
            }
        }

        log.accept("글쓰기 버튼으로 이동 시도");
        page.navigate(BLOG_SECTION_HOME);
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30_000));
        Locator writeLink = page.locator("a[href*='PostWriteForm'], a[href*='Redirect=Write']");
        if (writeLink.count() == 0) {
            saveDebugScreenshot(page, "write-link-missing");
            throw new IllegalStateException(
                    "블로그 ID를 찾을 수 없습니다. 네이버 블로그를 먼저 개설했는지 확인하세요.");
        }
        writeLink.first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(60_000));

        String resolved = extractBlogIdFromUrl(page.url());
        if (resolved == null) {
            resolved = extractBlogIdFromPage(page);
        }
        if (resolved != null) {
            sessionService.saveBlogId(resolved);
            log.accept("블로그 ID 확인: " + resolved);
        }
    }

    private void openWriteUrl(Page page, String blogId, Consumer<String> log) {
        for (String url : buildWriteUrls(blogId)) {
            log.accept("글쓰기 URL 시도: " + url);
            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout(60_000));
                waitForPageSettle(page, log);
                log.accept("페이지 로드 완료: " + page.url());
                if (isLoginPage(page.url())) {
                    return;
                }
                if (!isBlogAccessErrorPage(page)) {
                    waitForWriteShell(page, log);
                    log.accept("글쓰기 페이지 진입 완료");
                    return;
                }
                log.accept("글쓰기 접근 오류 — 다른 URL 시도");
            } catch (PlaywrightException e) {
                log.accept("페이지 로드 실패: " + shortPlaywrightMessage(e));
            }
        }
    }

    private List<String> buildWriteUrls(String blogId) {
        String encoded = URLEncoder.encode(blogId, StandardCharsets.UTF_8);
        return List.of(
                "https://blog.naver.com/" + blogId + "/postwrite",
                "https://blog.naver.com/PostWriteForm.naver?blogId=" + encoded
                        + "&Redirect=Write&categoryNo=0&redirect=Write&widgetTypeCall=true&directAccess=false",
                "https://blog.naver.com/" + blogId + "?Redirect=Write&widgetTypeCall=true&directAccess=false");
    }

    private void dismissShellPopups(Page page, Consumer<String> log) {
        dismissPopupsWithRetry(page, page.mainFrame(), log);
    }

    /** postwrite SPA / PostWriteForm iframe 등 글쓰기 껍데기가 뜰 때까지 짧게 대기 */
    private void waitForWriteShell(Page page, Consumer<String> log) {
        String url = page.url();
        try {
            if (url.contains("/postwrite")) {
                page.locator(".se-section-documentTitle, .se-title-text, .se-documentTitle")
                        .first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(20_000));
                return;
            }
            if (url.contains("PostWriteForm")) {
                page.locator("iframe#mainFrame, iframe[name='mainFrame'], iframe")
                        .first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.ATTACHED)
                                .setTimeout(20_000));
            }
        } catch (PlaywrightException e) {
            log.accept("글쓰기 UI 대기 생략 — 에디터 탐색에서 재시도");
        }
    }

    private Frame resolveEditorFrameWithFallback(Page page, Consumer<String> log) throws Exception {
        try {
            return resolveEditorFrame(page, log);
        } catch (IllegalStateException first) {
            String blogId = sessionService.getBlogId();
            if (!isValidBlogId(blogId)) {
                throw first;
            }
            String url = page.url();
            if (url.contains("/postwrite")) {
                log.accept("postwrite 에디터 미발견 — PostWriteForm URL 재시도");
                page.navigate(
                        "https://blog.naver.com/PostWriteForm.naver?blogId="
                                + URLEncoder.encode(blogId, StandardCharsets.UTF_8)
                                + "&Redirect=Write&categoryNo=0&redirect=Write&widgetTypeCall=true&directAccess=false",
                        new Page.NavigateOptions().setTimeout(60_000));
            } else {
                log.accept("에디터 미발견 — postwrite URL 재시도");
                page.navigate(
                        "https://blog.naver.com/" + blogId + "/postwrite",
                        new Page.NavigateOptions().setTimeout(60_000));
            }
            waitForPageSettle(page, log);
            waitForWriteShell(page, log);
            dismissShellPopups(page, log);
            return resolveEditorFrame(page, log);
        }
    }

    /** NETWORKIDLE은 네이버에서 끝나지 않는 경우가 많아 DOM/LOAD만 대기 */
    private void waitForPageSettle(Page page, Consumer<String> log) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30_000));
        try {
            page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(15_000));
        } catch (PlaywrightException e) {
            log.accept("LOAD 대기 생략 (DOMContentLoaded만 사용)");
        }
        page.waitForTimeout(2_000);
        if (page.url().contains("/postwrite")) {
            page.waitForTimeout(2_000);
        }
    }

    private String shortPlaywrightMessage(PlaywrightException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "알 수 없는 오류";
        }
        int cut = msg.indexOf("Call log:");
        if (cut > 0) {
            msg = msg.substring(0, cut).trim();
        }
        return msg.length() > 120 ? msg.substring(0, 120) + "…" : msg;
    }

    private String resolveBlogId(Page page, Consumer<String> log, boolean forceRefresh) throws Exception {
        if (!forceRefresh) {
            String cached = sessionService.getBlogId();
            if (isValidBlogId(cached)) {
                log.accept("저장된 블로그 ID 사용: " + cached);
                return cached;
            }
            if (cached != null && !cached.isBlank()) {
                log.accept("저장된 blogId가 유효하지 않아 재조회합니다: " + cached);
            }
        }

        log.accept("블로그 ID 조회 중…");
        String[][] targets = {
            {BLOG_SECTION_HOME, "블로그 홈"},
            {BLOG_HOME_URL + "/MyBlog.naver", "내 블로그"},
            {BLOG_HOME_URL, "블로그 메인"},
        };

        for (String[] target : targets) {
            log.accept(target[1] + " 확인");
            page.navigate(target[0]);
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30_000));
            if (isLoginPage(page.url())) {
                throw new IllegalStateException("세션이 만료되었습니다. 다시 로그인 창을 열어 주세요.");
            }
            String id = extractBlogIdFromPage(page);
            if (id != null) {
                log.accept("블로그 ID 확인: " + id);
                sessionService.saveBlogId(id);
                return id;
            }
        }
        return null;
    }

    private String extractBlogIdFromPage(Page page) {
        String fromWriteLink = firstBlogIdFromLinks(page, "a[href*='PostWriteForm'][href*='blogId=']");
        if (fromWriteLink != null) {
            return fromWriteLink;
        }

        String fromWriteRedirect = firstBlogIdFromLinks(
                page, "a[href*='blog.naver.com/'][href*='Redirect=Write']");
        if (fromWriteRedirect != null) {
            return fromWriteRedirect;
        }

        String fromProfile = firstBlogIdFromLinks(
                page,
                "a[href*='://blog.naver.com/']:not([href*='PostWriteForm']):not([href*='PostView'])");
        if (fromProfile != null) {
            return fromProfile;
        }

        String content = safePageAction(Page::content, page, "");
        Matcher matcher = BLOG_ID_JSON.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (isValidBlogId(id)) {
                return id;
            }
        }

        return extractBlogIdFromUrl(page.url());
    }

    private String firstBlogIdFromLinks(Page page, String selector) {
        List<String> hrefs = safePageAction(p -> {
            List<String> list = new ArrayList<>();
            Locator links = p.locator(selector);
            int count = links.count();
            for (int i = 0; i < count && i < 40; i++) {
                String href = links.nth(i).getAttribute("href");
                if (href != null && !href.isBlank()) {
                    list.add(href);
                }
            }
            return list;
        }, page, List.of());

        for (String href : hrefs) {
            if (isNidHost(href)) {
                continue;
            }
            String id = extractBlogIdFromUrl(href);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private String extractBlogIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!isBlogHost(host)) {
                return null;
            }

            String query = uri.getRawQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("blogId=")) {
                        String id = URLDecoder.decode(part.substring(7), StandardCharsets.UTF_8);
                        if (isValidBlogId(id)) {
                            return id;
                        }
                    }
                }
            }

            if ("section.blog.naver.com".equalsIgnoreCase(host)) {
                return null;
            }

            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                String segment = path.startsWith("/") ? path.substring(1) : path;
                int slash = segment.indexOf('/');
                if (slash > 0) {
                    segment = segment.substring(0, slash);
                }
                if (isValidBlogId(segment)) {
                    return segment;
                }
            }
        } catch (RuntimeException ignored) {
            // try next strategy
        }
        return null;
    }

    private boolean isBlogHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase();
        return BLOG_HOSTS.contains(normalized);
    }

    private boolean isNidHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && host.toLowerCase().contains("nid.naver.com");
        } catch (RuntimeException e) {
            return url.contains("nid.naver.com");
        }
    }

    private boolean isValidBlogId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (RESERVED_BLOG_PATHS.contains(id)) {
            return false;
        }
        String lower = id.toLowerCase();
        if (lower.contains("nidlogin")
                || lower.contains("logout")
                || lower.contains("login")
                || lower.contains("naver")
                || lower.contains("section")) {
            return false;
        }
        if (id.contains(".")) {
            return false;
        }
        return id.matches("[a-zA-Z0-9_-]{3,30}");
    }

    private boolean isBlogAccessErrorPage(Page page) {
        String content = safePageAction(Page::content, page, "");
        return content.contains("유효하지 않은 요청")
                || content.contains("블로그 아이디가 없습니다")
                || content.contains("접근하고자하는 블로그 아이디");
    }

    private BrowserContext newBrowserContext(Browser browser, Path sessionPath) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(1400, 900)
                .setLocale("ko-KR")
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        if (sessionPath != null) {
            options.setStorageStatePath(sessionPath);
        }
        BrowserContext context = browser.newContext(options);
        int actionMs = properties.getNaverEditorActionTimeoutMs();
        context.setDefaultTimeout(actionMs);
        context.setDefaultNavigationTimeout(Math.max(actionMs * 3, 60_000));
        return context;
    }

    private void persistSession(BrowserContext context, Page page, Consumer<String> log) throws Exception {
        Path sessionPath = sessionService.sessionFile();
        java.nio.file.Files.createDirectories(sessionPath.getParent());
        context.storageState(new BrowserContext.StorageStateOptions().setPath(sessionPath));
        String blogId = resolveBlogId(page, log, sessionService.getBlogId().isBlank());
        String naverId = detectNaverId(page, blogId);
        sessionService.saveMeta(naverId, blogId != null ? blogId : "");
        log.accept("로그인 세션 저장 완료"
                + (blogId != null && !blogId.isBlank() ? " (blogId=" + blogId + ")" : ""));
    }

    private String detectNaverId(Page page, String blogId) {
        if (isValidBlogId(blogId)) {
            return blogId;
        }
        String fromPage = firstBlogIdFromLinks(page, "a[href*='://blog.naver.com/']");
        if (fromPage != null) {
            return fromPage;
        }
        return "네이버";
    }

    private void waitForManualLoginComplete(
            BrowserContext context, Page page, Consumer<String> log, int timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean captchaHintLogged = false;
        boolean waitingLogged = false;

        while (System.currentTimeMillis() < deadline) {
            if (hasNaverAuthCookie(context)) {
                log.accept("네이버 로그인 확인됨");
                settleAfterNavigation(page);
                verifyBlogAccess(page, log);
                return;
            }

            if (isPageNavigating(page)) {
                page.waitForTimeout(1_500);
                continue;
            }

            boolean manualAuth = safePageAction(p -> needsManualAuth(p), page, false);
            if (manualAuth && !captchaHintLogged) {
                log.accept("추가 인증 화면 — 브라우저 창에서 계속 진행해 주세요");
                captchaHintLogged = true;
            } else if (!waitingLogged) {
                String url = safePageAction(Page::url, page, "");
                if (!url.isBlank() && !isLoginPage(url)) {
                    log.accept("로그인 처리 중…");
                    waitingLogged = true;
                }
            }

            page.waitForTimeout(2_000);
        }

        if (hasNaverAuthCookie(context)) {
            log.accept("네이버 로그인 확인됨");
            verifyBlogAccess(page, log);
            return;
        }

        saveDebugScreenshot(page, "login-timeout");
        throw new IllegalStateException(
                "로그인 시간 초과(" + (timeoutMs / 1000) + "초). "
                        + "브라우저 창에서 로그인을 완료했는지 확인하세요.");
    }

    private void verifyBlogAccess(Page page, Consumer<String> log) {
        log.accept("블로그 접속으로 로그인 재확인");
        page.navigate(BLOG_HOME_URL + "/MyBlog.naver");
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30_000));
        if (isLoginPage(page.url())) {
            saveDebugScreenshot(page, "login-verify-failed");
            throw new IllegalStateException("로그인 후에도 블로그 접속이 거부되었습니다. 다시 시도해 주세요.");
        }
        String blogId = extractBlogIdFromUrl(page.url());
        if (blogId == null) {
            blogId = extractBlogIdFromPage(page);
        }
        if (blogId != null) {
            try {
                sessionService.saveBlogId(blogId);
            } catch (Exception ignored) {
                // optional during verify
            }
            log.accept("블로그 ID 확인: " + blogId);
        }
    }

    private void settleAfterNavigation(Page page) {
        safePageAction(() -> page.waitForLoadState(
                LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10_000)));
        page.waitForTimeout(500);
    }

    private boolean hasNaverAuthCookie(BrowserContext context) {
        try {
            for (Cookie cookie : context.cookies()) {
                if ("NID_AUT".equals(cookie.name) || "NID_SES".equals(cookie.name)) {
                    return true;
                }
            }
        } catch (PlaywrightException ignored) {
            // context may be resetting during navigation
        }
        return false;
    }

    private boolean isPageNavigating(Page page) {
        try {
            page.evaluate("() => document.readyState");
            return false;
        } catch (PlaywrightException e) {
            return isNavigationError(e);
        }
    }

    private boolean isLoginPage(String url) {
        return url != null && url.contains("nid.naver.com") && url.contains("nidlogin");
    }

    private boolean needsManualAuth(Page page) {
        if (safeCount(page, "#captcha, .captcha, iframe[title*='캡']") > 0) {
            return true;
        }
        if (safeCount(page, "input[type='tel'], input[name*='otp']") > 0) {
            return true;
        }
        String body = safePageAction(Page::content, page, "").toLowerCase();
        return body.contains("captcha")
                || body.contains("자동입력 방지")
                || body.contains("otp")
                || body.contains("2-step")
                || body.contains("본인확인")
                || body.contains("새로운 기기");
    }

    private int safeCount(Page page, String selector) {
        Integer count = safePageAction(p -> p.locator(selector).count(), page, null);
        return count != null ? count : 0;
    }

    @FunctionalInterface
    private interface PageAction<T> {
        T run(Page page);
    }

    private <T> T safePageAction(PageAction<T> action, Page page, T fallback) {
        try {
            return action.run(page);
        } catch (PlaywrightException e) {
            if (isNavigationError(e)) {
                return fallback;
            }
            throw e;
        }
    }

    private void safePageAction(Runnable action) {
        try {
            action.run();
        } catch (PlaywrightException e) {
            if (!isNavigationError(e)) {
                throw e;
            }
        }
    }

    private boolean isNavigationError(PlaywrightException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("Execution context was destroyed")
                || msg.contains("frame was detached")
                || msg.contains("Target page, context or browser has been closed")
                || msg.contains("Navigation failed")
                || msg.contains("net::ERR");
    }

    private void saveDebugScreenshot(Page page, String name) {
        try {
            Path dir = sessionService.sessionFile().getParent();
            if (dir != null) {
                java.nio.file.Files.createDirectories(dir);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(dir.resolve(name + ".png"))
                        .setFullPage(true));
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            // debug only
        }
    }

    private Browser launchBrowser(Playwright playwright, boolean headless) {
        return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(properties.getNaverPlaywrightSlowMoMs())
                .setArgs(List.of(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--no-sandbox",
                        "--disable-gpu",
                        "--disable-software-rasterizer",
                        "--js-flags=--max-old-space-size=128"
                )));
    }

    /** SmartEditor iframe 또는 postwrite SPA 프레임 탐색 */
    private Frame resolveEditorFrame(Page page, Consumer<String> log) {
        int loadTimeout = properties.getNaverEditorLoadTimeoutMs();
        boolean postWriteSpa = page.url().contains("/postwrite");
        log.accept("에디터 탐색 시작…" + (postWriteSpa ? " (postwrite SPA)" : ""));

        long deadline = System.currentTimeMillis() + loadTimeout;
        long waitedMs = 0;
        while (System.currentTimeMillis() < deadline) {
            Frame frame = findEditorFrame(page, log);
            if (frame != null) {
                String label = frame.name();
                if (label == null || label.isBlank()) {
                    label = frame.equals(page.mainFrame()) ? "main(page)" : frame.url();
                }
                log.accept("에디터 프레임 확인: " + label);
                return frame;
            }
            page.waitForTimeout(400);
            waitedMs += 400;
            if (waitedMs % 4_000 == 0) {
                dismissShellPopups(page, log);
                logEditorProbe(page, log, waitedMs / 1000);
            }
        }
        logEditorProbe(page, log, loadTimeout / 1000);
        saveDebugScreenshot(page, "editor-frame-missing");
        throw new IllegalStateException(
                "네이버 에디터를 찾을 수 없습니다. 글쓰기 페이지가 완전히 로드된 뒤 다시 시도해 주세요.");
    }

    private void logEditorProbe(Page page, Consumer<String> log, long seconds) {
        log.accept("에디터 로딩 대기 중… (" + seconds + "초, URL=" + page.url() + ")");
        try {
            int iframeCount = page.locator("iframe").count();
            log.accept("  iframe 태그 " + iframeCount + "개, Playwright 프레임 " + page.frames().size() + "개");
            for (int i = 0; i < Math.min(iframeCount, 5); i++) {
                String src = page.locator("iframe").nth(i).getAttribute("src");
                String id = page.locator("iframe").nth(i).getAttribute("id");
                String name = page.locator("iframe").nth(i).getAttribute("name");
                log.accept("  iframe[" + i + "] id=" + id + " name=" + name + " src=" + shorten(src, 80));
            }
        } catch (PlaywrightException ignored) {
            // probe only
        }
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private Frame findEditorFrame(Page page, Consumer<String> log) {
        boolean postWriteSpa = page.url().contains("/postwrite");
        if (postWriteSpa) {
            Frame main = page.mainFrame();
            if (frameHasEditor(main, true)) {
                return main;
            }
        }

        for (String name : List.of("mainFrame", "main")) {
            Frame named = page.frame(name);
            if (named != null && frameHasEditor(named, false)) {
                return named;
            }
        }

        try {
            Locator iframes = page.locator("iframe");
            int count = iframes.count();
            for (int i = 0; i < count; i++) {
                ElementHandle handle = iframes.nth(i).elementHandle();
                if (handle == null) {
                    continue;
                }
                Frame content;
                try {
                    content = handle.contentFrame();
                } finally {
                    handle.dispose();
                }
                if (content != null && frameHasEditor(content, false)) {
                    if (log != null) {
                        String src = iframes.nth(i).getAttribute("src");
                        log.accept("에디터 iframe 매칭: index=" + i + " src=" + shorten(src, 80));
                    }
                    return content;
                }
            }
        } catch (PlaywrightException ignored) {
            // try frames list
        }

        for (Frame child : page.frames()) {
            if (child.equals(page.mainFrame())) {
                continue;
            }
            if (frameHasEditor(child, false)) {
                return child;
            }
        }

        if (frameHasEditor(page.mainFrame(), postWriteSpa)) {
            return page.mainFrame();
        }
        return null;
    }

    private Frame findEditorFrame(Page page) {
        return findEditorFrame(page, null);
    }

    private boolean frameHasEditor(Frame frame, boolean titleOnly) {
        try {
            Locator title = frame.locator(
                    ".se-documentTitle, .se-title-text, .se-section-documentTitle, "
                            + ".se-section-documentTitle .se-text-paragraph, "
                            + "div[placeholder*='제목']");
            if (title.count() == 0 || !title.first().isVisible()) {
                return false;
            }
            if (titleOnly) {
                return true;
            }
            Locator body = frame.locator(
                    ".se-main-container, .se-section-text, .se-canvas-bottom, "
                            + ".se-text-paragraph-text[contenteditable], "
                            + ".se-section-text [contenteditable]");
            return body.count() > 0 && body.first().isVisible();
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** 글쓰기 진입 시 도움말·작성 중인 글 복원 등 팝업 닫기 (있을 때만) */
    private void dismissPopupsWithRetry(Page page, Frame editor, Consumer<String> log) {
        for (int attempt = 0; attempt < 5; attempt++) {
            boolean closed = dismissHelpPanelEverywhere(page, log);
            closed |= dismissPopups(page.mainFrame(), page, log);
            closed |= dismissPopups(editor, page, log);
            for (Frame frame : page.frames()) {
                if (frame.equals(page.mainFrame()) || frame.equals(editor)) {
                    continue;
                }
                closed |= dismissPopups(frame, page, log);
            }
            boolean overlayVisible =
                    isHelpPanelVisibleAnywhere(page) || isRestoreDraftDialogVisible(page);
            if (!overlayVisible) {
                break;
            }
            if (!closed && attempt >= 1) {
                break;
            }
            page.waitForTimeout(300);
        }
        if (isHelpPanelVisibleAnywhere(page) || isRestoreDraftDialogVisible(page)) {
            page.keyboard().press("Escape");
            page.waitForTimeout(200);
        }
    }

    private boolean dismissHelpPanelEverywhere(Page page, Consumer<String> log) {
        boolean closed = false;
        for (Frame frame : page.frames()) {
            closed |= dismissHelpPanel(frame, page, log);
        }
        if (!closed && isHelpPanelVisibleAnywhere(page)) {
            log.accept("도움말 패널 감지 — Esc로 닫기 시도");
            page.keyboard().press("Escape");
            page.waitForTimeout(300);
        }
        return closed;
    }

    private boolean isHelpPanelVisibleAnywhere(Page page) {
        for (Frame frame : page.frames()) {
            try {
                Locator panel = frame.locator(".se-help-panel, [class*='help-panel']");
                if (panel.count() > 0 && panel.first().isVisible()) {
                    return true;
                }
            } catch (PlaywrightException ignored) {
                // next frame
            }
        }
        return false;
    }

    /** SmartEditor ONE 우측 도움말 패널 닫기 */
    private boolean dismissHelpPanel(Frame frame, Page page, Consumer<String> log) {
        String[] closeSelectors = {
                ".se-help-panel-close-button",
                "button.se-help-panel-close-button",
                ".se-help-panel .se-help-panel-close-button",
                ".se-help-panel button[class*='close']",
                ".se-help-panel button[aria-label*='닫기']",
                ".se-help-panel button[aria-label*='닫']",
                ".se-help-panel button:has-text('닫기')",
                ".se-help-panel button:has-text('×')",
                ".se-help-panel-close",
                ".se-help-panel-header button",
        };
        boolean closedAny = false;
        for (String selector : closeSelectors) {
            Locator buttons = frame.locator(selector);
            int count = buttons.count();
            for (int i = 0; i < count; i++) {
                Locator btn = buttons.nth(i);
                if (!btn.isVisible()) {
                    continue;
                }
                try {
                    btn.click(new Locator.ClickOptions().setTimeout(3_000).setForce(true));
                    log.accept("도움말 패널 닫기: " + selector);
                    closedAny = true;
                    page.waitForTimeout(400);
                } catch (PlaywrightException ignored) {
                    // try next
                }
            }
        }
        try {
            Locator neverAgain = frame.locator(
                    ".se-help-panel button:has-text('다시'), "
                            + ".se-help-panel label:has-text('다시')");
            if (neverAgain.count() > 0 && neverAgain.first().isVisible()) {
                neverAgain.first().click(new Locator.ClickOptions().setTimeout(2_000).setForce(true));
                log.accept("도움말 '다시 보지 않기' 선택");
            }
        } catch (PlaywrightException ignored) {
            // optional
        }
        return closedAny;
    }

    private boolean isRestoreDraftDialogVisible(Page page) {
        for (Frame frame : page.frames()) {
            try {
                if (frameHasRestoreDraftDialog(frame)) {
                    return true;
                }
            } catch (PlaywrightException ignored) {
                // next frame
            }
        }
        return false;
    }

    private boolean frameHasRestoreDraftDialog(Frame frame) {
        try {
            Locator dialogs = frame.locator(".se-popup, .se-dialog");
            int count = Math.min(dialogs.count(), 4);
            for (int i = 0; i < count; i++) {
                Locator dlg = dialogs.nth(i);
                if (!dlg.isVisible()) {
                    continue;
                }
                if (dlg.locator(".se-popup-button-cancel, button:has-text('취소'), button:has-text('새로 작성')")
                                .count()
                        > 0) {
                    return true;
                }
            }
        } catch (PlaywrightException ignored) {
            // optional
        }
        return false;
    }

    /** '작성 중인 글 복원' 다이얼로그가 보일 때만 취소/새로 작성 클릭 */
    private boolean dismissRestoreDraftDialog(Frame frame, Page page, Consumer<String> log) {
        if (!frameHasRestoreDraftDialog(frame)) {
            return false;
        }
        String[] roots = {".se-popup", ".se-dialog"};
        for (String root : roots) {
            Locator dialogs = frame.locator(root);
            int count = dialogs.count();
            for (int i = 0; i < count; i++) {
                Locator dlg = dialogs.nth(i);
                if (!dlg.isVisible()) {
                    continue;
                }
                String[] buttons = {
                    "button:has-text('취소')",
                    "button.se-popup-button-cancel",
                    ".se-popup-button-cancel",
                    "button:has-text('새로 작성')",
                    "button:has-text('새로')",
                };
                for (String btnSel : buttons) {
                    Locator btn = dlg.locator(btnSel);
                    if (btn.count() == 0 || !btn.first().isVisible()) {
                        continue;
                    }
                    try {
                        btn.first().click(new Locator.ClickOptions().setTimeout(3_000).setForce(true));
                        log.accept("작성 중인 글 복원 팝업 — " + btnSel + " 선택");
                        page.waitForTimeout(500);
                        return true;
                    } catch (PlaywrightException ignored) {
                        // try next button
                    }
                }
            }
        }
        return false;
    }

    private boolean dismissPopups(Frame editor, Page page, Consumer<String> log) {
        if (dismissRestoreDraftDialog(editor, page, log)) {
            return true;
        }
        String[] selectors = {
                // 작성 중인 글 복원 — 새 글 작성을 위해 '취소' 또는 '새로 작성' 선택
                ".se-popup button:has-text('취소')",
                ".se-popup-button-cancel",
                "button.se-popup-button-cancel",
                ".se-popup button:has-text('새로 작성')",
                ".se-popup button:has-text('새로')",
                ".se-dialog button:has-text('취소')",
                ".se-dialog button:has-text('새로 작성')",
                ".se-dialog button:has-text('새로 작성하기')",
                // 기타 팝업
                ".se-popup button:has-text('닫기')",
                ".se_popup button.btn_cancel",
                "button.se-popup-close-button",
        };
        boolean closedAny = false;
        for (String selector : selectors) {
            Locator btn = editor.locator(selector);
            if (btn.count() == 0 || !btn.first().isVisible()) {
                continue;
            }
            try {
                btn.first().click(new Locator.ClickOptions().setTimeout(3_000));
                log.accept("팝업 닫기: " + selector);
                closedAny = true;
                page.waitForTimeout(500);
            } catch (PlaywrightException ignored) {
                // next selector
            }
        }
        return closedAny;
    }

    private void fillTitle(Frame editor, Page page, String title, Consumer<String> log) {
        int timeout = properties.getNaverEditorActionTimeoutMs();
        Locator titleEditable = findTitleLocator(editor, page);
        titleEditable.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(20_000));
        String safe = draftSanitizer.sanitizeForNaverEditor(title);
        writeToEditable(page, titleEditable, safe, timeout, log, true);
        if (!titleContainsText(editor, page, safe)) {
            saveDebugScreenshot(page, "title-input-failed");
            throw new IllegalStateException("제목 입력에 실패했습니다.");
        }
        log.accept("제목 입력 확인");
    }

    private Locator findTitleLocator(Frame editor, Page page) {
        String selector =
                ".se-section-documentTitle [contenteditable='true'], "
                        + ".se-documentTitle [contenteditable='true'], "
                        + ".se-section-documentTitle .se-text-paragraph-text, "
                        + ".se-title-text[contenteditable='true'], "
                        + ".se-title-text";
        Locator inEditor = editor.locator(selector);
        if (inEditor.count() > 0) {
            return inEditor.first();
        }
        return page.locator(selector).first();
    }

    private Locator findBodyLocator(Frame editor, Page page, boolean last) {
        Locator paragraphs = bodyParagraphs(editor, page);
        int paraCount = paragraphs.count();
        if (paraCount > 0) {
            Locator para = last ? paragraphs.nth(paraCount - 1) : paragraphs.first();
            Locator editable = para.locator(
                    ".se-text-paragraph-text[contenteditable], "
                            + ".se-text-paragraph-text, [contenteditable]");
            if (editable.count() > 0) {
                return editable.last();
            }
            return para;
        }
        if (!last) {
            Locator picked = pickBodyEditableLocator(editor, page);
            if (picked.count() > 0) {
                return picked;
            }
        }
        String selector =
                ".se-section-text .se-text-paragraph-text, "
                        + ".se-section-text [contenteditable], "
                        + ".se-section-text .se-text-paragraph, "
                        + ".se-main-container .se-text-paragraph-text, "
                        + ".se-main-container .se-component.se-text .se-text-paragraph-text, "
                        + ".se-canvas-bottom .se-text-paragraph-text";
        Locator inEditor = editor.locator(selector);
        int editorCount = inEditor.count();
        if (editorCount > 0) {
            return last ? inEditor.nth(editorCount - 1) : inEditor.first();
        }
        Locator onPage = page.locator(selector);
        int pageCount = onPage.count();
        if (pageCount > 0) {
            return last ? onPage.nth(pageCount - 1) : onPage.first();
        }
        Locator fallback = editor.locator(".se-section-text, .se-main-container .se-component.se-text");
        if (fallback.count() > 0) {
            return last ? fallback.last() : fallback.first();
        }
        return page.locator(".se-section-text, .se-main-container").first();
    }

    /** 제목 영역 제외, 화면에서 가장 큰 본문 편집 노드 */
    private Locator pickBodyEditableLocator(Frame editor, Page page) {
        try {
            Object ok = page.evaluate(
                    "() => {"
                            + "document.querySelectorAll('[data-naver-body-target]').forEach("
                            + "  el => el.removeAttribute('data-naver-body-target'));"
                            + "const titleRoot = document.querySelector("
                            + "  '.se-section-documentTitle, .se-documentTitle');"
                            + "const candidates = [];"
                            + "document.querySelectorAll("
                            + "  '.se-text-paragraph-text, [contenteditable], .se-text-paragraph'"
                            + ").forEach(el => {"
                            + "  if (titleRoot && titleRoot.contains(el)) return;"
                            + "  const section = el.closest('.se-section-text, .se-main-container, "
                            + "    .se-canvas-bottom');"
                            + "  if (!section) return;"
                            + "  const r = el.getBoundingClientRect();"
                            + "  if (r.width < 8 || r.height < 8) return;"
                            + "  candidates.push({ el, area: r.width * r.height });"
                            + "});"
                            + "if (!candidates.length) return false;"
                            + "candidates.sort((a, b) => b.area - a.area);"
                            + "candidates[0].el.setAttribute('data-naver-body-target', '1');"
                            + "return true;"
                            + "}");
            if (Boolean.TRUE.equals(ok)) {
                return page.locator("[data-naver-body-target='1']");
            }
            if (!editor.equals(page.mainFrame())) {
                ok = editor.evaluate(
                        "() => {"
                                + "document.querySelectorAll('[data-naver-body-target]').forEach("
                                + "  el => el.removeAttribute('data-naver-body-target'));"
                                + "const titleRoot = document.querySelector("
                                + "  '.se-section-documentTitle, .se-documentTitle');"
                                + "const candidates = [];"
                                + "document.querySelectorAll("
                                + "  '.se-text-paragraph-text, [contenteditable], .se-text-paragraph'"
                                + ").forEach(el => {"
                                + "  if (titleRoot && titleRoot.contains(el)) return;"
                                + "  const section = el.closest('.se-section-text, .se-main-container');"
                                + "  if (!section) return;"
                                + "  const r = el.getBoundingClientRect();"
                                + "  if (r.width < 8 || r.height < 8) return;"
                                + "  candidates.push({ el, area: r.width * r.height });"
                                + "});"
                                + "if (!candidates.length) return false;"
                                + "candidates.sort((a, b) => b.area - a.area);"
                                + "candidates[0].el.setAttribute('data-naver-body-target', '1');"
                                + "return true;"
                                + "}");
                if (Boolean.TRUE.equals(ok)) {
                    return editor.locator("[data-naver-body-target='1']");
                }
            }
        } catch (PlaywrightException ignored) {
            // fallback to css selectors
        }
        return page.locator("[data-naver-body-target='1']");
    }

    /** @param replaceAll true=제목(전체 교체), false=본문(커서 위치에 이어 쓰기) */
    private void writeToEditable(
            Page page, Locator editable, String text, int timeoutMs, Consumer<String> log, boolean replaceAll) {
        if (text == null || text.isBlank()) {
            return;
        }
        editable.scrollIntoViewIfNeeded();
        clickLocatorSafely(editable, timeoutMs);
        page.waitForTimeout(120);
        try {
            if (replaceAll) {
                editable.press("Control+A", new Locator.PressOptions().setTimeout(3_000));
                page.waitForTimeout(60);
            } else {
                placeCursorAtEndOf(editable);
            }
            editable.pressSequentially(
                    text,
                    new Locator.PressSequentiallyOptions().setDelay(8).setTimeout(timeoutMs));
            page.waitForTimeout(120);
            return;
        } catch (PlaywrightException first) {
            log.accept("pressSequentially 실패 — DOM 입력 시도");
        }
        insertTextViaDomOnLocator(editable, text, replaceAll);
        page.waitForTimeout(120);
    }

    private void placeCursorAtEndOf(Locator editable) {
        editable.evaluate(
                "el => {"
                        + "const node = el.closest('[contenteditable]')"
                        + "  || el.querySelector('[contenteditable]')"
                        + "  || el.closest('.se-text-paragraph-text')"
                        + "  || el.querySelector('.se-text-paragraph-text')"
                        + "  || el;"
                        + "node.focus();"
                        + "const sel = window.getSelection();"
                        + "const range = document.createRange();"
                        + "range.selectNodeContents(node);"
                        + "range.collapse(false);"
                        + "sel.removeAllRanges();"
                        + "sel.addRange(range);"
                        + "}");
    }

    private void insertTextViaDomOnLocator(Locator editable, String text, boolean replaceAll) {
        editable.evaluate(
                "(el, args) => {"
                        + "const value = args.value;"
                        + "const replaceAll = args.replaceAll;"
                        + "const node = el.closest('[contenteditable]')"
                        + "  || el.querySelector('[contenteditable]')"
                        + "  || el.closest('.se-text-paragraph-text')"
                        + "  || el.querySelector('.se-text-paragraph-text')"
                        + "  || el.closest('.se-text-paragraph')"
                        + "  || el;"
                        + "node.focus();"
                        + "const sel = window.getSelection();"
                        + "const range = document.createRange();"
                        + "if (replaceAll) {"
                        + "  range.selectNodeContents(node);"
                        + "  range.collapse(true);"
                        + "} else {"
                        + "  range.selectNodeContents(node);"
                        + "  range.collapse(false);"
                        + "}"
                        + "sel.removeAllRanges();"
                        + "sel.addRange(range);"
                        + "if (!document.execCommand('insertText', false, value)) {"
                        + "  if (replaceAll) {"
                        + "    node.textContent = value;"
                        + "  } else {"
                        + "    node.textContent = (node.textContent || '') + value;"
                        + "  }"
                        + "}"
                        + "node.dispatchEvent(new InputEvent('input', { bubbles: true, data: value }));"
                        + "node.dispatchEvent(new Event('change', { bubbles: true }));"
                        + "}",
                java.util.Map.of("value", text, "replaceAll", replaceAll));
    }

    private void prepareBodyForInput(Frame editor, Page page, Consumer<String> log) {
        log.accept("제목 → 본문 Tab 이동");
        page.keyboard().press("Tab");
        page.waitForTimeout(120);
        page.keyboard().press("Tab");
        page.waitForTimeout(200);

        int timeoutMs = 20_000;
        long deadline = System.currentTimeMillis() + timeoutMs;
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            if (hasBodyInputTarget(editor, page)) {
                log.accept("본문 입력 칸 확인");
                return;
            }
            attempt++;
            activateBodyArea(editor, page, log, attempt);
            dismissHelpPanelEverywhere(page, log);
            page.waitForTimeout(350);
        }

        Locator bodySection = page.locator(".se-section-text");
        if (bodySection.count() > 0 && bodySection.first().isVisible()) {
            log.accept("본문 섹션 확인 — 입력 진행");
            return;
        }
        Locator inEditor = editor.locator(".se-section-text");
        if (inEditor.count() > 0 && inEditor.first().isVisible()) {
            log.accept("본문 섹션 확인(iframe) — 입력 진행");
            return;
        }

        saveDebugScreenshot(page, "body-editable-missing");
        throw new IllegalStateException(
                "본문 입력 영역을 찾을 수 없습니다. 글쓰기 화면이 완전히 로드됐는지 확인하세요.");
    }

    private boolean hasBodyInputTarget(Frame editor, Page page) {
        if (pickBodyEditableLocator(editor, page).count() > 0) {
            return true;
        }
        String[] selectors = {
            ".se-section-text .se-text-paragraph-text",
            ".se-section-text .se-text-paragraph",
            ".se-section-text [contenteditable]",
            ".se-main-container .se-text-paragraph-text",
            ".se-main-container .se-component.se-text",
        };
        for (String selector : selectors) {
            Locator onPage = page.locator(selector);
            if (onPage.count() > 0 && onPage.first().isVisible()) {
                return true;
            }
            Locator inEditor = editor.locator(selector);
            if (inEditor.count() > 0 && inEditor.first().isVisible()) {
                return true;
            }
        }
        return false;
    }

    private int countBodyEditables(Frame editor, Page page) {
        return hasBodyInputTarget(editor, page) ? 1 : 0;
    }

    private void activateBodyArea(Frame editor, Page page, Consumer<String> log, int attempt) {
        String[] selectors = {
            ".se-section-text .se-text-paragraph-text",
            ".se-section-text .se-text-paragraph",
            ".se-section-text .se-placeholder",
            ".se-section-text",
            ".se-main-container .se-component.se-text",
            ".se-canvas-bottom",
        };
        for (String selector : selectors) {
            Locator onPage = page.locator(selector);
            if (tryActivateBodyClick(page, onPage, log, selector, attempt >= 3)) {
                return;
            }
            if (!editor.equals(page.mainFrame())) {
                tryActivateBodyClick(page, editor.locator(selector), log, selector, attempt >= 3);
            }
        }
        if (attempt % 4 == 0) {
            page.keyboard().press("Tab");
            page.waitForTimeout(150);
        }
    }

    private boolean tryActivateBodyClick(
            Page page, Locator locators, Consumer<String> log, String selector, boolean dblClick) {
        int count = locators.count();
        for (int i = 0; i < count; i++) {
            Locator target = locators.nth(i);
            if (!target.isVisible()) {
                continue;
            }
            try {
                if (dblClick && selector.contains("placeholder")) {
                    target.dblclick(new Locator.DblclickOptions().setTimeout(3_000).setForce(true));
                } else {
                    target.click(new Locator.ClickOptions().setTimeout(3_000).setForce(true));
                }
                page.waitForTimeout(250);
                log.accept("본문 영역 클릭: " + selector);
                return true;
            } catch (PlaywrightException ignored) {
                // try next element
            }
        }
        return false;
    }

    private void moveFocusToBody(Frame editor, Page page, Consumer<String> log) {
        int timeout = properties.getNaverEditorActionTimeoutMs();
        Locator body = findBodyLocator(editor, page, false);
        clickLocatorSafely(body, timeout);
        page.waitForTimeout(150);
        placeCursorAtEnd(editor, page);
        log.accept("본문 포커스 이동 완료");
    }

    private boolean titleContainsText(Frame editor, Page page, String title) {
        if (title == null || title.isBlank()) {
            return true;
        }
        String snippet = title.length() > 20 ? title.substring(0, 20) : title;
        try {
            Locator onPage = page.locator(
                    ".se-section-documentTitle, .se-documentTitle, .se-title-text, .pcol1 textarea");
            if (onPage.filter(new Locator.FilterOptions().setHasText(snippet)).count() > 0) {
                return true;
            }
            Locator inEditor = editor.locator(
                    ".se-section-documentTitle, .se-documentTitle, .se-title-text, .pcol1 textarea");
            return inEditor.filter(new Locator.FilterOptions().setHasText(snippet)).count() > 0;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private Locator activeBodyEditable(Frame editor, Page page) {
        return findBodyLocator(editor, page, true);
    }

    /** 본문 입력 중 DOM 클릭 없이 문서 끝으로 커서 이동 */
    private void ensureBodyCursorAtEnd(Page page) {
        page.keyboard().press("End");
        page.waitForTimeout(40);
        page.keyboard().press("Control+End");
        page.waitForTimeout(80);
    }

    private void placeCursorAtEnd(Frame editor, Page page) {
        try {
            placeCursorAtEndOf(findBodyLocator(editor, page, true));
        } catch (PlaywrightException ignored) {
            // optional
        }
    }

    /** 툴바·저장 버튼은 iframe 밖(page 루트)에 있는 경우가 많음 */
    private Locator locateToolbar(Frame editor, Page page, String selectors) {
        Locator onPage = page.locator(selectors);
        int pageCount = onPage.count();
        for (int i = 0; i < pageCount; i++) {
            Locator candidate = onPage.nth(i);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        Locator inEditor = editor.locator(selectors);
        int editorCount = inEditor.count();
        for (int i = 0; i < editorCount; i++) {
            Locator candidate = inEditor.nth(i);
            if (candidate.isVisible()) {
                return candidate;
            }
        }
        if (pageCount > 0) {
            return onPage.first();
        }
        return inEditor.first();
    }

    private void clickLocatorSafely(Locator locator, int timeoutMs) {
        locator.scrollIntoViewIfNeeded();
        try {
            locator.click(new Locator.ClickOptions().setTimeout(timeoutMs));
        } catch (PlaywrightException first) {
            locator.click(new Locator.ClickOptions().setTimeout(timeoutMs).setForce(true));
        }
    }

    private Locator bodyParagraphs(Frame editor, Page page) {
        Locator combined = editor.locator(
                ".se-main-container .se-text .se-text-paragraph, "
                        + ".se-main-container .se-module-text .se-text-paragraph, "
                        + ".se-section-text .se-text-paragraph");
        if (combined.count() > 0) {
            return combined;
        }
        return page.locator(
                ".se-main-container .se-text .se-text-paragraph, "
                        + ".se-section-text .se-text-paragraph");
    }

    private void typeTextBlock(
            Frame editor, Page page, DraftBlock block, boolean firstInBody, boolean afterPlaceInfo,
            Consumer<String> log) {
        String safe = draftSanitizer.sanitizeForNaverEditor(SectionTitles.resolve(block.getText()));
        if (safe.isBlank()) {
            return;
        }
        switch (block.getTextRole()) {
            case INTRO -> typeIntroBlock(editor, page, safe, firstInBody, log);
            case SECTION_TITLE -> typeSectionTitleBlock(editor, page, safe, firstInBody, log);
            case PLACE_INFO -> typePlaceInfoBlock(editor, page, safe, firstInBody, log);
            default -> {
                boolean prepend = !firstInBody && !block.isTightAfterPhoto() && !afterPlaceInfo;
                insertPlainParagraph(editor, page, safe, prepend, log);
            }
        }
    }

    /** 인트로 직후 빈 줄 1개 */
    private void typeIntroBlock(
            Frame editor, Page page, String text, boolean firstInBody, Consumer<String> log) {
        insertPlainParagraph(editor, page, text, !firstInBody, log);
        ensureBodyCursorAtEnd(page);
        page.keyboard().press("Enter");
        page.waitForTimeout(150);
    }

    /** 섹션(카테고리) 제목 앞 빈 줄 2개 */
    private void typeSectionTitleBlock(
            Frame editor, Page page, String text, boolean firstInBody, Consumer<String> log) {
        if (!firstInBody) {
            ensureBodyCursorAtEnd(page);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
        }
        clearStrikethroughFormat(editor, page);
        ensureBodyCursorAtEnd(page);
        insertSectionHeaderText(page, text);
        page.waitForTimeout(100);
        stripStrikethroughFromLastBodyParagraph(editor, page);
        ensureBodyCursorAtEnd(page);
        page.keyboard().press("Enter");
        page.waitForTimeout(150);
    }

    /** 섹션 카테고리 제목(🏠 장소 분위기 등) — 이모지와 제목 분리 입력 */
    private void insertSectionHeaderText(Page page, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        SectionTitles.SectionHeader header = SectionTitles.parseHeader(text);
        if (header.hasEmoji()) {
            page.keyboard().insertText(header.emoji());
            page.waitForTimeout(80);
            if (!header.label().isBlank()) {
                page.keyboard().insertText(" " + header.label());
            }
            return;
        }
        insertTextWithInlineEmojis(page, text);
    }

    /**
     * 본문·사진설명: 문장 안 이모지는 텍스트와 분리해 순서대로 입력 (한꺼번에 넣으면 이모지만 남음).
     */
    private void insertNaverEditorText(Page page, String text) {
        insertTextWithInlineEmojis(page, text);
    }

    private void insertTextWithInlineEmojis(Page page, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (isEmojiCodePoint(codePoint)) {
                if (!plain.isEmpty()) {
                    page.keyboard().insertText(plain.toString());
                    plain.setLength(0);
                    page.waitForTimeout(40);
                }
                page.keyboard().insertText(new String(Character.toChars(codePoint)));
                page.waitForTimeout(50);
            } else {
                plain.appendCodePoint(codePoint);
            }
            i += charCount;
        }
        if (!plain.isEmpty()) {
            page.keyboard().insertText(plain.toString());
        }
    }

    private boolean isEmojiCodePoint(int codePoint) {
        if (codePoint >= 0x1F300 && codePoint <= 0x1FAFF) {
            return true;
        }
        if (codePoint >= 0x2600 && codePoint <= 0x27BF) {
            return true;
        }
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    /** 장소 정보: 첫 줄에 📍 장소 정보, 항목은 Shift+Enter로 줄바꿈 */
    private void typePlaceInfoBlock(
            Frame editor, Page page, String text, boolean firstInBody, Consumer<String> log) {
        if (!firstInBody) {
            ensureBodyCursorAtEnd(page);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
        }
        clearStrikethroughFormat(editor, page);
        ensureBodyCursorAtEnd(page);

        List<String> lines = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                page.keyboard().press("Shift+Enter");
                page.waitForTimeout(80);
            }
            if (i == 0 && SectionTitles.hasLeadingSectionEmoji(lines.get(i))) {
                insertSectionHeaderText(page, lines.get(i));
            } else {
                insertNaverEditorText(page, lines.get(i));
            }
            page.waitForTimeout(80);
        }
        if (!lines.isEmpty()) {
            stripStrikethroughFromLastBodyParagraph(editor, page);
            ensureBodyCursorAtEnd(page);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
            page.keyboard().press("Enter");
            page.waitForTimeout(150);
        }
    }

    /** 일반 문단 — 커서 끝에 이어 쓰기 */
    private void insertPlainParagraph(
            Frame editor, Page page, String text, boolean prependNewParagraph, Consumer<String> log) {
        if (prependNewParagraph) {
            ensureBodyCursorAtEnd(page);
            page.keyboard().press("Enter");
            page.waitForTimeout(120);
        }
        typeAtBodyCursor(editor, page, text, log);
    }

    /** 본문 텍스트를 한 번에 삽입 (글자 단위 입력·DOM 클릭 없음) */
    private void typeAtBodyCursor(Frame editor, Page page, String text, Consumer<String> log) {
        if (text == null || text.isBlank()) {
            return;
        }
        clearStrikethroughFormat(editor, page);
        ensureBodyCursorAtEnd(page);
        insertNaverEditorText(page, text);
        page.waitForTimeout(100);
        stripStrikethroughFromLastBodyParagraph(editor, page);
    }

    private void stripStrikethroughFromLastBodyParagraph(Frame editor, Page page) {
        Locator paragraph = bodyParagraphs(editor, page);
        if (paragraph.count() == 0) {
            return;
        }
        paragraph.last().evaluate(
                "el => {"
                        + "const root = el.closest('.se-text-paragraph') || el;"
                        + "root.querySelectorAll('s, strike, del').forEach(node => {"
                        + "  const t = node.textContent || '';"
                        + "  node.replaceWith(document.createTextNode(t));"
                        + "});"
                        + "root.querySelectorAll('*').forEach(node => {"
                        + "  if (node.style) node.style.textDecoration = 'none';"
                        + "});"
                        + "root.normalize();"
                        + "}");
    }

    /** 네이버 SE: 취소선이 켜져 있을 때만 해제 (strikeThrough는 토글이라 무조건 호출하면 켜짐). */
    private void clearStrikethroughFormat(Frame editor, Page page) {
        String toolbarSelectors =
                "button[data-name='strikeThrough'], button.se-strikethrough-toolbar-button, "
                        + "button[aria-label*='취소선'], button[title*='취소선']";
        Locator btn = locateToolbar(editor, page, toolbarSelectors);
        try {
            if (btn.count() > 0) {
                String pressed = btn.getAttribute("aria-pressed");
                String clazz = btn.getAttribute("class");
                if ("true".equals(pressed) || (clazz != null && clazz.contains("active"))) {
                    btn.click(new Locator.ClickOptions().setTimeout(2_000));
                    page.waitForTimeout(100);
                }
            }
        } catch (RuntimeException ignored) {
            // optional toolbar sync
        }
        try {
            Locator targets = editor.locator(
                    ".se-main-container .se-text-paragraph-text, "
                            + ".se-main-container .se-text-paragraph, "
                            + ".se-main-container .se-component-content[contenteditable='true']");
            if (targets.count() == 0) {
                return;
            }
            targets.last().evaluate(
                    "el => {"
                            + "const node = el.isContentEditable ? el"
                            + "  : el.querySelector('[contenteditable=\"true\"]') || el;"
                            + "if (!node) return;"
                            + "node.focus();"
                            + "if (document.queryCommandState('strikeThrough')) {"
                            + "  document.execCommand('strikeThrough', false, null);"
                            + "}"
                            + "}");
        } catch (PlaywrightException ignored) {
            // focus optional
        }
        page.waitForTimeout(80);
    }

    private boolean paragraphContainsText(Frame editor, Page page, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String snippet = text.length() > 24 ? text.substring(0, 24) : text;
        try {
            Locator inEditor = editor.locator(
                    ".se-section-text, .se-main-container .se-text-paragraph, "
                            + ".se-main-container .se-module-text, .se-section-text .se-text-paragraph");
            if (inEditor.filter(new Locator.FilterOptions().setHasText(snippet)).count() > 0) {
                return true;
            }
            Locator onPage = page.locator(
                    ".se-section-text, .se-main-container .se-text-paragraph, .se-section-text .se-text-paragraph");
            return onPage.filter(new Locator.FilterOptions().setHasText(snippet)).count() > 0;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private void uploadImage(Frame editor, Page page, String imagePath, Consumer<String> log) {
        ensureBodyCursorAtEnd(page);
        String imageSelectors =
                "button[data-name='image'], button.se-toolbar-icon-image, "
                        + "button[aria-label*='사진'], .se-image-toolbar-button";
        Locator imageBtn = locateToolbar(editor, page, imageSelectors);
        imageBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15_000));
        FileChooser chooser = page.waitForFileChooser(() -> imageBtn.click());
        chooser.setFiles(Path.of(imagePath));
        Locator uploaded = editor.locator(".se-image, .se-component-image, img.se-image-resource");
        if (uploaded.count() == 0) {
            uploaded = page.locator(".se-image, .se-component-image, img.se-image-resource");
        }
        uploaded.last().waitFor(new Locator.WaitForOptions().setTimeout(60_000));
        page.waitForTimeout(1_000);
        log.accept("이미지 반영 대기 완료");
    }

    /** 업로드 직후 이미지 캡션란(사진 설명)에 텍스트 입력 */
    private void fillImageCaption(Frame editor, Page page, String text, Consumer<String> log) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (tryFillCaptionOnLastImageModule(editor, page, text, log)) {
            return;
        }
        String[] selectors = {
            ".se-module-image:last-child .se-caption-text",
            ".se-module-image:last-child .se-module-text [contenteditable='true']",
            ".se-component.se-image:last-child .se-module-text [contenteditable='true']",
            ".se-module-image:last-child .se-placeholder",
            ".se-image-caption:last-child [contenteditable='true']",
        };
        for (String selector : selectors) {
            if (tryFillImageCaption(editor, page, selector, text, log)) {
                return;
            }
        }
        try {
            Locator placeholder = page.getByText("사진 설명을 입력하세요.").last();
            if (placeholder.count() > 0 && placeholder.isVisible()) {
                placeholder.click();
                page.waitForTimeout(150);
                insertNaverEditorText(page, text);
                page.waitForTimeout(100);
                log.accept("사진 설명 입력(placeholder)");
                return;
            }
        } catch (PlaywrightException ignored) {
            // fallback below
        }
        log.accept("사진 설명 칸 미발견 — 본문에 이어 붙임");
        typeAtBodyCursor(editor, page, text, log);
    }

    private boolean tryFillCaptionOnLastImageModule(
            Frame editor, Page page, String text, Consumer<String> log) {
        for (Locator root : List.of(page.locator(".se-module-image, .se-component.se-image"), editor.locator(".se-module-image, .se-component.se-image"))) {
            try {
                if (root.count() == 0) {
                    continue;
                }
                Locator lastImage = root.last();
                lastImage.scrollIntoViewIfNeeded();
                String[] innerSelectors = {
                    ".se-module-text .se-text-paragraph-text",
                    ".se-module-text [contenteditable='true']",
                    ".se-caption-text",
                    ".se-placeholder",
                };
                for (String inner : innerSelectors) {
                    Locator candidates = lastImage.locator(inner);
                    int count = candidates.count();
                    for (int i = count - 1; i >= 0; i--) {
                        Locator cap = candidates.nth(i);
                        if (!cap.isVisible()) {
                            continue;
                        }
                        cap.click(new Locator.ClickOptions().setTimeout(3_000));
                        page.waitForTimeout(150);
                        insertNaverEditorText(page, text);
                        page.waitForTimeout(100);
                        log.accept("사진 설명 입력(마지막 이미지 모듈)");
                        return true;
                    }
                }
            } catch (PlaywrightException ignored) {
                // try next root
            }
        }
        return false;
    }

    private boolean tryFillImageCaption(
            Frame editor, Page page, String selector, String text, Consumer<String> log) {
        for (Locator source : List.of(page.locator(selector), editor.locator(selector))) {
            try {
                if (source.count() == 0) {
                    continue;
                }
                Locator target = source.last();
                if (!target.isVisible()) {
                    continue;
                }
                target.scrollIntoViewIfNeeded();
                target.click(new Locator.ClickOptions().setTimeout(3_000));
                page.waitForTimeout(120);
                insertNaverEditorText(page, text);
                page.waitForTimeout(100);
                log.accept("사진 설명 입력: " + selector);
                return true;
            } catch (PlaywrightException ignored) {
                // try next locator
            }
        }
        return false;
    }

    private void clickTempSave(Frame editor, Page page) {
        String saveSelectors =
                "button:has-text('임시저장'), .save_btn__bzc5B, button[data-click-area='tpb.save']";
        Locator saveBtn = page.locator(saveSelectors);
        if (saveBtn.count() == 0 || !saveBtn.first().isVisible()) {
            saveBtn = editor.locator(saveSelectors);
        }
        saveBtn.first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        saveBtn.first().click();
    }
}
