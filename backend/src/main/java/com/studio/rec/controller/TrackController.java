package com.studio.rec.controller;

import com.studio.rec.dto.TrackBudgetView;
import com.studio.rec.entity.Track;
import com.studio.rec.service.TrackService;
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
@RequestMapping("/api/tracks")
public class TrackController {
    private final TrackService trackService;

    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }

    @GetMapping
    public List<Track> list() {
        return trackService.list();
    }

    /** 各预约的曲目分钟账面：预约分钟 / 已排分钟 / 剩余分钟，数字以后台库内汇总为准 */
    @GetMapping("/budgets")
    public List<TrackBudgetView> listBudgets() {
        return trackService.listBudgets();
    }

    @GetMapping("/{id}")
    public Track get(@PathVariable Long id) {
        return trackService.get(id);
    }

    @PostMapping
    public Track create(@RequestBody Track track) {
        return trackService.create(track);
    }

    @PutMapping("/{id}")
    public Track update(@PathVariable Long id, @RequestBody Track track) {
        return trackService.update(id, track);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        trackService.delete(id);
    }
}
