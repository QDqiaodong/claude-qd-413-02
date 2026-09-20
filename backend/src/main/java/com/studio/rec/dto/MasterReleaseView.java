package com.studio.rec.dto;

import com.studio.rec.entity.MasterRelease;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 放行单详情：单据本身 + 勾入的齐件曲目快照（曲目信息在出参时实时查库）。
 */
public class MasterReleaseView {
    private Long id;
    private String releaseNo;
    private Long bookingId;
    private String bookingStatus;
    private String custName;
    private Long roomId;
    private String roomCode;
    private String roomName;
    private String roomStatus;
    private String status;
    private String failReason;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ItemView> items;

    public static class ItemView {
        private Long itemId;
        private Long trackId;
        private String name;
        private Integer duration;
        private Integer rating;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public Long getTrackId() { return trackId; }
        public void setTrackId(Long trackId) { this.trackId = trackId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReleaseNo() { return releaseNo; }
    public void setReleaseNo(String releaseNo) { this.releaseNo = releaseNo; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }
    public String getCustName() { return custName; }
    public void setCustName(String custName) { this.custName = custName; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomStatus() { return roomStatus; }
    public void setRoomStatus(String roomStatus) { this.roomStatus = roomStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ItemView> getItems() { return items; }
    public void setItems(List<ItemView> items) { this.items = items; }

    public static MasterReleaseView of(MasterRelease r) {
        MasterReleaseView v = new MasterReleaseView();
        v.setId(r.getId());
        v.setReleaseNo(r.getReleaseNo());
        v.setBookingId(r.getBookingId());
        v.setStatus(r.getStatus());
        v.setFailReason(r.getFailReason());
        v.setVersion(r.getVersion());
        v.setCreatedAt(r.getCreatedAt());
        v.setUpdatedAt(r.getUpdatedAt());
        return v;
    }
}
