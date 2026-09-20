package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.entity.Room;
import com.studio.rec.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {
    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<Room> list() {
        return roomRepository.findAll();
    }

    public Room get(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new BizException("录音室不存在"));
    }

    public Room create(Room input) {
        if (input.getCode() == null || input.getCode().isBlank()) {
            throw new BizException("编号不能为空");
        }
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("名称不能为空");
        }
        if (roomRepository.findByCode(input.getCode()).isPresent()) {
            throw new BizException("编号已存在，不可重复");
        }
        Room room = new Room();
        room.setCode(input.getCode());
        room.setName(input.getName());
        room.setStatus(input.getStatus() == null || input.getStatus().isBlank() ? "空闲" : input.getStatus());
        room.setPower(input.getPower());
        return roomRepository.save(room);
    }

    public Room update(Long id, Room input) {
        Room room = get(id);
        if (input.getCode() != null && !input.getCode().equals(room.getCode())) {
            if (roomRepository.findByCode(input.getCode()).isPresent()) {
                throw new BizException("编号已存在，不可重复");
            }
            room.setCode(input.getCode());
        }
        if (input.getName() != null && !input.getName().isBlank()) {
            room.setName(input.getName());
        }
        if (input.getPower() != null) {
            // 占用中的房间禁止修改功率类字段
            if ("占用".equals(room.getStatus())) {
                throw new BizException("占用中的录音室禁止修改功率");
            }
            room.setPower(input.getPower());
        }
        if (input.getStatus() != null && !input.getStatus().isBlank()) {
            room.setStatus(input.getStatus());
        }
        return roomRepository.save(room);
    }

    public void delete(Long id) {
        roomRepository.deleteById(id);
    }
}
