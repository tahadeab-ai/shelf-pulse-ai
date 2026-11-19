#include "yolo11_ncnn.h"
#include <algorithm>
#include <cmath>
#include <android/log.h>
#include <cpu.h>

#define TAG "ShelfPulseNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace shelfpulse {

static inline float intersection_over_union(const Detection& a, const Detection& b) {
    float x1 = std::max(a.x1, b.x1);
    float y1 = std::max(a.y1, b.y1);
    float x2 = std::min(a.x2, b.x2);
    float y2 = std::min(a.y2, b.y2);

    float inter_w = std::max(0.0f, x2 - x1);
    float inter_h = std::max(0.0f, y2 - y1);
    float inter_area = inter_w * inter_h;

    float area_a = std::max(0.0f, a.x2 - a.x1) * std::max(0.0f, a.y2 - a.y1);
    float area_b = std::max(0.0f, b.x2 - b.x1) * std::max(0.0f, b.y2 - b.y1);
    float union_area = area_a + area_b - inter_area;

    if (union_area <= 0.0f) return 0.0f;
    return inter_area / union_area;
}

Yolo11Ncnn::Yolo11Ncnn()
    : is_loaded_(false), has_gpu_(false), target_size_(640) {
    num_threads_ = ncnn::get_big_cpu_count();
    if (num_threads_ <= 0) num_threads_ = 4;
}

Yolo11Ncnn::~Yolo11Ncnn() {
    std::lock_guard<std::mutex> lock(inference_mutex_);
    net_.clear();
    is_loaded_ = false;
}

bool Yolo11Ncnn::load_model(const std::string& param_path, const std::string& bin_path, bool use_gpu) {
    std::lock_guard<std::mutex> lock(inference_mutex_);
    net_.clear();
    has_gpu_ = false;

    net_.opt = ncnn::Option();
    net_.opt.num_threads = num_threads_;
    net_.opt.use_fp16_packed = true;
    net_.opt.use_fp16_storage = true;
    net_.opt.use_fp16_arithmetic = true;
    net_.opt.use_packing_layout = true;

#if NCNN_VULKAN
    if (use_gpu && ncnn::get_gpu_count() > 0) {
        net_.opt.use_vulkan_compute = true;
        has_gpu_ = true;
        LOGI("Vulkan compute enabled for YOLO11 inference (GPU count: %d)", ncnn::get_gpu_count());
    } else {
        net_.opt.use_vulkan_compute = false;
        LOGI("Using CPU inference with %d threads", num_threads_);
    }
#else
    net_.opt.use_vulkan_compute = false;
    LOGI("Compiled without Vulkan support. Using CPU inference with %d threads", num_threads_);
#endif

    int ret_param = net_.load_param(param_path.c_str());
    if (ret_param != 0) {
        LOGE("Failed to load param file: %s (ret: %d)", param_path.c_str(), ret_param);
        return false;
    }

    int ret_bin = net_.load_model(bin_path.c_str());
    if (ret_bin != 0) {
        LOGE("Failed to load bin file: %s (ret: %d)", bin_path.c_str(), ret_bin);
        return false;
    }

    is_loaded_ = true;
    LOGI("YOLO11 NCNN model loaded successfully (Vulkan: %s)", has_gpu_ ? "YES" : "NO");
    return true;
}

void Yolo11Ncnn::preprocess_letterbox(const ncnn::Mat& in, ncnn::Mat& out, LetterboxInfo& info) {
    int src_w = in.w;
    int src_h = in.h;

    float scale = std::min(static_cast<float>(target_size_) / src_w,
                           static_cast<float>(target_size_) / src_h);

    int unpad_w = static_cast<int>(std::round(src_w * scale));
    int unpad_h = static_cast<int>(std::round(src_h * scale));

    ncnn::Mat in_scaled;
    if (unpad_w != src_w || unpad_h != src_h) {
        ncnn::resize_bilinear(in, in_scaled, unpad_w, unpad_h);
    } else {
        in_scaled = in;
    }

    int pad_w = (target_size_ - unpad_w) / 2;
    int pad_h = (target_size_ - unpad_h) / 2;
    int pad_r = target_size_ - unpad_w - pad_w;
    int pad_b = target_size_ - unpad_h - pad_h;

    ncnn::copy_make_border(in_scaled, out, pad_h, pad_b, pad_w, pad_r,
                           ncnn::BORDER_CONSTANT, 114.0f);

    info.target_w = target_size_;
    info.target_h = target_size_;
    info.scale = scale;
    info.pad_w = static_cast<float>(pad_w);
    info.pad_h = static_cast<float>(pad_h);
}

