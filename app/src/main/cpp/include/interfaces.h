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

// Abstract Detector Interface
class IDetector {
public:
    virtual ~IDetector() = default;
    virtual bool load_model(const std::string& param_path, const std::string& bin_path, bool use_gpu) = 0;
    virtual std::vector<Detection> detect(const ImageFrame& frame, float conf_thresh, float nms_thresh) = 0;
    virtual bool is_gpu_active() const = 0;
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
