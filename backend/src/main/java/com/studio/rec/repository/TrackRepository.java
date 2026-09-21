package com.studio.rec.repository;

import com.studio.rec.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByBookingId(Long bookingId);

    /** 标量查询：不把曲目实体提前塞进持久化上下文，锁后汇总看到的才是当下数据 */
    @Query("select t.bookingId from Track t where t.id = :id")
    Optional<Long> findBookingIdById(@Param("id") Long id);

    /** 某预约当前全部曲目分钟数合计；无曲目返回 0 */
    @Query("select coalesce(sum(t.duration), 0) from Track t where t.bookingId = :bookingId")
    Long sumDurationByBookingId(@Param("bookingId") Long bookingId);

    /** 各预约 id -> 当前已排分钟数（用于曲目页账面展示） */
    @Query("select t.bookingId, coalesce(sum(t.duration), 0) from Track t group by t.bookingId")
    List<Object[]> sumDurationGroupByBookingId();
}
