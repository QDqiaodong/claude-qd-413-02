package com.studio.rec.dto;

import java.util.List;

/**
 * 开单 / 更新齐件入参。
 * trackIds 为勾进来的齐件曲目；开单时至少两首，且必须都已打星、归属该预约。
 */
public class MasterReleaseRequest {
    private Long bookingId;
    private List<Long> trackIds;

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public List<Long> getTrackIds() { return trackIds; }
    public void setTrackIds(List<Long> trackIds) { this.trackIds = trackIds; }
}
