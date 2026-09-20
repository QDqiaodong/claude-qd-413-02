package com.studio.rec.controller;

import com.studio.rec.dto.PowerLimitView;
import com.studio.rec.service.SettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {
    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    /** 查看全棚功率上限与当前已占合计 */
    @GetMapping("/power-limit")
    public PowerLimitView getPowerLimit() {
        return new PowerLimitView(settingService.getPowerLimit(), settingService.currentPowerInUse());
    }

    /** 调整全棚功率上限（body：{"value": 7000}，必须为正整数） */
    @PutMapping("/power-limit")
    public PowerLimitView updatePowerLimit(@RequestBody Map<String, Object> body) {
        Object v = body.get("value");
        if (v == null) {
            v = body.get("limit");
        }
        Long value = v == null ? null : Long.valueOf(v.toString());
        settingService.updatePowerLimit(value);
        return new PowerLimitView(settingService.getPowerLimit(), settingService.currentPowerInUse());
    }
}
