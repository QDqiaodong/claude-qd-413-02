package com.studio.rec.dto;

/**
 * 按预约的曲目时长账面：预约分钟（硬额度）、已排分钟（库里全部曲目汇总）、剩余分钟。
 * 页面只做展示，所有数字都来自后台，不在前端做加法。
 */
public class TrackUsageView {
    private Long bookingId;
    private String custName;
    private Integer bookingMinutes;
    private Long scheduledMinutes;
    private Long remainingMinutes;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getCustName() { return custName; }
    public void setCustName(String custName) { this.custName = custName; }
    public Integer getBookingMinutes() { return bookingMinutes; }
    public void setBookingMinutes(Integer bookingMinutes) { this.bookingMinutes = bookingMinutes; }
    public Long getScheduledMinutes() { return scheduledMinutes; }
    public void setScheduledMinutes(Long scheduledMinutes) { this.scheduledMinutes = scheduledMinutes; }
    public Long getRemainingMinutes() { return remainingMinutes; }
    public void setRemainingMinutes(Long remainingMinutes) { this.remainingMinutes = remainingMinutes; }
}
