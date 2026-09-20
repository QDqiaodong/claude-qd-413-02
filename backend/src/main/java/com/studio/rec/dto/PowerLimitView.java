package com.studio.rec.dto;

/** 功率上限视图：当前生效上限（null 表示未设置）+ 全棚此刻已占合计 */
public class PowerLimitView {
    private Long limit;
    private long inUse;

    public PowerLimitView() {
    }

    public PowerLimitView(Long limit, long inUse) {
        this.limit = limit;
        this.inUse = inUse;
    }

    public Long getLimit() { return limit; }
    public void setLimit(Long limit) { this.limit = limit; }
    public long getInUse() { return inUse; }
    public void setInUse(long inUse) { this.inUse = inUse; }
}
