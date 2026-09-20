package com.studio.rec.repository;

import com.studio.rec.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByBookingId(Long bookingId);
}
