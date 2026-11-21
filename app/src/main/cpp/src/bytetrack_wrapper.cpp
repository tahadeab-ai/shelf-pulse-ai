#include "bytetrack_wrapper.h"
#include <map>
#include <algorithm>
#include <cmath>

namespace shelfpulse {

// --- Kalman Filter Implementation ---
namespace byte_kalman {

const double KalmanFilter::chi2inv95[10] = {
    0, 3.8415, 5.9915, 7.8147, 9.4877, 11.070, 12.592, 14.067, 15.507, 16.919
};

KalmanFilter::KalmanFilter() {
    int ndim = 4;
    double dt = 1.0;

    _motion_mat = Eigen::Matrix<float, 8, 8, Eigen::RowMajor>::Identity();
    for (int i = 0; i < ndim; i++) {
        _motion_mat(i, ndim + i) = dt;
    }

    _update_mat = Eigen::Matrix<float, 4, 8, Eigen::RowMajor>::Identity();

    _std_weight_position = 1.0f / 20.0f;
    _std_weight_velocity = 1.0f / 160.0f;
}

std::pair<Eigen::Matrix<float, 1, 8, Eigen::RowMajor>, Eigen::Matrix<float, 8, 8, Eigen::RowMajor>>
KalmanFilter::initiate(const DETECTBOX& measurement) {
    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> mean_pos = Eigen::Matrix<float, 1, 8, Eigen::RowMajor>::Zero();
    mean_pos.head<4>() = measurement;

    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> std;
    float h = std::max(measurement(3), 1e-4f);
    std << 2.0f * _std_weight_position * h,
           2.0f * _std_weight_position * h,
           1e-2f,
           2.0f * _std_weight_position * h,
           10.0f * _std_weight_velocity * h,
           10.0f * _std_weight_velocity * h,
           1e-5f,
           10.0f * _std_weight_velocity * h;

    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> var = std.array().square();
    Eigen::Matrix<float, 8, 8, Eigen::RowMajor> covariance = var.asDiagonal();
    return {mean_pos, covariance};
}

void KalmanFilter::predict(Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
                          Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance) {
    float h = std::max(mean(3), 1e-4f);
    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> std;
    std << _std_weight_position * h,
           _std_weight_position * h,
           1e-2f,
           _std_weight_position * h,
           _std_weight_velocity * h,
           _std_weight_velocity * h,
           1e-5f,
           _std_weight_velocity * h;

    Eigen::Matrix<float, 8, 8, Eigen::RowMajor> motion_cov = std.array().square().matrix().asDiagonal();
    mean = (mean * _motion_mat.transpose()).eval();
    covariance = (_motion_mat * covariance * _motion_mat.transpose() + motion_cov).eval();
}

std::pair<Eigen::Matrix<float, 1, 4, Eigen::RowMajor>, Eigen::Matrix<float, 4, 4, Eigen::RowMajor>>
KalmanFilter::project(const Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
                      const Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance) {
    float h = std::max(mean(3), 1e-4f);
    Eigen::Matrix<float, 1, 4, Eigen::RowMajor> std;
    std << _std_weight_position * h,
           _std_weight_position * h,
           1e-1f,
           _std_weight_position * h;

    Eigen::Matrix<float, 4, 4, Eigen::RowMajor> diag = std.array().square().matrix().asDiagonal();
    Eigen::Matrix<float, 1, 4, Eigen::RowMajor> proj_mean = mean * _update_mat.transpose();
    Eigen::Matrix<float, 4, 4, Eigen::RowMajor> proj_cov = _update_mat * covariance * _update_mat.transpose() + diag;
    return {proj_mean, proj_cov};
}

std::pair<Eigen::Matrix<float, 1, 8, Eigen::RowMajor>, Eigen::Matrix<float, 8, 8, Eigen::RowMajor>>
KalmanFilter::update(const Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
                     const Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance,
                     const DETECTBOX& measurement) {
    auto proj = project(mean, covariance);
    Eigen::Matrix<float, 1, 4, Eigen::RowMajor> proj_mean = proj.first;
    Eigen::Matrix<float, 4, 4, Eigen::RowMajor> proj_cov = proj.second;

    Eigen::Matrix<float, 8, 4, Eigen::RowMajor> kalman_gain =
        covariance * _update_mat.transpose() * proj_cov.inverse();
    Eigen::Matrix<float, 1, 4, Eigen::RowMajor> innovation = measurement - proj_mean;

    Eigen::Matrix<float, 1, 8, Eigen::RowMajor> new_mean = mean + innovation * kalman_gain.transpose();
    Eigen::Matrix<float, 8, 8, Eigen::RowMajor> new_cov =
        covariance - kalman_gain * proj_cov * kalman_gain.transpose();
    return {new_mean, new_cov};
}

} // namespace byte_kalman

// --- STrack Implementation ---

int STrack::s_count = 0;

int STrack::next_id() {
    s_count++;
    return s_count;
}

void STrack::reset_id_counter() {
    s_count = 0;
}

STrack::STrack(const std::vector<float>& tlwh, float score, int class_id)
    : is_activated(false), track_id(0), state(TrackState::New),
      _tlwh(tlwh), score(score), class_id(class_id), frame_id(0),
      tracklet_len(0), start_frame(0) {}

std::vector<float> STrack::tlwh() const {
    if (mean.size() == 0) return _tlwh;
    float w = std::max(1e-4f, mean(2) * mean(3));
    float h = std::max(1e-4f, mean(3));
    float x1 = mean(0) - w / 2.0f;
    float y1 = mean(1) - h / 2.0f;
    return {x1, y1, w, h};
}

std::vector<float> STrack::tlbr() const {
    auto box = tlwh();
    float x1 = std::clamp(box[0], 0.0f, 1.0f);
    float y1 = std::clamp(box[1], 0.0f, 1.0f);
    float x2 = std::clamp(box[0] + box[2], x1, 1.0f);
    float y2 = std::clamp(box[1] + box[3], y1, 1.0f);
    return {x1, y1, x2, y2};
}

void STrack::activate(byte_kalman::KalmanFilter& kalman_filter, int frame_id) {
    this->track_id = STrack::next_id();
    float w = std::max(_tlwh[2], 1e-4f);
    float h = std::max(_tlwh[3], 1e-4f);
    byte_kalman::DETECTBOX box;
    box << _tlwh[0] + w / 2.0f, _tlwh[1] + h / 2.0f, w / h, h;

    auto init = kalman_filter.initiate(box);
    this->mean = init.first;
    this->covariance = init.second;
    this->tracklet_len = 0;
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->frame_id = frame_id;
    this->start_frame = frame_id;
}

void STrack::re_activate(STrack& new_track, int frame_id, bool new_id) {
    auto new_box = new_track.tlwh();
    float w = std::max(new_box[2], 1e-4f);
    float h = std::max(new_box[3], 1e-4f);
    byte_kalman::DETECTBOX box;
    box << new_box[0] + w / 2.0f, new_box[1] + h / 2.0f, w / h, h;

    byte_kalman::KalmanFilter kf;
    auto res = kf.update(this->mean, this->covariance, box);
    this->mean = res.first;
    this->covariance = res.second;
    this->tracklet_len = 0;
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->frame_id = frame_id;
    this->score = new_track.score;
    this->class_id = new_track.class_id;
    if (new_id) {
        this->track_id = STrack::next_id();
    }
}

void STrack::update(STrack& new_track, int frame_id) {
    this->frame_id = frame_id;
    this->tracklet_len++;

    auto new_box = new_track.tlwh();
    float w = std::max(new_box[2], 1e-4f);
    float h = std::max(new_box[3], 1e-4f);
    byte_kalman::DETECTBOX box;
    box << new_box[0] + w / 2.0f, new_box[1] + h / 2.0f, w / h, h;

    byte_kalman::KalmanFilter kf;
    auto res = kf.update(this->mean, this->covariance, box);
    this->mean = res.first;
    this->covariance = res.second;
    this->state = TrackState::Tracked;
    this->is_activated = true;
    this->score = new_track.score;
    this->class_id = new_track.class_id;
}

void STrack::multi_predict(std::vector<STrack*>& stracks, byte_kalman::KalmanFilter& kalman_filter) {
    for (auto* tr : stracks) {
        if (tr->state != TrackState::Tracked) {
            tr->mean(7) = 0.0f;
        }
        kalman_filter.predict(tr->mean, tr->covariance);
    }
}

// --- Association Utils ---

static float iou_distance(const std::vector<float>& box_a, const std::vector<float>& box_b) {
    float x1 = std::max(box_a[0], box_b[0]);
    float y1 = std::max(box_a[1], box_b[1]);
    float x2 = std::min(box_a[2], box_b[2]);
    float y2 = std::min(box_a[3], box_b[3]);

    float inter_w = std::max(0.0f, x2 - x1);
    float inter_h = std::max(0.0f, y2 - y1);
    float inter_area = inter_w * inter_h;

    float area_a = std::max(0.0f, box_a[2] - box_a[0]) * std::max(0.0f, box_a[3] - box_a[1]);
    float area_b = std::max(0.0f, box_b[2] - box_b[0]) * std::max(0.0f, box_b[3] - box_b[1]);
    float union_area = area_a + area_b - inter_area;

    if (union_area <= 0.0f) return 1.0f;
    return std::clamp(1.0f - (inter_area / union_area), 0.0f, 1.0f);
}

// Greedy IoU matching for robust association
static void linear_assignment(
    const std::vector<std::vector<float>>& cost_matrix,
    float thresh,
    std::vector<std::pair<int, int>>& matches,
    std::vector<int>& unmatched_a,
    std::vector<int>& unmatched_b) {

    matches.clear();
    unmatched_a.clear();
    unmatched_b.clear();

    if (cost_matrix.empty() || cost_matrix[0].empty()) {
        for (size_t i = 0; i < cost_matrix.size(); i++) unmatched_a.push_back(i);
        return;
    }

    size_t num_a = cost_matrix.size();
    size_t num_b = cost_matrix[0].size();

    std::vector<bool> used_a(num_a, false);
    std::vector<bool> used_b(num_b, false);

    struct PairCost {
        int a;
        int b;
        float cost;
    };

    std::vector<PairCost> all_pairs;
    all_pairs.reserve(num_a * num_b);
    for (size_t i = 0; i < num_a; i++) {
        for (size_t j = 0; j < num_b; j++) {
            if (cost_matrix[i][j] <= thresh) {
                all_pairs.push_back({(int)i, (int)j, cost_matrix[i][j]});
            }
        }
    }

    std::sort(all_pairs.begin(), all_pairs.end(), [](const PairCost& p1, const PairCost& p2) {
        return p1.cost < p2.cost;
    });

    for (const auto& p : all_pairs) {
        if (!used_a[p.a] && !used_b[p.b]) {
            used_a[p.a] = true;
            used_b[p.b] = true;
            matches.emplace_back(p.a, p.b);
        }
    }

    for (size_t i = 0; i < num_a; i++) {
        if (!used_a[i]) unmatched_a.push_back(i);
    }
    for (size_t j = 0; j < num_b; j++) {
        if (!used_b[j]) unmatched_b.push_back(j);
    }
}

// --- BYTETracker Implementation ---

BYTETracker::BYTETracker(int frame_rate, int track_buffer)
    : track_thresh_(0.20f), high_thresh_(0.30f), match_thresh_(0.8f),
      frame_id_(0), max_time_lost_(track_buffer) {}

void BYTETracker::reset() {
    tracked_stracks_.clear();
    lost_stracks_.clear();
    removed_stracks_.clear();
    frame_id_ = 0;
    STrack::reset_id_counter();
}

std::vector<STrack> BYTETracker::update(const std::vector<STrack>& detections) {
    frame_id_++;

    std::vector<STrack> activated_stracks;
    std::vector<STrack> refind_stracks;
    std::vector<STrack> lost_stracks;
    std::vector<STrack> removed_stracks;

    std::vector<STrack> detections_high;
    std::vector<STrack> detections_low;

    for (const auto& det : detections) {
        auto box = det.tlwh();
        float area = box[2] * box[3];
        if (box[2] <= 0.0f || box[3] <= 0.0f || area <= 1e-7f) continue;
        if (det.score >= track_thresh_) {
            detections_high.push_back(det);
        } else if (det.score >= 0.1f) {
            detections_low.push_back(det);
        }
    }

    // Predict existing tracks
    std::vector<STrack*> strack_pool;
    for (auto& tr : tracked_stracks_) strack_pool.push_back(&tr);
    for (auto& tr : lost_stracks_) strack_pool.push_back(&tr);
    STrack::multi_predict(strack_pool, kalman_filter_);

    // 1st association: High score detections with tracked stracks
    std::vector<std::vector<float>> dists1(tracked_stracks_.size(), std::vector<float>(detections_high.size(), 1.0f));
    for (size_t i = 0; i < tracked_stracks_.size(); i++) {
        auto tlbr_a = tracked_stracks_[i].tlbr();
        for (size_t j = 0; j < detections_high.size(); j++) {
            auto tlbr_b = detections_high[j].tlbr();
            dists1[i][j] = iou_distance(tlbr_a, tlbr_b);
        }
    }

    std::vector<std::pair<int, int>> matches1;
    std::vector<int> u_track1, u_detection1;
    linear_assignment(dists1, match_thresh_, matches1, u_track1, u_detection1);

    for (const auto& match : matches1) {
        tracked_stracks_[match.first].update(detections_high[match.second], frame_id_);
        activated_stracks.push_back(tracked_stracks_[match.first]);
    }

    // 2nd association: Low score detections with remaining unmatched tracked stracks
    std::vector<STrack*> r_tracked_stracks;
    for (int idx : u_track1) {
        if (tracked_stracks_[idx].state == TrackState::Tracked) {
            r_tracked_stracks.push_back(&tracked_stracks_[idx]);
        }
    }

    std::vector<std::vector<float>> dists2(r_tracked_stracks.size(), std::vector<float>(detections_low.size(), 1.0f));
    for (size_t i = 0; i < r_tracked_stracks.size(); i++) {
        auto tlbr_a = r_tracked_stracks[i]->tlbr();
        for (size_t j = 0; j < detections_low.size(); j++) {
            auto tlbr_b = detections_low[j].tlbr();
            dists2[i][j] = iou_distance(tlbr_a, tlbr_b);
        }
    }

    std::vector<std::pair<int, int>> matches2;
    std::vector<int> u_track2, u_detection2;
    linear_assignment(dists2, 0.5f, matches2, u_track2, u_detection2);

    for (const auto& match : matches2) {
        r_tracked_stracks[match.first]->update(detections_low[match.second], frame_id_);
        activated_stracks.push_back(*r_tracked_stracks[match.first]);
    }

    for (int idx : u_track2) {
        r_tracked_stracks[idx]->state = TrackState::Lost;
        lost_stracks.push_back(*r_tracked_stracks[idx]);
    }

    // Deal with unmatched high-score detections (new tracks)
    // Frame 1 detections and high-confidence detections (>= 0.30f) activate immediately
    for (int idx : u_detection1) {
        STrack track = detections_high[idx];
        if (track.score >= high_thresh_ || frame_id_ == 1) {
            track.activate(kalman_filter_, frame_id_);
            activated_stracks.push_back(track);
        }
    }

    // Update state lists
    tracked_stracks_ = activated_stracks;

    std::vector<STrack> new_lost;
    for (const auto& tr : lost_stracks) {
        if (frame_id_ - tr.frame_id < max_time_lost_) {
            new_lost.push_back(tr);
        } else {
            removed_stracks.push_back(tr);
        }
    }
    for (const auto& tr : lost_stracks_) {
        if (frame_id_ - tr.frame_id < max_time_lost_) {
            bool refound = false;
            for (const auto& act : activated_stracks) {
                if (act.track_id == tr.track_id) {
                    refound = true;
                    break;
                }
            }
            if (!refound) {
                new_lost.push_back(tr);
            }
        }
    }
    lost_stracks_ = std::move(new_lost);

    std::vector<STrack> output_stracks;
    for (const auto& tr : tracked_stracks_) {
        if (tr.is_activated) output_stracks.push_back(tr);
    }

    return output_stracks;
}

// --- ByteTrackWrapper Implementation ---

ByteTrackWrapper::ByteTrackWrapper(int frame_rate, int track_buffer)
    : tracker_(std::make_unique<BYTETracker>(frame_rate, track_buffer)) {}

void ByteTrackWrapper::reset() {
    std::lock_guard<std::mutex> lock(tracker_mutex_);
    tracker_->reset();
}

std::vector<Detection> ByteTrackWrapper::update(const std::vector<Detection>& detections) {
    std::lock_guard<std::mutex> lock(tracker_mutex_);

    std::vector<STrack> stracks_in;
    stracks_in.reserve(detections.size());
    for (const auto& det : detections) {
        float x1 = std::clamp(det.x1, 0.0f, 1.0f);
        float y1 = std::clamp(det.y1, 0.0f, 1.0f);
        float x2 = std::clamp(det.x2, x1, 1.0f);
        float y2 = std::clamp(det.y2, y1, 1.0f);
        float w = x2 - x1;
        float h = y2 - y1;
        float area = w * h;
        if (w <= 0.0f || h <= 0.0f || area <= 1e-7f) continue;
        std::vector<float> tlwh = {x1, y1, w, h};
        stracks_in.emplace_back(tlwh, det.score, det.class_id);
    }

    auto tracked = tracker_->update(stracks_in);

    std::vector<Detection> results;
    results.reserve(tracked.size());
    for (const auto& tr : tracked) {
        auto tlbr = tr.tlbr();
        results.emplace_back(tlbr[0], tlbr[1], tlbr[2], tlbr[3], tr.score, tr.class_id, tr.track_id);
    }
    return results;
}

} // namespace shelfpulse
