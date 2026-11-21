#ifndef SHELFPULSE_INTERFACES_H
#define SHELFPULSE_INTERFACES_H

#include <vector>
#include <string>
#include <memory>
#include <cstdint>

namespace shelfpulse {

struct Detection {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int class_id;
    int track_id;

    Detection() : x1(0), y1(0), x2(0), y2(0), score(0.0f), class_id(0), track_id(-1) {}
    Detection(float x1, float y1, float x2, float y2, float score, int class_id, int track_id = -1)
        : x1(x1), y1(y1), x2(x2), y2(y2), score(score), class_id(class_id), track_id(track_id) {}
};

struct ImageFrame {
    const uint8_t* data;
    int width;
    int height;
    int channels;
    int step;
    int rotation_degrees;
};

struct StageTimings {
    float preprocess_ms;
    float infer_ms;
    float track_ms;
    float total_ms;
    float nms_ms;
    int tile_count;
    int raw_box_count;
    int merged_box_count;

    StageTimings()
        : preprocess_ms(0.0f),
          infer_ms(0.0f),
          track_ms(0.0f),
          total_ms(0.0f),
          nms_ms(0.0f),
          tile_count(1),
          raw_box_count(0),
          merged_box_count(0) {}
};

// Abstract Detector Interface
class IDetector {
public:
    virtual ~IDetector() = default;
    virtual bool load_model(const std::string& param_path, const std::string& bin_path, bool use_gpu) = 0;
    virtual std::vector<Detection> detect(const ImageFrame& frame, float conf_thresh, float nms_thresh, int target_size = -1, StageTimings* timings = nullptr) = 0;
    virtual std::vector<Detection> detect_tiled(const ImageFrame& frame, float conf_thresh, float nms_thresh, float overlap_ratio = 0.20f, int grid_size = 2, StageTimings* timings = nullptr) = 0;
    virtual bool is_gpu_active() const = 0;
    virtual bool set_backend(bool use_gpu) = 0;
};

// Abstract Tracker Interface (ByteTrack)
class ITracker {
public:
    virtual ~ITracker() = default;
    virtual std::vector<Detection> update(const std::vector<Detection>& detections) = 0;
    virtual void reset() = 0;
};

// Phase-2 Stub: Feature Embedder for SKU fine-grained recognition
class IEmbedder {
public:
    virtual ~IEmbedder() = default;
    virtual std::vector<float> extract_embedding(const ImageFrame& frame, const Detection& roi) = 0;
    virtual float cosine_similarity(const std::vector<float>& emb1, const std::vector<float>& emb2) = 0;
};

} // namespace shelfpulse

#endif // SHELFPULSE_INTERFACES_H
