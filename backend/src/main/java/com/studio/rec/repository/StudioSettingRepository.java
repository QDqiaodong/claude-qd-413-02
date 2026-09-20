package com.studio.rec.repository;

import com.studio.rec.entity.StudioSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudioSettingRepository extends JpaRepository<StudioSetting, Long> {
    Optional<StudioSetting> findBySkey(String skey);

    /** 功率相关落库前用：对设置行加悲观写锁，串行化所有要加总功率的事务 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudioSetting s where s.skey = :skey")
    Optional<StudioSetting> findBySkeyForUpdate(@Param("skey") String skey);
}
