package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.dto.MasterReleaseRequest;
import com.studio.rec.dto.MasterReleaseView;
import com.studio.rec.entity.Booking;
import com.studio.rec.entity.MasterRelease;
import com.studio.rec.entity.MasterReleaseItem;
import com.studio.rec.entity.Room;
import com.studio.rec.entity.Track;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.MasterReleaseItemRepository;
import com.studio.rec.repository.MasterReleaseRepository;
import com.studio.rec.repository.RoomRepository;
import com.studio.rec.repository.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MasterReleaseService {
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MasterReleaseRepository releaseRepository;
    private final MasterReleaseItemRepository itemRepository;
    private final BookingRepository bookingRepository;
    private final TrackRepository trackRepository;
    private final RoomRepository roomRepository;

    public MasterReleaseService(MasterReleaseRepository releaseRepository,
                                MasterReleaseItemRepository itemRepository,
                                BookingRepository bookingRepository,
                                TrackRepository trackRepository,
                                RoomRepository roomRepository) {
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
        this.bookingRepository = bookingRepository;
        this.trackRepository = trackRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<MasterReleaseView> list() {
        List<MasterReleaseView> views = new ArrayList<>();
        for (MasterRelease r : releaseRepository.findAll()) {
            views.add(toView(r));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public MasterReleaseView get(Long id) {
        return toView(loadRelease(id));
    }

    /**
     * 开单：必须挂在已完成的预约上，且勾入至少两首已打星、归属该预约的曲目。
     * 只建空单不勾曲目、或只勾曲目不建单，都不允许——单子和齐件在同一事务落库。
     */
    @Transactional
    public MasterReleaseView create(MasterReleaseRequest input) {
        Booking booking = requireBookingCompleted(input.getBookingId());

        List<Track> picked = validatePickedTracks(booking, input.getTrackIds());
        if (picked.size() < 2) {
            throw new BizException("齐件曲目至少勾入两首已打星曲目");
        }

        MasterRelease release = new MasterRelease();
        release.setReleaseNo(generateReleaseNo());
        release.setBookingId(booking.getId());
        release.setStatus(MasterRelease.STATUS_PENDING);
        release.setFailReason(null);
        release = releaseRepository.save(release);

        saveItems(release.getId(), picked);
        return get(release.getId());
    }

    /**
     * 重新勾齐件：仅待齐件 / 会签失败后停在会签中 的单据可改。
     * 已放行不许再动。同样要求至少两首、当前全部有星级。
     */
    @Transactional
    public MasterReleaseView updateItems(Long id, MasterReleaseRequest input) {
        MasterRelease release = loadRelease(id);
        if (MasterRelease.STATUS_RELEASED.equals(release.getStatus())) {
            throw new BizException("已放行的单据不可修改齐件");
        }
        Booking booking = bookingRepository.findById(release.getBookingId())
                .orElseThrow(() -> new BizException("单据挂载的预约不存在"));

        // 改齐件按当前数据重新核：预约已不再完成则连勾曲目都不让保存
        if (!"已完成".equals(booking.getStatus())) {
            throw new BizException("预约当前不是已完成，无法整理齐件");
        }
        List<Track> picked = validatePickedTracks(booking, input.getTrackIds());
        if (picked.size() < 2) {
            throw new BizException("齐件曲目至少勾入两首已打星曲目");
        }

        itemRepository.deleteByReleaseId(release.getId());
        itemRepository.flush();
        saveItems(release.getId(), picked);

        // 上次会签失败留下的原因，齐件整理后先清掉；是否过签以重新会签的实时校验为准
        release.setFailReason(null);
        release.setUpdatedAt(LocalDateTime.now());
        releaseRepository.save(release);
        return get(release.getId());
    }

    /**
     * 提交会签：待齐件 -> 会签中。进入会签中时先按当前数据核一遍，
     * 不合规直接整单停在会签中并写失败原因，齐件一条都不会被部分勾改。
     */
    @Transactional
    public MasterReleaseView submitSign(Long id) {
        MasterRelease release = releaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("母带放行单不存在"));
        if (!MasterRelease.STATUS_PENDING.equals(release.getStatus())) {
            throw new BizException("只有待齐件的单据才能提交会签，不可跳步");
        }
        release.setStatus(MasterRelease.STATUS_SIGNING);
        release.setFailReason(null);
        release.setUpdatedAt(LocalDateTime.now());
        releaseRepository.saveAndFlush(release);

        return runSignChecks(release);
    }

    /**
     * 会签失败后重新会签：必须按此刻的预约、曲目、房间重新核一遍，
     * 不能沿用上一次失败前的快照。停在会签中的单子（含重新整理过齐件的）都可重签。
     */
    @Transactional
    public MasterReleaseView retrySign(Long id) {
        MasterRelease release = releaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("母带放行单不存在"));
        if (!MasterRelease.STATUS_SIGNING.equals(release.getStatus())) {
            throw new BizException("只有会签中的单据才能重新会签");
        }
        return runSignChecks(release);
    }

    /**
     * 会签实时校验（提交 / 重试共用）：
     * 预约仍为已完成、勾中的曲目此刻仍有星级、录音室此刻不是维护。
     * 任一不符：整单停在会签中，写清全部失败原因，不做任何半成品落库；
     * 全部通过：会签中 -> 已放行。
     */
    private MasterReleaseView runSignChecks(MasterRelease release) {
        List<String> failures = new ArrayList<>();

        Booking booking = bookingRepository.findById(release.getBookingId()).orElse(null);
        if (booking == null) {
            failures.add("挂载的预约已不存在");
        } else if (!"已完成".equals(booking.getStatus())) {
            failures.add("预约当前状态为「" + booking.getStatus() + "」，不再是已完成");
        }

        List<MasterReleaseItem> items = itemRepository.findByReleaseIdOrderByIdAsc(release.getId());
        if (items.size() < 2) {
            failures.add("齐件曲目不足两首");
        }
        List<String> unratedNames = new ArrayList<>();
        for (MasterReleaseItem item : items) {
            Track track = trackRepository.findById(item.getTrackId()).orElse(null);
            if (track == null) {
                unratedNames.add("#" + item.getTrackId() + "(已删除)");
            } else if (track.getRating() == null) {
                unratedNames.add(track.getName());
            }
        }
        if (!unratedNames.isEmpty()) {
            failures.add("以下齐件曲目当前没有星级：" + String.join("、", unratedNames));
        }

        Room room = null;
        if (booking != null) {
            room = roomRepository.findById(booking.getRoomId()).orElse(null);
            if (room == null) {
                failures.add("预约的录音室已不存在，无法取带");
            } else if ("维护".equals(room.getStatus())) {
                // 母带室口径优先：维护中的房间柜子上锁、带子拿不出来，禁止放行
                failures.add("录音室「" + room.getName() + "」处于维护中，柜子上锁无法取带，禁止放行");
            }
        }

        if (!failures.isEmpty()) {
            release.setStatus(MasterRelease.STATUS_SIGNING);
            release.setFailReason(String.join("；", failures));
            release.setUpdatedAt(LocalDateTime.now());
            releaseRepository.save(release);
            return get(release.getId());
        }

        release.setStatus(MasterRelease.STATUS_RELEASED);
        release.setFailReason(null);
        release.setUpdatedAt(LocalDateTime.now());
        releaseRepository.save(release);
        return get(release.getId());
    }

    @Transactional
    public void delete(Long id) {
        MasterRelease release = loadRelease(id);
        if (MasterRelease.STATUS_RELEASED.equals(release.getStatus())) {
            throw new BizException("已放行的单据不可删除");
        }
        itemRepository.deleteByReleaseId(release.getId());
        releaseRepository.delete(release);
    }

    // ---- 校验与装配 ----

    private MasterRelease loadRelease(Long id) {
        return releaseRepository.findById(id)
                .orElseThrow(() -> new BizException("母带放行单不存在"));
    }

    private Booking requireBookingCompleted(Long bookingId) {
        if (bookingId == null) {
            throw new BizException("放行单必须挂载一条预约");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BizException("挂载的预约不存在"));
        if (!"已完成".equals(booking.getStatus())) {
            throw new BizException("只有已完成的预约才能开母带放行单，当前为「" + booking.getStatus() + "」");
        }
        return booking;
    }

    /**
     * 按当前库内状态核对勾入曲目：去重、必须存在、必须归属该预约、必须已打星。
     */
    private List<Track> validatePickedTracks(Booking booking, List<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            throw new BizException("开单必须勾入齐件曲目（至少两首已打星曲目）");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(trackIds);
        List<Track> picked = new ArrayList<>();
        List<String> notOwned = new ArrayList<>();
        List<String> unrated = new ArrayList<>();
        for (Long trackId : uniqueIds) {
            if (trackId == null) {
                continue;
            }
            Track track = trackRepository.findById(trackId)
                    .orElseThrow(() -> new BizException("曲目不存在：#" + trackId));
            if (!booking.getId().equals(track.getBookingId())) {
                notOwned.add(track.getName());
                continue;
            }
            if (track.getRating() == null) {
                unrated.add(track.getName());
            }
            picked.add(track);
        }
        if (!notOwned.isEmpty()) {
            throw new BizException("曲目不属于该预约，不能勾入：" + String.join("、", notOwned));
        }
        if (!unrated.isEmpty()) {
            throw new BizException("勾入曲目存在未打星曲目，不允许开单：" + String.join("、", unrated));
        }
        return picked;
    }

    private void saveItems(Long releaseId, List<Track> tracks) {
        for (Track track : tracks) {
            MasterReleaseItem item = new MasterReleaseItem();
            item.setReleaseId(releaseId);
            item.setTrackId(track.getId());
            itemRepository.save(item);
        }
    }

    private String generateReleaseNo() {
        String base = "MR" + LocalDateTime.now().format(NO_FMT);
        String no = base;
        int seq = 1;
        while (releaseRepository.existsByReleaseNo(no)) {
            no = base + "-" + seq++;
        }
        return no;
    }

    private MasterReleaseView toView(MasterRelease release) {
        MasterReleaseView v = MasterReleaseView.of(release);
        Booking booking = bookingRepository.findById(release.getBookingId()).orElse(null);
        if (booking != null) {
            v.setBookingStatus(booking.getStatus());
            v.setCustName(booking.getCustName());
            v.setRoomId(booking.getRoomId());
            Room room = roomRepository.findById(booking.getRoomId()).orElse(null);
            if (room != null) {
                v.setRoomCode(room.getCode());
                v.setRoomName(room.getName());
                v.setRoomStatus(room.getStatus());
            }
        }
        List<MasterReleaseView.ItemView> itemViews = new ArrayList<>();
        for (MasterReleaseItem item : itemRepository.findByReleaseIdOrderByIdAsc(release.getId())) {
            MasterReleaseView.ItemView iv = new MasterReleaseView.ItemView();
            iv.setItemId(item.getId());
            iv.setTrackId(item.getTrackId());
            Track track = trackRepository.findById(item.getTrackId()).orElse(null);
            if (track != null) {
                iv.setName(track.getName());
                iv.setDuration(track.getDuration());
                iv.setRating(track.getRating());
            }
            itemViews.add(iv);
        }
        v.setItems(itemViews);
        return v;
    }
}
