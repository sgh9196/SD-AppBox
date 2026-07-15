package com.sanaiddalgi.hub.blog.campaign.model;

import java.util.List;

/** 체험단 목록 페이징 응답. */
public class CampaignPageResult {

    private int page;
    private int size;
    private long total;
    private List<CampaignCard> items;
    /** 수집 진행 중 미리보기 응답이면 true */
    private boolean partial;

    public CampaignPageResult() {
    }

    public CampaignPageResult(int page, int size, long total, List<CampaignCard> items) {
        this(page, size, total, items, false);
    }

    public CampaignPageResult(int page, int size, long total, List<CampaignCard> items, boolean partial) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.items = items;
        this.partial = partial;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<CampaignCard> getItems() {
        return items;
    }

    public void setItems(List<CampaignCard> items) {
        this.items = items;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }
}
