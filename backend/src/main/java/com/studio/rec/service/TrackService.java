package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.dto.TrackBudgetView;
import com.studio.rec.entity.Booking;
import com.studio.rec.entity.Track;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrackService {
    private final TrackRepository trackRepository;
    private final BookingRepository bookingRepository;

    public TrackService(TrackRepository trackRepository, BookingRepository bookingRepository) {
        this.trackRepository = trackRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<Track> list() {
        return trackRepository.findAll();
    }

    public Track get(Long id) {
        return trackRepository.findById(id)
                .orElseThrow(() -> new BizException("曲目不存在"));
    }

    /**
     * 曲目页账面：按预约返回「预约分钟、已排分钟、剩余分钟」。
     * 已排分钟直接取库内当下合计，不依赖前端上报。
     */
    @Transactional(readOnly = true)
    public List<TrackBudgetView> listBudgets() {
        Map<Long, Long> usedMap = new HashMap<>();
        for (Object[] row : trackRepository.sumDurationGroupByBookingId()) {
            usedMap.put((Long) row[0], ((Number) row[1]).longValue());
        }
        List<TrackBudgetView> views = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            int bookingMin = b.getEndMin() - b.getStartMin();
            long used = usedMap.getOrDefault(b.getId(), 0L);
            views.add(new TrackBudgetView(b.getId(), bookingMin, used, bookingMin - used));
        }
        return views;
    }

    private void validateRating(Integer rating) {
        // 0 用于清星（置为未打星）；有值时必须落在 1~5
        if (rating != null && rating != 0 && (rating < 1 || rating > 5)) {
            throw new BizException("评分需在 1~5 之间");
        }
    }

    /**
     * 新增曲目。先对归属预约行加悲观写锁（与其他新增 / 修改 / 加长串行），
     * 锁后按库内当下全部曲目重新汇总分钟数，再加上本次时长；
     * 超过预约起止时段分钟数（endMin - startMin）当场拒绝、整笔不落库。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Track create(Track input) {
        if (input.getBookingId() == null) {
            throw new BizException("曲目归属的预约不能为空");
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("曲名不能为空");
        }
        if (input.getDuration() == null || input.getDuration() <= 0) {
            throw new BizException("时长需大于 0");
        }
        validateRating(input.getRating());

        // 锁预约行：两名助理同时给同一预约加曲目时，后到者必须等先到者提交后再汇总
        Booking booking = bookingRepository.findByIdForUpdate(input.getBookingId())
                .orElseThrow(() -> new BizException("曲目归属的预约不存在"));
        int bookingMin = booking.getEndMin() - booking.getStartMin();
        long used = trackRepository.sumDurationByBookingId(booking.getId());
        long projected = used + input.getDuration();
        if (projected > bookingMin) {
            throw new BizException(quotaMessage(bookingMin, used, projected));
        }

        Track track = new Track();
        track.setBookingId(input.getBookingId());
        track.setName(input.getName());
        track.setDuration(input.getDuration());
        track.setRating(normalizeRating(input.getRating()));
        return trackRepository.save(track);
    }

    /**
     * 修改曲目。同样先锁预约行，锁后重读该曲目与全部曲目分钟数；
     * 汇总时剔除自己这笔的旧时长（usedOthers = 全部 - 旧时长），再按新时长重算，
     * 超过预约分钟数当场拒绝、原曲目内容一行不动。校验通过后才改实体并落库。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Track update(Long id, Track input) {
        if (input.getDuration() != null && input.getDuration() <= 0) {
            throw new BizException("时长需大于 0");
        }
        if (input.getRating() != null) {
            validateRating(input.getRating());
        }

        Long bookingId = trackRepository.findBookingIdById(id)
                .orElseThrow(() -> new BizException("曲目不存在"));
        // 锁预约行：加长别人曲目 / 给同预约加新歌的并发事务在此排队，后到者按最新合计重判
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BizException("曲目归属的预约不存在"));
        // 锁后重读：不使用进方法前的任何快照
        Track track = trackRepository.findById(id)
                .orElseThrow(() -> new BizException("曲目不存在"));

        int newDuration = input.getDuration() != null ? input.getDuration() : track.getDuration();
        int bookingMin = booking.getEndMin() - booking.getStartMin();
        long usedAll = trackRepository.sumDurationByBookingId(booking.getId());
        long usedOthers = usedAll - track.getDuration();
        long projected = usedOthers + newDuration;
        if (projected > bookingMin) {
            throw new BizException(quotaMessage(bookingMin, usedAll, projected));
        }

        // 额度校验过后才动实体：失败时原曲目名 / 时长 / 评分保持不变，不留半成品
        if (input.getName() != null && !input.getName().isBlank()) {
            track.setName(input.getName());
        }
        if (input.getDuration() != null) {
            track.setDuration(input.getDuration());
        }
        if (input.getRating() != null) {
            track.setRating(normalizeRating(input.getRating()));
        }
        return trackRepository.save(track);
    }

    private Integer normalizeRating(Integer rating) {
        return rating != null && rating == 0 ? null : rating;
    }

    /** 拒绝信息带账面数字：预约分钟、当前已排、保存后合计与剩余，方便后台对账面 */
    private String quotaMessage(int bookingMin, long used, long projected) {
        return "曲目总时长超过预约棚时：预约 " + bookingMin + " 分钟、当前已排 " + used
                + " 分钟、剩余 " + (bookingMin - used) + " 分钟，本次保存后合计 " + projected
                + " 分钟，已整笔退回（曲目未保存、原内容不变）";
    }

    @Transactional
    public void delete(Long id) {
        if (!trackRepository.existsById(id)) {
            throw new BizException("曲目不存在");
        }
        trackRepository.deleteById(id);
    }
}
