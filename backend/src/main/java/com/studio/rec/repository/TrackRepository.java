package com.studio.rec.repository;

import com.studio.rec.entity.Track;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByBookingId(Long bookingId);

    /** 库里该预约当前全部曲目的分钟数汇总；一首都没有时返回 0（不加权内存里的未提交对象） */
    @Query("select coalesce(sum(t.duration), 0) from Track t where t.bookingId = :bookingId")
    Long sumDurationByBookingId(@Param("bookingId") Long bookingId);

    /** 只取 bookingId 的标量查询：加预约行锁前不把曲目实体提前塞进持久化上下文 */
    @Query("select t.bookingId from Track t where t.id = :id")
    Optional<Long> findBookingIdById(@Param("id") Long id);

    /**
     * 预约行锁内再对本曲目行加悲观写锁：锁后拿到的是其他事务提交后的最新行，
     * 同一曲目的并发修改按最新旧时长重算，且不会用陈旧快照覆盖对方已提交的字段。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Track t where t.id = :id")
    Optional<Track> findByIdForUpdate(@Param("id") Long id);
}
