package com.studio.rec.dto;

/**
 * 一个预约的曲目分钟账面：预约分钟（起止时段硬额度）、已排分钟、剩余分钟。
 * 数字一律由后台按库内当下数据汇总，前端刷新后与此一致。
 */
public class TrackBudgetView {
    private Long bookingId;
    private Integer bookingMin;
    private long scheduledMin;
    private long remainingMin;

    public TrackBudgetView() {
    }

    public TrackBudgetView(Long bookingId, Integer bookingMin, long scheduledMin, long remainingMin) {
        this.bookingId = bookingId;
        this.bookingMin = bookingMin;
        this.scheduledMin = scheduledMin;
        this.remainingMin = remainingMin;
    }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Integer getBookingMin() { return bookingMin; }
    public void setBookingMin(Integer bookingMin) { this.bookingMin = bookingMin; }
    public long getScheduledMin() { return scheduledMin; }
    public void setScheduledMin(long scheduledMin) { this.scheduledMin = scheduledMin; }
    public long getRemainingMin() { return remainingMin; }
    public void setRemainingMin(long remainingMin) { this.remainingMin = remainingMin; }
}
