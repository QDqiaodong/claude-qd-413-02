package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.dto.TrackUsageView;
import com.studio.rec.entity.Booking;
import com.studio.rec.entity.Track;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    private void validateRating(Integer rating) {
        // 0 用于清星（置为未打星）；有值时必须落在 1~5
        if (rating != null && rating != 0 && (rating < 1 || rating > 5)) {
            throw new BizException("评分需在 1~5 之间");
        }
    }

    /**
     * 新增曲目。先对归属预约行加悲观写锁，再按库里该预约当前全部曲目重新汇总分钟数，
     * 把本次时长计入后超过预约时段分钟数就当场拒绝、整笔退回——曲目对象在校验通过后才创建，
     * 失败不留半条。两名助理并发给同一预约新增曲目时，后一笔锁后看到的是前一笔提交后的最新总数，
     * 不会各自拿旧余量都保存成功。
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

        Booking booking = bookingRepository.findByIdForUpdate(input.getBookingId())
                .orElseThrow(() -> new BizException("曲目归属的预约不存在"));
        long currentTotal = trackRepository.sumDurationByBookingId(booking.getId());
        assertQuota(booking, currentTotal, input.getDuration(), 0L);

        Track track = new Track();
        track.setBookingId(input.getBookingId());
        track.setName(input.getName());
        track.setDuration(input.getDuration());
        track.setRating(normalizeRating(input.getRating()));
        return trackRepository.save(track);
    }

    /**
     * 修改曲目。改时长时按固定顺序「预约行 → 本曲目行」加悲观锁：
     * 先锁预约行（与并发新增/加长其他曲目串行），再锁本曲目行拿最新旧值，
     * 按库里该预约当前全部曲目汇总并扣除本曲目旧时长（不能把旧时长重复计算），加新时长重判。
     * 超限当场抛错回滚，曲目名称/时长/评分全部停在原状；不接受 0 或负数时长。
     * 重试时全部数字重新查库，不沿用上一次失败前的快照。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Track update(Long id, Track input) {
        // 基础校验先做：非法请求不必加锁
        if (input.getName() != null && input.getName().isBlank()) {
            throw new BizException("曲名不能为空");
        }
        if (input.getDuration() != null && input.getDuration() <= 0) {
            throw new BizException("时长需大于 0");
        }
        if (input.getRating() != null) {
            validateRating(input.getRating());
        }

        // 先只取预约 id（标量），不把曲目实体提前塞进持久化上下文
        Long bookingId = trackRepository.findBookingIdById(id)
                .orElseThrow(() -> new BizException("曲目不存在"));
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BizException("曲目归属的预约不存在"));
        // 预约锁内再锁本曲目行，锁后托管实例是对方提交后的最新行，不会丢失更新
        Track track = trackRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("曲目不存在"));

        if (input.getDuration() != null && !input.getDuration().equals(track.getDuration())) {
            long oldDuration = track.getDuration() == null ? 0L : track.getDuration();
            long currentTotal = trackRepository.sumDurationByBookingId(booking.getId());
            assertQuota(booking, currentTotal, input.getDuration(), oldDuration);
        }

        if (input.getName() != null && !input.getName().isBlank()) {
            track.setName(input.getName());
        }
        if (input.getDuration() != null && input.getDuration() > 0) {
            track.setDuration(input.getDuration());
        }
        if (input.getRating() != null) {
            track.setRating(normalizeRating(input.getRating()));
        }
        return trackRepository.save(track);
    }

    /**
     * 硬额度判断：已排分钟（库里当前总数扣除本曲目旧时长后）+ 本次新时长，
     * 超过预约起止时段分钟数即拒绝。所有数字都取自加锁后的当下库内状态，不用页面传来的快照。
     */
    private void assertQuota(Booking booking, long currentTotal, long newDuration, long oldDuration) {
        long quota = booking.getEndMin() - booking.getStartMin();
        long projected = currentTotal - oldDuration + newDuration;
        if (projected > quota) {
            String action = oldDuration > 0 ? "本曲目改为 " : "本曲目 ";
            throw new BizException("预约「" + booking.getCustName() + "」棚时只有 " + quota
                    + " 分钟，当前已排 " + currentTotal + " 分钟，" + action + newDuration
                    + " 分钟后合计 " + projected + " 分钟，超出 " + (projected - quota)
                    + " 分钟，已整笔退回，原曲目内容不变");
        }
    }

    /** 后台账面：每个预约的预约分钟、已排分钟、剩余分钟（全部由后台实时汇总） */
    @Transactional(readOnly = true)
    public List<TrackUsageView> usage() {
        List<TrackUsageView> views = new ArrayList<>();
        for (Booking b : bookingRepository.findAll()) {
            TrackUsageView v = new TrackUsageView();
            v.setBookingId(b.getId());
            v.setCustName(b.getCustName());
            int quota = b.getEndMin() - b.getStartMin();
            long scheduled = trackRepository.sumDurationByBookingId(b.getId());
            v.setBookingMinutes(quota);
            v.setScheduledMinutes(scheduled);
            v.setRemainingMinutes(quota - scheduled);
            views.add(v);
        }
        return views;
    }

    private Integer normalizeRating(Integer rating) {
        return rating != null && rating == 0 ? null : rating;
    }

    @Transactional
    public void delete(Long id) {
        trackRepository.deleteById(id);
    }
}
