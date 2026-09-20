package com.studio.rec.repository;

import com.studio.rec.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByRoomId(Long roomId);
    long countByRoomIdAndStatus(Long roomId, String status);
    long countByRoomId(Long roomId);
}
