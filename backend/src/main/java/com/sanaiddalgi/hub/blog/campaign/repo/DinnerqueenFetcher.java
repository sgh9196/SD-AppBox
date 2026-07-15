package com.sanaiddalgi.hub.blog.campaign.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** dinnerqueen.net 체험단 — taste_list AJAX + 병렬 상세 혜택 조회. */
@Repository
public class DinnerqueenFetcher {

    private static final String BASE = "https://dinnerqueen.net";
    private static final String TASTE_REFERER = BASE + "/taste";
    private static final Pattern SKIP_CHANNEL_RE = Pattern.compile("\\[(릴스|클립|쇬츠|쇼츠|N클립|포토)\\]");
    private static final Pattern BENEFIT_RE = Pattern.compile(
            "제공 내역[\\s\\S]{0,800}?<strong class=\"w-600\">([^<]+)</strong>");
    private static final int MAX_LIST_PAGES = 120;
    private static final int LIST_BATCH_SIZE = 10;
    private static final int BENEFIT_BATCH_SIZE = 20;

    private final CampaignHttpClient http;
    private final ObjectMapper objectMapper;
    private final Executor fetchExecutor;

    public DinnerqueenFetcher(
            CampaignHttpClient http,
            ObjectMapper objectMapper,
            @Qualifier("campaignFetchExecutor") Executor fetchExecutor) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.fetchExecutor = fetchExecutor;
    }

    public List<CampaignCard> fetchCampaigns() {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        List<CampaignCard> cards = Collections.synchronizedList(new ArrayList<>());

        for (int batchStart = 1; batchStart <= MAX_LIST_PAGES; batchStart += LIST_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + LIST_BATCH_SIZE - 1, MAX_LIST_PAGES);
            List<CompletableFuture<TasteListPage>> pageFutures = new ArrayList<>();
            for (int page = batchStart; page <= batchEnd; page++) {
                final int pageNo = page;
                pageFutures.add(CompletableFuture.supplyAsync(
                        () -> fetchTasteListPage(pageNo), fetchExecutor));
            }
            CompletableFuture.allOf(pageFutures.toArray(CompletableFuture[]::new)).join();

            boolean stop = false;
            for (CompletableFuture<TasteListPage> future : pageFutures) {
                TasteListPage result = future.join();
                if (result.layoutHtml().isBlank()) {
                    stop = true;
                    continue;
                }
                cards.addAll(parsePage(result.layoutHtml(), seen));
                if (!result.hasNext()) {
                    stop = true;
                }
            }
            if (stop) {
                break;
            }
        }
        return new ArrayList<>(cards);
    }

    /** 목록에 없는 혜택을 상세 페이지에서 병렬로 채움. cachedBenefits에 있으면 HTTP 생략. */
    public void enrichBenefits(
            List<CampaignCard> cards,
            Consumer<String> log,
            Map<String, String> cachedBenefits) {
        Map<String, String> cache = cachedBenefits != null ? cachedBenefits : Map.of();
        int fromCache = 0;
        List<CampaignCard> targets = new ArrayList<>();
        for (CampaignCard card : cards) {
            String cached = cache.get(card.getCampaignId());
            if (cached != null && !cached.isBlank()) {
                card.setBenefit(cached);
                fromCache++;
            } else if (card.getBenefit() == null || card.getBenefit().isBlank()) {
                targets.add(card);
            }
        }
        final int cachedCount = fromCache;
        if (cachedCount > 0) {
            log.accept("디너의여왕 혜택 캐시 재사용 " + cachedCount + "건");
        }

        int total = targets.size();
        if (total == 0) {
            log.accept("디너의여왕 혜택: 추가 조회 없음");
            return;
        }
        log.accept("디너의여왕 혜택 상세 병렬 조회 (" + total + "건, 동시 " + BENEFIT_BATCH_SIZE + "건)");

        AtomicInteger done = new AtomicInteger();
        AtomicInteger filled = new AtomicInteger(cachedCount);
        final int grandTotal = cachedCount + total;

        for (int batchStart = 0; batchStart < total; batchStart += BENEFIT_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BENEFIT_BATCH_SIZE, total);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = batchStart; i < batchEnd; i++) {
                CampaignCard card = targets.get(i);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        String benefit = parseBenefitFromDetail(http.get(card.getDetailUrl()));
                        if (!benefit.isBlank()) {
                            card.setBenefit(benefit);
                            filled.incrementAndGet();
                        }
                    } catch (RuntimeException ignored) {
                        // skip failed detail
                    }
                    int current = done.incrementAndGet();
                    if (current % 100 == 0 || current == total) {
                        log.accept("디너의여왕 혜택 " + (cachedCount + current) + "/" + grandTotal
                                + " (" + filled.get() + "건 채움)");
                    }
                }, fetchExecutor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
    }

    private TasteListPage fetchTasteListPage(int page) {
        String url = BASE + "/taste/taste_list?ct="
                + "&area1=" + CampaignHttpClient.encode("전국")
                + "&area2=" + CampaignHttpClient.encode("전체")
                + "&page=" + page
                + "&ctype=&query=&sns[]=blog";
        try {
            String json = http.post(url, "", TASTE_REFERER);
            if (json == null || json.isBlank()) {
                return new TasteListPage(false, "");
            }
            JsonNode root = objectMapper.readTree(json);
            String layout = root.path("layout").asText("");
            if (layout.isBlank() || "[]".equals(layout.trim())) {
                return new TasteListPage(false, "");
            }
            return new TasteListPage(root.path("has_next").asBoolean(false), layout);
        } catch (JsonProcessingException | RuntimeException e) {
            return new TasteListPage(false, "");
        }
    }

    private String parseBenefitFromDetail(String html) {
        Matcher match = BENEFIT_RE.matcher(html);
        if (match.find()) {
            return match.group(1).trim();
        }
        Document doc = Jsoup.parse(html);
        for (Element heading : doc.select("h5, strong")) {
            if (!heading.text().contains("제공 내역")) {
                continue;
            }
            Element collapse = heading.closest(".qz-collapse");
            if (collapse == null) {
                continue;
            }
            Element strong = collapse.selectFirst(".qz-collapse__content strong.w-600");
            if (strong != null && !strong.text().isBlank()) {
                return strong.text().trim();
            }
        }
        return "";
    }

    List<CampaignCard> parsePage(String html, Set<String> seen) {
        Document doc = Jsoup.parse(html);
        Elements anchors = doc.select("a.qz-dq-card__link[href*='/taste/']");
        List<CampaignCard> cards = new ArrayList<>();
        for (Element anchor : anchors) {
            CampaignCard card = parseCard(anchor);
            if (card == null || seen.contains(card.getCampaignId())) {
                continue;
            }
            seen.add(card.getCampaignId());
            cards.add(card);
        }
        return cards;
    }

    private CampaignCard parseCard(Element anchor) {
        String href = anchor.attr("href");
        Matcher idMatch = Pattern.compile("/taste/(\\d+)").matcher(href);
        if (!idMatch.find()) {
            return null;
        }

        Element cardDiv = anchor.closest(".qz-dq-card");
        if (isNonBlogCampaign(cardDiv)) {
            return null;
        }

        String[] parsed = parseTitle(anchor.attr("title"));
        if (parsed == null) {
            parsed = parseTitle(anchor.selectFirst("img") != null ? anchor.selectFirst("img").attr("alt") : "");
        }
        if (parsed == null) {
            return null;
        }

        String region = parsed[0];
        String district = parsed[1];
        String storeName = parsed[2];
        String title = parsed[3];
        String campaignId = idMatch.group(1);

        Element cardRoot = cardDiv != null ? cardDiv : anchor.parent();
        String block = cardRoot != null ? cardRoot.text() : anchor.text();
        int[] counts = ParseUtils.parseCounts(block);

        String deadline = "";
        Matcher dMatch = Pattern.compile("D-(\\d+)").matcher(block);
        if (dMatch.find()) {
            deadline = "D-" + dMatch.group(1);
        } else if (block.contains("오늘") && block.contains("마감")) {
            deadline = "오늘 마감";
        }

        String thumb = "";
        Element img = anchor.selectFirst("img");
        if (img != null) {
            String src = img.hasAttr("src") ? img.attr("src") : img.attr("data-src");
            thumb = ParseUtils.absUrl(BASE, src);
        }

        CampaignCard card = new CampaignCard();
        card.setPlatform("디너의여왕");
        card.setCampaignId(campaignId);
        card.setTitle(title);
        card.setStoreName(storeName);
        card.setRegion(region);
        card.setDistrict(district);
        card.setCategory(ParseUtils.inferCategory(title));
        card.setChannel("블로그");
        card.setBenefit("");
        card.setApplied(counts[0]);
        card.setRecruit(counts[1]);
        card.setDeadline(deadline);
        card.setThumbnailUrl(thumb);
        card.setDetailUrl(BASE + "/taste/" + campaignId);
        return card;
    }

    private boolean isNonBlogCampaign(Element cardDiv) {
        if (cardDiv == null) {
            return false;
        }
        if (cardDiv.selectFirst(".qz_b_reels, .qz_b_clip, .qz_b_pandora_box") != null) {
            return true;
        }
        String block = cardDiv.text();
        if (SKIP_CHANNEL_RE.matcher(block).find()) {
            return true;
        }
        return block.contains("배송") && !block.contains("맛집") && !block.contains("카페");
    }

    private String[] parseTitle(String titleAttr) {
        String raw = titleAttr == null ? "" : titleAttr.replace(" 신청하기", "").trim();
        if (raw.isEmpty() || SKIP_CHANNEL_RE.matcher(raw).find()) {
            return null;
        }
        String[] parts = ParseUtils.parseRegionStore(raw);
        if (parts == null) {
            return null;
        }
        String title = "[" + parts[0];
        if (!parts[1].isBlank()) {
            title += " " + parts[1];
        }
        title += "] " + parts[2];
        if (title.length() > 120) {
            title = title.substring(0, 120);
        }
        return new String[] {parts[0], parts[1], parts[2], title};
    }

    private record TasteListPage(boolean hasNext, String layoutHtml) {
    }
}