void Yolo11Ncnn::nms_sorted_bboxes(const std::vector<Detection>& bboxes, std::vector<int>& picked, float nms_threshold) {
    picked.clear();
    const size_t n = bboxes.size();
    if (n == 0) return;

    std::vector<float> areas(n);
    for (size_t i = 0; i < n; i++) {
        areas[i] = (bboxes[i].x2 - bboxes[i].x1) * (bboxes[i].y2 - bboxes[i].y1);
    }

    for (size_t i = 0; i < n; i++) {
        const Detection& a = bboxes[i];
        bool keep = true;
        for (int idx : picked) {
            const Detection& b = bboxes[idx];
            float inter_x1 = std::max(a.x1, b.x1);
            float inter_y1 = std::max(a.y1, b.y1);
            float inter_x2 = std::min(a.x2, b.x2);
            float inter_y2 = std::min(a.y2, b.y2);

            float inter_w = std::max(0.0f, inter_x2 - inter_x1);
            float inter_h = std::max(0.0f, inter_y2 - inter_y1);
            float inter_area = inter_w * inter_h;

            float union_area = areas[i] + areas[idx] - inter_area;
            float iou = (union_area > 0.0f) ? (inter_area / union_area) : 0.0f;

            if (iou > nms_threshold) {
                keep = false;
                break;
            }
        }
        if (keep) {
            picked.push_back(static_cast<int>(i));
        }
    }
}

