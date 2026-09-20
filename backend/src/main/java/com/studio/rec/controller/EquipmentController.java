package com.studio.rec.controller;

import com.studio.rec.entity.Equipment;
import com.studio.rec.service.EquipmentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipments")
public class EquipmentController {
    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public List<Equipment> list() {
        return equipmentService.list();
    }

    @GetMapping("/{id}")
    public Equipment get(@PathVariable Long id) {
        return equipmentService.get(id);
    }

    @PostMapping
    public Equipment create(@RequestBody Equipment equipment) {
        return equipmentService.create(equipment);
    }

    @PutMapping("/{id}")
    public Equipment update(@PathVariable Long id, @RequestBody Equipment equipment) {
        return equipmentService.update(id, equipment);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        equipmentService.delete(id);
    }
}
