package com.studio.rec.repository;

import com.studio.rec.entity.MasterReleaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MasterReleaseItemRepository extends JpaRepository<MasterReleaseItem, Long> {
    List<MasterReleaseItem> findByReleaseIdOrderByIdAsc(Long releaseId);

    void deleteByReleaseId(Long releaseId);
}
