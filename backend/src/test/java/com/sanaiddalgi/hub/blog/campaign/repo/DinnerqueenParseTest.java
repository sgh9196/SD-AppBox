package com.sanaiddalgi.hub.blog.campaign.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DinnerqueenParseTest {

    private DinnerqueenFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new DinnerqueenFetcher(new CampaignHttpClient(null), new ObjectMapper(), Runnable::run);
    }

    @Test
    void skipsReelsAndClips() {
        String html = """
                <a class="qz-dq-card__link" href="/taste/100" title="[릴스] 테스트 신청하기">
                  <div class="qz-dq-card">릴스</div>
                </a>
                <a class="qz-dq-card__link" href="/taste/200" title="[대전 서구] 맛집카페 신청하기">
                  <div class="qz-dq-card">신청 1 / 모집 3 D-5</div>
                </a>
                """;
        Set<String> seen = new HashSet<>();
        List<CampaignCard> cards = fetcher.parsePage(html, seen);
        assertEquals(1, cards.size());
        assertEquals("200", cards.get(0).getCampaignId());
        assertTrue(cards.get(0).getTitle().contains("맛집카페"));
    }

    @Test
    void parsesDaejeonDistrictAndCounts() {
        String html = """
                <a class="qz-dq-card__link" href="/taste/300" title="[대전 유성] 브런치카페 신청하기">
                  <div class="qz-dq-card">신청 4 / 모집 2 D-3</div>
                </a>
                """;
        List<CampaignCard> cards = fetcher.parsePage(html, new HashSet<>());
        assertEquals(1, cards.size());
        CampaignCard card = cards.get(0);
        assertEquals("유성", card.getDistrict());
        assertEquals(4, card.getApplied());
        assertEquals(2, card.getRecruit());
        assertEquals("D-3", card.getDeadline());
    }
}
