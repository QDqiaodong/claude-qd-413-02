package com.studio.rec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 全棚级开关项（key-value）。目前只有一项：power_limit —— 夜班电工定的全棚功率合计上限（W）。
 */
@Entity
@Table(name = "studio_setting")
public class StudioSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skey", nullable = false, unique = true)
    private String skey;

    @Column(name = "svalue", nullable = false)
    private String svalue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSkey() { return skey; }
    public void setSkey(String skey) { this.skey = skey; }
    public String getSvalue() { return svalue; }
    public void setSvalue(String svalue) { this.svalue = svalue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
