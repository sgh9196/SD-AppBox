package com.sanaiddalgi.hub.blog.naver.service;

import com.sanaiddalgi.hub.blog.naver.model.NaverLogEntry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** 네이버 Playwright Job 진행 로그. */
@Service
public class NaverLogService {

    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CopyOnWriteArrayList<NaverLogEntry> entries = new CopyOnWriteArrayList<>();
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
        entries.add(new NaverLogEntry(id, LocalTime.now().format(TIME_FMT), level, message));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public List<NaverLogEntry> getLogsAfter(long afterId) {
        List<NaverLogEntry> result = new ArrayList<>();
        for (NaverLogEntry entry : entries) {
            if (entry.getId() > afterId) {
                result.add(entry);
            }
        }
        return result;
    }
}
