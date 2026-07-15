package com.sanaiddalgi.hub.blog.campaign.repo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class FetcherParseSmokeTest {

    @Test
    void parsesDinnerqueenDaejeonSample() throws Exception {
        String html = Files.readString(Path.of("../output/dq_daejeon2.html"));
        DinnerqueenFetcher fetcher = new DinnerqueenFetcher(null, null, Runnable::run);
        int count = fetcher.parsePage(html, new HashSet<>()).size();
        assertTrue(count >= 10, "expected blog campaigns, got " + count);
    }

    @Test
    void parsesGangnamMatzipSample() throws Exception {
        String html = Files.readString(Path.of("../output/gm_cp.html"));
        GangnamMatzipFetcher fetcher = new GangnamMatzipFetcher(null);
        int count = fetcher.parseHtml(html, new HashSet<>()).size();
        assertTrue(count >= 1, "expected gangnam campaigns, got " + count);
    }
}
