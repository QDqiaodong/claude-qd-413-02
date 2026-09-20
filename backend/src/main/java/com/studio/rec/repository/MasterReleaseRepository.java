package com.studio.rec.repository;

import com.studio.rec.entity.MasterRelease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MasterReleaseRepository extends JpaRepository<MasterRelease, Long> {
    boolean existsByReleaseNo(String releaseNo);

    /** 会签关键路径用：对单据行加悲观写锁，串行化同一张单的会签并发 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MasterRelease r where r.id = :id")
    Optional<MasterRelease> findByIdForUpdate(@Param("id") Long id);
}
