#ifndef SHELFPULSE_YOLO11_NCNN_H
#define SHELFPULSE_YOLO11_NCNN_H

#include "interfaces.h"
#include <net.h>
#include <mat.h>
#include <mutex>
#include <vector>
#include <string>

namespace shelfpulse {

class Yolo11Ncnn : public IDetector {
public:
    Yolo11Ncnn();
    ~Yolo11Ncnn() override;

    bool load_model(const std::string& param_path, const std::string& bin_path, bool use_gpu) override;
    std::vector<Detection> detect(const ImageFrame& frame, float conf_thresh, float nms_thresh, int target_size = -1, StageTimings* timings = nullptr) override;
    std::vector<Detection> detect_tiled(const ImageFrame& frame, float conf_thresh, float nms_thresh, float overlap_ratio = 0.20f, StageTimings* timings = nullptr) override;
    bool is_gpu_active() const override { return has_gpu_; }
    bool set_backend(bool use_gpu) override;

    void set_target_size(int target_size) { target_size_ = target_size; }
    int get_target_size() const { return target_size_; }

private:
    struct LetterboxInfo {
        int target_w;
        int target_h;
        float scale;
        float pad_w;
        float pad_h;
    };

    void preprocess_letterbox(const ncnn::Mat& in, ncnn::Mat& out, LetterboxInfo& info, int target_size);
    void decode_detections(
        const ncnn::Mat& out0,
        const LetterboxInfo& info,
        int offset_x,
        int offset_y,
        int max_w,
        int max_h,
        float conf_thresh,
        int active_target_size,
        std::vector<Detection>& candidates
    );
    void nms_sorted_bboxes(const std::vector<Detection>& bboxes, std::vector<int>& picked, float nms_threshold);

    ncnn::Net net_;
    bool is_loaded_;
    bool has_gpu_;
    int num_threads_;
    int target_size_;
    std::string cached_param_path_;
    std::string cached_bin_path_;
    mutable std::mutex inference_mutex_;
};

} // namespace shelfpulse

#endif // SHELFPULSE_YOLO11_NCNN_H
