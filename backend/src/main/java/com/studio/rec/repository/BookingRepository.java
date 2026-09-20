package com.studio.rec.repository;

import com.studio.rec.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByRoomId(Long roomId);

    List<Booking> findByRoomIdAndStartMinLessThanAndEndMinGreaterThan(Long roomId, Integer startMinLessThan, Integer endMinGreaterThan);

    List<Booking> findByEquipmentId(Long equipmentId);

    List<Booking> findByRoomIdAndStatus(Long roomId, String status);

    /** 加总功率用：当前有「进行中」预约的房间 id 集合 */
    @Query("select distinct b.roomId from Booking b where b.status = :status")
    List<Long> findDistinctRoomIdByStatus(@Param("status") String status);

    /** 只取 roomId 的标量查询：不把预约实体提前塞进持久化上下文，保证加锁后读到的是当下数据 */
    @Query("select b.roomId from Booking b where b.id = :id")
    Optional<Long> findRoomIdById(@Param("id") Long id);

    /** 推进预约前对预约行加悲观写锁，锁后重读，状态机按当下数据判定 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}