std::vector<Detection> Yolo11Ncnn::detect(const ImageFrame& frame, float conf_thresh, float nms_thresh) {
    std::lock_guard<std::mutex> lock(inference_mutex_);
    if (!is_loaded_) {
        LOGE("Cannot detect: model is not loaded.");
        return {};
    }

    // Convert raw frame to ncnn::Mat RGB
    ncnn::Mat in_rgb;
    if (frame.channels == 4) {
        in_rgb = ncnn::Mat::from_pixels(frame.data, ncnn::Mat::PIXEL_RGBA2RGB, frame.width, frame.height);
    } else if (frame.channels == 3) {
        in_rgb = ncnn::Mat::from_pixels(frame.data, ncnn::Mat::PIXEL_RGB, frame.width, frame.height);
    } else {
        LOGE("Unsupported frame channel count: %d", frame.channels);
        return {};
    }

    // Apply letterbox preprocessing
    LetterboxInfo info;
    ncnn::Mat in_letterboxed;
    preprocess_letterbox(in_rgb, in_letterboxed, info);

    // Normalize: scale [0, 255] to [0.0, 1.0]
    const float norm_vals[3] = {1 / 255.0f, 1 / 255.0f, 1 / 255.0f};
    in_letterboxed.substract_mean_normalize(nullptr, norm_vals);

    // Run inference
    ncnn::Extractor ex = net_.create_extractor();
    ex.input("in0", in_letterboxed);

    ncnn::Mat out0;
    int ret = ex.extract("out0", out0);
    if (ret != 0) {
        LOGE("Failed to extract layer 'out0' (ret: %d)", ret);
        return {};
    }

    // Auto-detect dynamic output layout: [4 + C, N] vs [N, 4 + C]
    int num_anchors = 0;
    int num_classes = 0;
    bool transpose_layout = false; // true if [4 + C, N]

    if (out0.dims == 2) {
        if (out0.w > out0.h) {
            // Layout: [4 + C, N] where h = 4 + num_classes, w = num_anchors (e.g., 5 x 8400 or 84 x 8400)
            num_anchors = out0.w;
            num_classes = out0.h - 4;
            transpose_layout = true;
        } else {
            // Layout: [N, 4 + C] where w = 4 + num_classes, h = num_anchors (e.g., 8400 x 5)
            num_anchors = out0.h;
            num_classes = out0.w - 4;
            transpose_layout = false;
        }
    } else if (out0.dims == 3) {
        // [1, 4 + C, N] or [1, N, 4 + C]
        if (out0.h > out0.d) {
            num_anchors = out0.h;
            num_classes = out0.d - 4;
            transpose_layout = false;
        } else {
            num_anchors = out0.d;
            num_classes = out0.h - 4;
            transpose_layout = true;
        }
    } else {
        LOGE("Unexpected out0 dimension count: %d", out0.dims);
        return {};
    }

    if (num_classes <= 0 || num_anchors <= 0) {
        LOGE("Invalid tensor shape parsed: num_classes=%d, num_anchors=%d", num_classes, num_anchors);
        return {};
    }

    std::vector<Detection> candidates;
    candidates.reserve(num_anchors / 4);

    // Parse predictions
    // NOTE: Ultralytics PNNX NCNN exports output class scores ALREADY sigmoid-activated!
    for (int i = 0; i < num_anchors; i++) {
        float cx, cy, w, h;
        float max_score = 0.0f;
        int best_class = 0;

        if (transpose_layout) {
            cx = out0.row(0)[i];
            cy = out0.row(1)[i];
            w  = out0.row(2)[i];
            h  = out0.row(3)[i];

            for (int c = 0; c < num_classes; c++) {
                float score = out0.row(4 + c)[i];
                if (score > max_score) {
                    max_score = score;
                    best_class = c;
                }
            }
        } else {
            const float* row_ptr = out0.row(i);
            cx = row_ptr[0];
            cy = row_ptr[1];
            w  = row_ptr[2];
            h  = row_ptr[3];

            for (int c = 0; c < num_classes; c++) {
                float score = row_ptr[4 + c];
                if (score > max_score) {
                    max_score = score;
                    best_class = c;
                }
            }
        }

        if (max_score < conf_thresh) continue;

        // Convert [cx, cy, w, h] to corner coordinates [x1, y1, x2, y2]
        float x1 = cx - w * 0.5f;
        float y1 = cy - h * 0.5f;
        float x2 = cx + w * 0.5f;
        float y2 = cy + h * 0.5f;

        // Reverse letterbox transformation to original frame coordinates
        float orig_x1 = (x1 - info.pad_w) / info.scale;
        float orig_y1 = (y1 - info.pad_h) / info.scale;
        float orig_x2 = (x2 - info.pad_w) / info.scale;
        float orig_y2 = (y2 - info.pad_h) / info.scale;

        // Clamp to original frame boundaries
        orig_x1 = std::clamp(orig_x1, 0.0f, static_cast<float>(frame.width));
        orig_y1 = std::clamp(orig_y1, 0.0f, static_cast<float>(frame.height));
        orig_x2 = std::clamp(orig_x2, 0.0f, static_cast<float>(frame.width));
        orig_y2 = std::clamp(orig_y2, 0.0f, static_cast<float>(frame.height));

        if (orig_x2 - orig_x1 < 2.0f || orig_y2 - orig_y1 < 2.0f) continue;

        candidates.emplace_back(orig_x1, orig_y1, orig_x2, orig_y2, max_score, best_class, -1);
    }

    if (candidates.empty()) return {};

    // Sort descending by score for NMS
    std::sort(candidates.begin(), candidates.end(), [](const Detection& a, const Detection& b) {
        return a.score > b.score;
    });

    std::vector<int> picked;
    nms_sorted_bboxes(candidates, picked, nms_thresh);

    std::vector<Detection> results;
    results.reserve(picked.size());
    for (int idx : picked) {
        results.push_back(candidates[idx]);
    }

    return results;
}

} // namespace shelfpulse
