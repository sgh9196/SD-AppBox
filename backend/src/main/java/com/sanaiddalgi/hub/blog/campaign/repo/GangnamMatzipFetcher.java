package com.sanaiddalgi.hub.blog.campaign.repo;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Repository;

/** 강남맛집.net 체험단 — 전국 /cp/ 목록 + AJAX 무한스크롤 스크래핑. */
@Repository
public class GangnamMatzipFetcher {

    private static final String BASE = "https://xn--939au0g4vj8sq.net";
    private static final String CATEGORY = "20";
    private static final int MAX_PAGES = 100;

    private final CampaignHttpClient http;

    public GangnamMatzipFetcher(CampaignHttpClient http) {
        this.http = http;
    }

    public List<CampaignCard> fetchCampaigns() {
        Set<String> seen = new HashSet<>();
        List<CampaignCard> cards = new ArrayList<>();
        try {
            cards.addAll(parseHtml(http.get(BASE + "/cp/?ca=" + CATEGORY), seen));
            for (int page = 1; page <= MAX_PAGES; page++) {
                String html = http.get(BASE + "/theme/go/_list_cmp_tpl.php?ca=" + CATEGORY + "&rpage=" + page);
                if (html.isBlank() || html.contains("조회된 캠페인이 없습니다")) {
                    break;
                }
                int before = cards.size();
                cards.addAll(parseHtml(html, seen));
                if (cards.size() == before) {
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // skip platform on failure
        }
        return cards;
    }

    List<CampaignCard> parseHtml(String html, Set<String> seen) {
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("li.list_item[data-product]");
        List<CampaignCard> cards = new ArrayList<>();
        for (Element item : items) {
            CampaignCard card = parseCard(item);
            if (card == null || seen.contains(card.getCampaignId())) {
                continue;
            }
            seen.add(card.getCampaignId());
            cards.add(card);
        }
        return cards;
    }

    private CampaignCard parseCard(Element item) {
        if (item.selectFirst("em.blog") == null) {
            return null;
        }
        String campaignId = item.attr("data-product");
        if (campaignId.isBlank()) {
            return null;
        }

        Element titleEl = item.selectFirst("dt.tit a");
        String title = titleEl != null ? titleEl.text().trim() : "";
        if (title.isEmpty()) {
            return null;
        }

        String[] parts = ParseUtils.parseRegionStore(title);
        if (parts == null) {
            return null;
        }
        String region = parts[0];
        String district = parts[1];
        String storeName = parts[2];

        Element benefitEl = item.selectFirst("dd.sub_tit");
        String benefit = benefitEl != null ? benefitEl.text().trim() : "";
        int[] counts = ParseUtils.parseCounts(item.text());

        String deadline = "";
        Element dayEl = item.selectFirst(".day_c");
        if (dayEl != null) {
            deadline = dayEl.text().trim();
        }

        String thumb = "";
        Element img = item.selectFirst("img.thumb_img");
        if (img != null) {
            String src = img.hasAttr("src") ? img.attr("src") : img.attr("data-src");
            thumb = ParseUtils.absUrl(BASE, src);
        }

        CampaignCard card = new CampaignCard();
        card.setPlatform("강남맛집");
        card.setCampaignId(campaignId);
        card.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        card.setStoreName(storeName);
        card.setRegion(region);
        card.setDistrict(district);
        card.setCategory(ParseUtils.inferCategory(title + " " + benefit));
        card.setChannel("블로그");
        card.setBenefit(benefit);
        card.setApplied(counts[0]);
        card.setRecruit(counts[1]);
        card.setDeadline(deadline);
        card.setThumbnailUrl(thumb);
        card.setDetailUrl(BASE + "/cp/?id=" + campaignId);
        return card;
    }
}
