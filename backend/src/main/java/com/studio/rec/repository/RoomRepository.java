package com.studio.rec.repository;

import com.studio.rec.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByCodeAndIdNot(String code, Long id);
    Optional<Room> findByCode(String code);

    /** 改设备状态 / 开预约的关键路径用：对房间行加悲观写锁，串行化同一房间的并发写 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
