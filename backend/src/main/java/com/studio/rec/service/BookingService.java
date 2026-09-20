package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.entity.Booking;
import com.studio.rec.entity.Equipment;
import com.studio.rec.entity.Room;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.EquipmentRepository;
import com.studio.rec.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class BookingService {
    private static final List<String> STATUS_ORDER = Arrays.asList("待确认", "进行中", "已完成");

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final SettingService settingService;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository,
                          EquipmentRepository equipmentRepository, SettingService settingService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.equipmentRepository = equipmentRepository;
        this.settingService = settingService;
    }

    public List<Booking> list() {
        return bookingRepository.findAll();
    }

    public Booking get(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BizException("预约不存在"));
    }

    /**
     * 新开预约。先锁全棚功率上限行、再锁目标房间行，与「报故障拆占用」等并发写串行；
     * 设备占用、时段重叠、故障闸门、功率上限全部按加锁后的当下数据校验，
     * 任一不过：整笔不落库，房间和预约停在原状。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking create(Booking input) {
        if (input.getRoomId() == null) {
            throw new BizException("预约的录音室不能为空");
        }
        if (input.getCustName() == null || input.getCustName().isBlank()) {
            throw new BizException("客户名称不能为空");
        }
        if (input.getStartMin() == null || input.getEndMin() == null || input.getStartMin() >= input.getEndMin()) {
            throw new BizException("时段起止不合法");
        }
        String status = (input.getStatus() == null || input.getStatus().isBlank()) ? "待确认" : input.getStatus();
        if (!"待确认".equals(status) && !"进行中".equals(status)) {
            throw new BizException("新预约只能从待确认或进行中开始");
        }

        // 加锁顺序固定为「功率上限行 -> 房间行」，所有写路径一致，避免死锁
        Long powerLimit = settingService.lockPowerLimit();
        Room room = roomRepository.findByIdForUpdate(input.getRoomId())
                .orElseThrow(() -> new BizException("预约的录音室不存在"));

        // 占用中设备不能重复约
        if (input.getEquipmentId() != null) {
            Equipment equipment = equipmentRepository.findById(input.getEquipmentId())
                    .orElseThrow(() -> new BizException("预约的设备不存在"));
            if ("占用".equals(equipment.getStatus())) {
                throw new BizException("该设备正被占用，无法重复预约");
            }
        }
        // 同房间时段重叠拦截
        List<Booking> overlap = bookingRepository.findByRoomIdAndStartMinLessThanAndEndMinGreaterThan(
                input.getRoomId(), input.getEndMin(), input.getStartMin());
        if (!overlap.isEmpty()) {
            throw new BizException("该录音室时段冲突，已被预约");
        }
        // 故障闸门：开进行中的场次，房间里不能有故障设备
        if ("进行中".equals(status)) {
            assertRoomFreeOfFault(room);
        }
        // 电工口径：新开预约先加总功率，超限当场拦住（不是黄条提醒）
        settingService.assertPowerWithinLimit(powerLimit, room);

        Booking booking = new Booking();
        booking.setRoomId(input.getRoomId());
        booking.setEquipmentId(input.getEquipmentId());
        booking.setCustName(input.getCustName());
        booking.setStartMin(input.getStartMin());
        booking.setEndMin(input.getEndMin());
        booking.setStatus(status);
        return bookingRepository.save(booking);
    }

    /**
     * 推进预约。状态机、故障闸门、功率上限都基于加锁后的当下数据重核，
     * 不使用进入方法前的任何快照；推进失败整笔回滚，房间和预约停在原状。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking update(Long id, Booking input) {
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            int target = STATUS_ORDER.indexOf(input.getStatus());
            if (target < 0) {
                throw new BizException("非法的预约状态");
            }
            Long roomId = bookingRepository.findRoomIdById(id)
                    .orElseThrow(() -> new BizException("预约不存在"));
            // 推进进行中会抬高全棚功率，先锁功率上限行；再锁房间行，与报故障拆占用串行
            Long powerLimit = "进行中".equals(input.getStatus()) ? settingService.lockPowerLimit() : null;
            Room room = roomRepository.findByIdForUpdate(roomId)
                    .orElseThrow(() -> new BizException("预约的录音室不存在"));
            // 锁预约行后重读：状态机按当下状态判定（可能刚被故障拆占用打回待确认）
            Booking booking = bookingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BizException("预约不存在"));
            int current = STATUS_ORDER.indexOf(booking.getStatus());
            if (target <= current) {
                throw new BizException("预约状态只能向前推进");
            }
            if (target > current + 1) {
                throw new BizException("预约状态不能跳步");
            }
            if ("进行中".equals(input.getStatus())) {
                // 故障房不许开进行中；功率超限当场拦住
                assertRoomFreeOfFault(room);
                settingService.assertPowerWithinLimit(powerLimit, room);
            }
            booking.setStatus(input.getStatus());
            if (input.getCustName() != null && !input.getCustName().isBlank()) {
                booking.setCustName(input.getCustName());
            }
            return bookingRepository.save(booking);
        }
        Booking booking = get(id);
        if (input.getCustName() != null && !input.getCustName().isBlank()) {
            booking.setCustName(input.getCustName());
        }
        return bookingRepository.save(booking);
        // 并发改同一预约时 @Version 会自动触发乐观锁，抛出 OptimisticLockException，由 @RestControllerAdvice 转 400
    }

    /** 故障闸门：房间里有故障设备，就不能开展进行中的场次 */
    private void assertRoomFreeOfFault(Room room) {
        if (equipmentRepository.countByRoomIdAndStatus(room.getId(), "故障") > 0) {
            throw new BizException("录音室「" + room.getName() + "」存在故障设备，不能开展进行中的场次，请先处理故障");
        }
    }

    public void delete(Long id) {
        bookingRepository.deleteById(id);
    }
}
