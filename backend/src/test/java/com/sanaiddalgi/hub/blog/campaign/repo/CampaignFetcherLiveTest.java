package com.sanaiddalgi.hub.blog.campaign.repo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CampaignFetcherLiveTest {

    @Autowired
    private GabojaFetcher gabojaFetcher;
    @Autowired
    private DinnerqueenFetcher dinnerqueenFetcher;
    @Autowired
    private GangnamMatzipFetcher gangnamMatzipFetcher;

    @Test
    void fetchersReturnCampaigns() {
        List<?> gaboja = gabojaFetcher.fetchCampaigns();
        List<?> dinnerqueen = dinnerqueenFetcher.fetchCampaigns();
        List<?> gangnam = gangnamMatzipFetcher.fetchCampaigns();
        assertTrue(!gaboja.isEmpty() || !dinnerqueen.isEmpty() || !gangnam.isEmpty(),
                "gaboja=" + gaboja.size() + " dq=" + dinnerqueen.size() + " gm=" + gangnam.size());
    }
}
