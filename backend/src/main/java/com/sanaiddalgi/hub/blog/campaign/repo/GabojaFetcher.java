package com.sanaiddalgi.hub.blog.campaign.repo;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Repository;

/** 가보자 체험단 사이트 — 전국 캠페인 스크래핑. */
@Repository
public class GabojaFetcher {

    private static final String BASE = "https://xn--o39a04kpnjo4k9hgflp.com";

    private final CampaignHttpClient http;

    public GabojaFetcher(CampaignHttpClient http) {
        this.http = http;
    }

    public List<CampaignCard> fetchCampaigns() {
        Set<String> seen = new java.util.HashSet<>();
        List<CampaignCard> cards = new ArrayList<>();

        for (String path : new String[] {"/"}) {
            try {
                cards.addAll(parseFragment(http.get(BASE + path), seen));
            } catch (RuntimeException ignored) {
                // skip failed page
            }
        }

        for (String listType : new String[] {"popular", "new", "deadline"}) {
            try {
                String formBody = "page=1&listType=" + listType;
                String ajaxHtml = http.post(
                        BASE + "/main/ajax/_ajax.cmpMainList.php",
                        formBody,
                        BASE + "/");
                cards.addAll(parseFragment(ajaxHtml, seen));
            } catch (RuntimeException ignored) {
                // skip failed ajax
            }
        }
        return cards;
    }

    List<CampaignCard> parseFragment(String html, Set<String> seen) {
        Document doc = Jsoup.parse(html);
        Elements anchors = doc.select("a[href*=\"cmp/?id=\"]");
        List<CampaignCard> cards = new ArrayList<>();
        for (Element anchor : anchors) {
            String href = anchor.attr("href");
            Matcher match = Pattern.compile("id=(\\d+)").matcher(href);
            if (!match.find()) {
                continue;
            }
            String campaignId = match.group(1);
            if (seen.contains(campaignId)) {
                continue;
            }

            Element titleEl = anchor.selectFirst("dt");
            Element benefitEl = anchor.selectFirst("dd");
            String title = titleEl != null ? titleEl.text() : anchor.text();
            String[] storeParts = ParseUtils.parseRegionStore(title);
            if (storeParts == null) {
                continue;
            }

            String block = anchor.text();
            if (!block.contains("블로그") && !anchor.html().toLowerCase().contains("blog")) {
                continue;
            }

            seen.add(campaignId);
            String benefit = benefitEl != null ? benefitEl.text() : "";
            int[] counts = ParseUtils.parseCounts(block);

            String deadline = "";
            for (String label : new String[] {"오늘마감", "마감"}) {
                if (block.contains(label)) {
                    deadline = label;
                    break;
                }
            }
            Matcher days = Pattern.compile("(\\d+)일\\s*남음").matcher(block);
            if (days.find()) {
                deadline = days.group(1) + "일 남음";
            }

            String thumb = "";
            Element img = anchor.selectFirst("img");
            if (img != null && img.hasAttr("src")) {
                thumb = ParseUtils.absUrl(BASE, img.attr("src"));
            }

            CampaignCard card = new CampaignCard();
            card.setPlatform("가보자");
            card.setCampaignId(campaignId);
            card.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
            card.setStoreName(storeParts[2]);
            card.setRegion(storeParts[0]);
            card.setDistrict(storeParts[1]);
            card.setCategory(ParseUtils.inferCategory(title + " " + benefit));
            card.setChannel("블로그");
            card.setBenefit(benefit);
            card.setApplied(counts[0]);
            card.setRecruit(counts[1]);
            card.setDeadline(deadline);
            card.setThumbnailUrl(thumb);
            card.setDetailUrl(BASE + "/cmp/?id=" + campaignId);
            cards.add(card);
        }
        return cards;
    }
}
