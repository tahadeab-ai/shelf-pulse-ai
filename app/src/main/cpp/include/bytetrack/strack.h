#ifndef SHELFPULSE_STRACK_H
#define SHELFPULSE_STRACK_H

#include "kalman_filter.h"
#include <vector>

namespace shelfpulse {

enum class TrackState {
    New = 0,
    Tracked = 1,
    Lost = 2,
    Removed = 3
};

class STrack {
public:
    STrack(const std::vector<float>& tlwh, float score, int class_id);
    ~STrack() = default;

    void static_init();
    void activate(byte_kalman::KalmanFilter& kalman_filter, int frame_id);
    void re_activate(STrack& new_track, int frame_id, bool new_id = false);
    void update(STrack& new_track, int frame_id);

    std::vector<float> tlwh() const;
    std::vector<float> tlbr() const;

    static void multi_predict(std::vector<STrack*>& stracks, byte_kalman::KalmanFilter& kalman_filter);

    bool is_activated;
    int track_id;
    TrackState state;

    std::vector<float> _tlwh;
    float score;
    int class_id;
    int frame_id;
    int tracklet_len;
    int start_frame;

    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> mean;
    Eigen::Matrix<float, 8, 8, Eigen::RowMajor> covariance;

    static int next_id();
    static void reset_id_counter();

private:
    static int s_count;
};

} // namespace shelfpulse

#endif // SHELFPULSE_STRACK_H
