#ifndef SHELFPULSE_BYTETRACK_H
#define SHELFPULSE_BYTETRACK_H

#include "strack.h"
#include <vector>

namespace shelfpulse {

class BYTETracker {
public:
    BYTETracker(int frame_rate = 30, int track_buffer = 60);
    ~BYTETracker() = default;

    std::vector<STrack> update(const std::vector<STrack>& detections);
    void reset();

private:
    float track_thresh_;
    float high_thresh_;
    float match_thresh_;
    int frame_id_;
    int max_time_lost_;

    byte_kalman::KalmanFilter kalman_filter_;
    std::vector<STrack> tracked_stracks_;
    std::vector<STrack> lost_stracks_;
    std::vector<STrack> removed_stracks_;
};

} // namespace shelfpulse

#endif // SHELFPULSE_BYTETRACK_H
