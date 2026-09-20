package com.studio.rec.service;

import com.studio.rec.dto.BizException;
import com.studio.rec.entity.Track;
import com.studio.rec.repository.BookingRepository;
import com.studio.rec.repository.TrackRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrackService {
    private final TrackRepository trackRepository;
    private final BookingRepository bookingRepository;

    public TrackService(TrackRepository trackRepository, BookingRepository bookingRepository) {
        this.trackRepository = trackRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<Track> list() {
        return trackRepository.findAll();
    }

    public Track get(Long id) {
        return trackRepository.findById(id)
                .orElseThrow(() -> new BizException("曲目不存在"));
    }

    private void validateRating(Integer rating) {
        // 0 用于清星（置为未打星）；有值时必须落在 1~5
        if (rating != null && rating != 0 && (rating < 1 || rating > 5)) {
            throw new BizException("评分需在 1~5 之间");
        }
    }

    public Track create(Track input) {
        if (input.getBookingId() == null) {
            throw new BizException("曲目归属的预约不能为空");
        }
        bookingRepository.findById(input.getBookingId())
                .orElseThrow(() -> new BizException("曲目归属的预约不存在"));
        if (input.getName() == null || input.getName().isBlank()) {
            throw new BizException("曲名不能为空");
        }
        if (input.getDuration() == null || input.getDuration() <= 0) {
            throw new BizException("时长需大于 0");
        }
        validateRating(input.getRating());
        Track track = new Track();
        track.setBookingId(input.getBookingId());
        track.setName(input.getName());
        track.setDuration(input.getDuration());
        track.setRating(normalizeRating(input.getRating()));
        return trackRepository.save(track);
    }

    public Track update(Long id, Track input) {
        Track track = get(id);
        if (input.getName() != null && !input.getName().isBlank()) {
            track.setName(input.getName());
        }
        if (input.getDuration() != null && input.getDuration() > 0) {
            track.setDuration(input.getDuration());
        }
        if (input.getRating() != null) {
            validateRating(input.getRating());
            track.setRating(normalizeRating(input.getRating()));
        }
        return trackRepository.save(track);
    }

    private Integer normalizeRating(Integer rating) {
        return rating != null && rating == 0 ? null : rating;
    }

    public void delete(Long id) {
        trackRepository.deleteById(id);
    }
}
