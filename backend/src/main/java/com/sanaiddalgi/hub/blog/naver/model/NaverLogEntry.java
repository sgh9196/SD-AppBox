package com.sanaiddalgi.hub.blog.naver.model;

/** 네이버 Job 로그 1줄. */
public class NaverLogEntry {

    private long id;
    private String time;
    private String level;
    private String message;

    public NaverLogEntry() {
    }

    public NaverLogEntry(long id, String time, String level, String message) {
        this.id = id;
        this.time = time;
        this.level = level;
        this.message = message;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
