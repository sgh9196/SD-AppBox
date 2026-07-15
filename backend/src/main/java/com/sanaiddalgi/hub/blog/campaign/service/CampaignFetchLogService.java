package com.sanaiddalgi.hub.blog.campaign.service;

import com.sanaiddalgi.hub.blog.campaign.model.CampaignFetchLogEntry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** 체험단 수집 진행 로그 — 메모리 링 버퍼. */
@Service
public class CampaignFetchLogService {

    private static final int MAX_ENTRIES = 1000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CopyOnWriteArrayList<CampaignFetchLogEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public void clear() {
        entries.clear();
        nextId.set(1);
    }

    public void info(String message) {
        append("INFO", message);
    }

    public void warn(String message) {
        append("WARN", message);
    }

    private void append(String level, String message) {
        long id = nextId.getAndIncrement();
        String time = LocalTime.now().format(TIME_FMT);
        entries.add(new CampaignFetchLogEntry(id, time, level, message));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public List<CampaignFetchLogEntry> getLogsAfter(long afterId) {
        List<CampaignFetchLogEntry> result = new ArrayList<>();
        for (CampaignFetchLogEntry entry : entries) {
            if (entry.getId() > afterId) {
                result.add(entry);
            }
        }
        return result;
    }

    public long latestId() {
        if (entries.isEmpty()) {
            return 0;
        }
        return entries.get(entries.size() - 1).getId();
    }
}
