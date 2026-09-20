package com.studio.rec.controller;

import com.studio.rec.dto.MasterReleaseRequest;
import com.studio.rec.dto.MasterReleaseView;
import com.studio.rec.service.MasterReleaseService;
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
@RequestMapping("/api/master-releases")
public class MasterReleaseController {
    private final MasterReleaseService masterReleaseService;

    public MasterReleaseController(MasterReleaseService masterReleaseService) {
        this.masterReleaseService = masterReleaseService;
    }

    @GetMapping
    public List<MasterReleaseView> list() {
        return masterReleaseService.list();
    }

    @GetMapping("/{id}")
    public MasterReleaseView get(@PathVariable Long id) {
        return masterReleaseService.get(id);
    }

    /** 开单：挂已完成预约 + 勾入至少两首已打星曲目 */
    @PostMapping
    public MasterReleaseView create(@RequestBody MasterReleaseRequest request) {
        return masterReleaseService.create(request);
    }

    /** 重新勾齐件（待齐件 / 会签失败后） */
    @PutMapping("/{id}/items")
    public MasterReleaseView updateItems(@PathVariable Long id, @RequestBody MasterReleaseRequest request) {
        return masterReleaseService.updateItems(id, request);
    }

    /** 提交会签：待齐件 -> 会签中（实时校验，失败即停在会签中） */
    @PostMapping("/{id}/submit-sign")
    public MasterReleaseView submitSign(@PathVariable Long id) {
        return masterReleaseService.submitSign(id);
    }

    /** 失败后重新会签：按当前预约/曲目/房间重新核 */
    @PostMapping("/{id}/retry-sign")
    public MasterReleaseView retrySign(@PathVariable Long id) {
        return masterReleaseService.retrySign(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        masterReleaseService.delete(id);
    }
}
