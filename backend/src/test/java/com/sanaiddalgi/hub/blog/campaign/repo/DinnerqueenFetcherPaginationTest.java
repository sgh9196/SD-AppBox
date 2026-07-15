package com.sanaiddalgi.hub.blog.campaign.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanaiddalgi.hub.blog.campaign.model.CampaignCard;
import java.util.List;
import org.junit.jupiter.api.Test;

class DinnerqueenFetcherPaginationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void continuesAcrossPagesWhenFirstPageIsFiltered() throws Exception {
        ObjectNode page1 = objectMapper.createObjectNode();
        page1.put("has_next", true);
        page1.put("layout", """
                <a class="qz-dq-card__link" href="/taste/1" title="[릴스] 클립 신청하기">
                  <div class="qz-dq-card"></div>
                </a>
                """);

        ObjectNode page2 = objectMapper.createObjectNode();
        page2.put("has_next", false);
        page2.put("layout", """
                <a class="qz-dq-card__link" href="/taste/2" title="[대전 서구] 맛집 신청하기">
                  <div class="qz-dq-card">신청 1 / 모집 1</div>
                </a>
                """);

        DinnerqueenFetcher fetcher = new DinnerqueenFetcher(
                new CampaignHttpClient(null) {
                    private int page = 0;

                    @Override
                    public String post(String url, String formBody, String referer) {
                        page++;
                        return page == 1 ? page1.toString() : page2.toString();
                    }
                },
                objectMapper,
                Runnable::run);

        List<CampaignCard> cards = fetcher.fetchCampaigns();
        assertEquals(1, cards.size());
        assertEquals("2", cards.get(0).getCampaignId());
    }

    @Test
    void stopsWhenLayoutIsEmpty() {
        DinnerqueenFetcher fetcher = new DinnerqueenFetcher(
                new CampaignHttpClient(null) {
                    @Override
                    public String post(String url, String formBody, String referer) {
                        return "{\"has_next\":false,\"layout\":\"\"}";
                    }
                },
                objectMapper,
                Runnable::run);
        assertTrue(fetcher.fetchCampaigns().isEmpty());
    }
}
