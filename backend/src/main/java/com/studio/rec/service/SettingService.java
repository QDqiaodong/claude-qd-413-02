package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.entity.Room;
import com.studio.rec.entity.StudioSetting;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.RoomRepository;
import com.studio.rec.repository.StudioSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 全棚级设置与功率硬上限。电工口径：新开预约、把待确认推进进行中，
 * 都要先把全棚功率加总，超限当场拦住、整笔退回——不是黄条提醒。
 */
@Service
public class SettingService {
    public static final String POWER_LIMIT_KEY = "power_limit";

    private final StudioSettingRepository settingRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public SettingService(StudioSettingRepository settingRepository,
                          RoomRepository roomRepository,
                          BookingRepository bookingRepository) {
        this.settingRepository = settingRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    /** 当前生效的全棚功率上限（W），未设置返回 null */
    public Long getPowerLimit() {
        return settingRepository.findBySkey(POWER_LIMIT_KEY)
                .map(s -> parseLimit(s.getSvalue()))
                .orElse(null);
    }

    /** 全棚当前已占功率合计：状态为「占用」的房间 + 有「进行中」预约的房间 */
    public long currentPowerInUse() {
        Set<Long> busyRoomIds = new HashSet<>(bookingRepository.findDistinctRoomIdByStatus("进行中"));
        long sum = 0;
        for (Room r : roomRepository.findAll()) {
            if ("占用".equals(r.getStatus()) || busyRoomIds.contains(r.getId())) {
                sum += powerOf(r);
            }
        }
        return sum;
    }

    @Transactional
    public void updatePowerLimit(Long value) {
        if (value == null || value <= 0) {
            throw new BizException("功率上限必须为大于 0 的整数（单位 W）");
        }
        StudioSetting setting = settingRepository.findBySkeyForUpdate(POWER_LIMIT_KEY).orElseGet(() -> {
            StudioSetting s = new StudioSetting();
            s.setSkey(POWER_LIMIT_KEY);
            return s;
        });
        setting.setSvalue(String.valueOf(value));
        settingRepository.save(setting);
    }

    /**
     * 预约落库前调用：对功率上限行加悲观写锁，把全棚所有功率相关落库串行化，
     * 之后同一事务内的加总看到的就是对方提交后的当下数据。返回当前上限（未设置返回 null，表示不限）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long lockPowerLimit() {
        return settingRepository.findBySkeyForUpdate(POWER_LIMIT_KEY)
                .map(s -> parseLimit(s.getSvalue()))
                .orElse(null);
    }

    /**
     * 电工口径的硬拦截：把目标房计入后按当下数据加总，超限当场抛错、整笔退回，
     * 房间和预约停在原状。目标房已在活跃集合（占用 / 有进行中预约）则不重复计入。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertPowerWithinLimit(Long limit, Room candidate) {
        if (limit == null || candidate == null) {
            return;
        }
        Set<Long> busyRoomIds = new HashSet<>(bookingRepository.findDistinctRoomIdByStatus("进行中"));
        long inUse = 0;
        boolean candidateActive = false;
        for (Room r : roomRepository.findAll()) {
            boolean active = "占用".equals(r.getStatus()) || busyRoomIds.contains(r.getId());
            if (active) {
                inUse += powerOf(r);
            }
            if (r.getId().equals(candidate.getId())) {
                candidateActive = active;
            }
        }
        long add = candidateActive ? 0 : powerOf(candidate);
        long projected = inUse + add;
        if (projected > limit) {
            if (candidateActive) {
                throw new BizException("全棚功率合计超限：当前已占 " + inUse + "W，超过上限 " + limit + "W，已整笔退回");
            }
            throw new BizException("全棚功率合计超限：当前已占 " + inUse + "W，本次「" + candidate.getName()
                    + "」新增 " + add + "W 后合计 " + projected + "W，超过上限 " + limit + "W，已整笔退回");
        }
    }

    private static long powerOf(Room r) {
        return r.getPower() == null ? 0 : r.getPower();
    }

    private static Long parseLimit(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
