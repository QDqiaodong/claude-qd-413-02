package com.studio.rec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "cust_name", nullable = false)
    private String custName;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    @Column(name = "status", nullable = false)
    private String status;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
    public String getCustName() { return custName; }
    public void setCustName(String custName) { this.custName = custName; }
    public Integer getStartMin() { return startMin; }
    public void setStartMin(Integer startMin) { this.startMin = startMin; }
    public Integer getEndMin() { return endMin; }
    public void setEndMin(Integer endMin) { this.endMin = endMin; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
