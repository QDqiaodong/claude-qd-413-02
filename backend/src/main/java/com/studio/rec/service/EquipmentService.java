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

import java.util.List;

@Service
public class EquipmentService {
    private final EquipmentRepository equipmentRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public EquipmentService(EquipmentRepository equipmentRepository, RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.equipmentRepository = equipmentRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<Equipment> list() {
        return equipmentRepository.findAll();
    }

    public Equipment get(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new BizException("录音设备不存在"));
    }

    public Equipment create(Equipment input) {
        if (input.getCode() == null || input.getCode().isBlank()) {
            throw new BizException("编号不能为空");
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("名称不能为空");
        }
        if (input.getType() == null || input.getType().isBlank()) {
            throw new BizException("类型不能为空");
        }
        // 归属室必须存在
        if (input.getRoomId() != null) {
            Room room = roomRepository.findById(input.getRoomId())
                    .orElseThrow(() -> new BizException("归属的录音室不存在"));
        }
        Equipment equipment = new Equipment();
        equipment.setCode(input.getCode());
        equipment.setName(input.getName());
        equipment.setRoomId(input.getRoomId());
        equipment.setType(input.getType());
        equipment.setStatus(input.getStatus() == null || input.getStatus().isBlank() ? "空闲" : input.getStatus());
        return equipmentRepository.save(equipment);
    }

    /**
     * 改设备状态。报故障按棚控口径当场拆占用：故障件所在房间只要还有进行中预约，
     * 同一事务内把这些预约打回待确认、房间改回空闲；拆不干净整笔回滚，设备状态也不落库。
     * 房间没有进行中预约时只改设备状态，不动房间。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Equipment update(Long id, Equipment input) {
        Equipment equipment = get(id);
        if (input.getCode() != null && !input.getCode().isBlank()) {
            equipment.setCode(input.getCode());
        }
        if (input.getName() != null && !input.getName().isBlank()) {
            equipment.setName(input.getName());
        }
        if (input.getType() != null && !input.getType().isBlank()) {
            equipment.setType(input.getType());
        }
        if (input.getRoomId() != null) {
            if (!roomRepository.existsById(input.getRoomId())) {
                throw new BizException("归属的录音室不存在");
            }
            equipment.setRoomId(input.getRoomId());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            equipment.setStatus(input.getStatus());
            if ("故障".equals(input.getStatus())) {
                teardownRoomOccupation(equipment.getRoomId());
            }
        }
        return equipmentRepository.save(equipment);
    }

    /**
     * 拆占用：先锁房间行，与「新开预约 / 推进进行中」串行，彼此只看对方提交后的占用和设备状态；
     * 锁后按当下数据把该房间所有进行中预约打回待确认，并把房间改回空闲。
     */
    private void teardownRoomOccupation(Long roomId) {
        if (roomId == null) {
            return;
        }
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BizException("设备归属的录音室不存在"));
        List<Booking> inProgress = bookingRepository.findByRoomIdAndStatus(roomId, "进行中");
        if (inProgress.isEmpty()) {
            return;
        }
        for (Booking b : inProgress) {
            b.setStatus("待确认");
            bookingRepository.save(b);
        }
        if (!"空闲".equals(room.getStatus())) {
            room.setStatus("空闲");
            roomRepository.save(room);
        }
    }

    public void delete(Long id) {
        equipmentRepository.deleteById(id);
    }
}
