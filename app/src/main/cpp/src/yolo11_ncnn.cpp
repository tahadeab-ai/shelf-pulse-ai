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

static inline float sigmoid(float x) {
    return 1.0f / (1.0f + std::exp(-x));
}

static inline void softmax16(const float* src, float* dst) {
    float max_val = src[0];
    for (int i = 1; i < 16; i++) {
        if (src[i] > max_val) max_val = src[i];
    }
    float sum = 0.0f;
    for (int i = 0; i < 16; i++) {
        dst[i] = std::exp(src[i] - max_val);
        sum += dst[i];
    }
    float inv_sum = 1.0f / sum;
    for (int i = 0; i < 16; i++) {
        dst[i] *= inv_sum;
    }
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
    cached_param_path_ = param_path;
    cached_bin_path_ = bin_path;

    // Pin CPU worker threads strictly to big Kryo 485 performance cores
    ncnn::set_cpu_powersave(2);

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
    LOGI("YOLO11 NCNN model loaded successfully (Vulkan: %s, threads: %d)", has_gpu_ ? "YES" : "NO", num_threads_);
    return true;
}

bool Yolo11Ncnn::set_backend(bool use_gpu) {
    if (cached_param_path_.empty() || cached_bin_path_.empty()) {
        return false;
    }
    return load_model(cached_param_path_, cached_bin_path_, use_gpu);
}

void Yolo11Ncnn::preprocess_letterbox(const ncnn::Mat& in, ncnn::Mat& out, LetterboxInfo& info, int target_size) {
    int src_w = in.w;
    int src_h = in.h;

    float scale = std::min(static_cast<float>(target_size) / src_w,
                           static_cast<float>(target_size) / src_h);

    int unpad_w = static_cast<int>(std::round(src_w * scale));
    int unpad_h = static_cast<int>(std::round(src_h * scale));

    ncnn::Mat in_scaled;
    if (unpad_w != src_w || unpad_h != src_h) {
        ncnn::resize_bilinear(in, in_scaled, unpad_w, unpad_h);
    } else {
        in_scaled = in;
    }

    int pad_w = (target_size - unpad_w) / 2;
    int pad_h = (target_size - unpad_h) / 2;
    int pad_r = target_size - unpad_w - pad_w;
    int pad_b = target_size - unpad_h - pad_h;

    ncnn::copy_make_border(in_scaled, out, pad_h, pad_b, pad_w, pad_r,
                           ncnn::BORDER_CONSTANT, 114.0f);

    info.target_w = target_size;
    info.target_h = target_size;
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

std::vector<Detection> Yolo11Ncnn::detect(const ImageFrame& frame, float conf_thresh, float nms_thresh, int target_size, StageTimings* timings) {
    auto prep_start = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(inference_mutex_);
    if (!is_loaded_) {
        LOGE("Cannot detect: model is not loaded.");
        return {};
    }

    // Pin CPU worker threads strictly to big Kryo 485 performance cores
    ncnn::set_cpu_powersave(2);

    // Dynamic resolution: 320 for Live Scan (Fast), 640 for Snap & Verify (HD)
    int active_target_size = (target_size > 0) ? target_size : target_size_;

    // Convert raw frame to ncnn::Mat RGB with explicit row stride
    ncnn::Mat in_rgb;
    int step_bytes = (frame.step > 0) ? frame.step : (frame.width * frame.channels);
    if (frame.channels == 4) {
        in_rgb = ncnn::Mat::from_pixels(frame.data, ncnn::Mat::PIXEL_RGBA2RGB, frame.width, frame.height, step_bytes);
    } else if (frame.channels == 3) {
        in_rgb = ncnn::Mat::from_pixels(frame.data, ncnn::Mat::PIXEL_RGB, frame.width, frame.height, step_bytes);
    } else {
        LOGE("Unsupported frame channel count: %d", frame.channels);
        return {};
    }

    // Apply letterbox preprocessing
    LetterboxInfo info;
    ncnn::Mat in_letterboxed;
    preprocess_letterbox(in_rgb, in_letterboxed, info, active_target_size);

    // Normalize: scale [0, 255] to [0.0, 1.0]
    const float norm_vals[3] = {1 / 255.0f, 1 / 255.0f, 1 / 255.0f};
    in_letterboxed.substract_mean_normalize(nullptr, norm_vals);

    auto prep_end = std::chrono::high_resolution_clock::now();
    if (timings) {
        timings->preprocess_ms = std::chrono::duration<float, std::milli>(prep_end - prep_start).count();
    }

    // Run inference
    auto inf_start = std::chrono::high_resolution_clock::now();
    ncnn::Extractor ex = net_.create_extractor();
    ex.input("in0", in_letterboxed);

    ncnn::Mat out0;
    int ret = ex.extract("out0", out0);
    if (ret != 0) {
        LOGE("Failed to extract layer 'out0' (ret: %d)", ret);
        return {};
    }

    std::vector<Detection> candidates;

    // Check if raw dynamic multi-stride tensor from PNNX export: [N, 64 + C]
    // where w = 64 bbox reg + C class scores (65 for SKU-110K 1-class), h = N anchors (2100 or 8400)
    if (out0.dims == 2 && out0.w >= 65) {
        const int reg_max_1 = 16;
        const int num_class = out0.w - reg_max_1 * 4;
        const int strides[3] = {8, 16, 32};

        candidates.reserve(out0.h / 4);

        int pred_row_offset = 0;
        for (int stride : strides) {
            int num_grid_x = active_target_size / stride;
            int num_grid_y = active_target_size / stride;
            int num_grid = num_grid_x * num_grid_y;

            if (pred_row_offset + num_grid > out0.h) {
                LOGW("Grid overflow in stride %d: offset %d + grid %d > total %d",
                     stride, pred_row_offset, num_grid, out0.h);
                break;
            }

            for (int y = 0; y < num_grid_y; y++) {
                for (int x = 0; x < num_grid_x; x++) {
                    const float* row_ptr = out0.row(pred_row_offset + y * num_grid_x + x);

                    // Find label with max score
                    int best_label = 0;
                    float max_score = -1e9f;
                    for (int k = 0; k < num_class; k++) {
                        float s = row_ptr[reg_max_1 * 4 + k];
                        if (s > max_score) {
                            max_score = s;
                            best_label = k;
                        }
                    }

                    float score = sigmoid(max_score);
                    if (score < conf_thresh) continue;

                    // Decode DFL bboxes: 4 sides * 16 bins
                    float pred_ltrb[4];
                    float sm_buf[16];
                    for (int k = 0; k < 4; k++) {
                        softmax16(row_ptr + k * 16, sm_buf);
                        float dis = 0.0f;
                        for (int l = 0; l < 16; l++) {
                            dis += l * sm_buf[l];
                        }
                        pred_ltrb[k] = dis * stride;
                    }

                    float pb_cx = (x + 0.5f) * stride;
                    float pb_cy = (y + 0.5f) * stride;

                    float x0 = pb_cx - pred_ltrb[0];
                    float y0 = pb_cy - pred_ltrb[1];
                    float x1 = pb_cx + pred_ltrb[2];
                    float y1 = pb_cy + pred_ltrb[3];

                    // Reverse letterbox transformation to original frame coordinates
                    float orig_x1 = (x0 - info.pad_w) / info.scale;
                    float orig_y1 = (y0 - info.pad_h) / info.scale;
                    float orig_x2 = (x1 - info.pad_w) / info.scale;
                    float orig_y2 = (y1 - info.pad_h) / info.scale;

                    orig_x1 = std::clamp(orig_x1, 0.0f, static_cast<float>(frame.width));
                    orig_y1 = std::clamp(orig_y1, 0.0f, static_cast<float>(frame.height));
                    orig_x2 = std::clamp(orig_x2, 0.0f, static_cast<float>(frame.width));
                    orig_y2 = std::clamp(orig_y2, 0.0f, static_cast<float>(frame.height));

                    if (orig_x2 - orig_x1 < 2.0f || orig_y2 - orig_y1 < 2.0f) continue;

                    candidates.emplace_back(orig_x1, orig_y1, orig_x2, orig_y2, score, best_label, -1);
                }
            }
            pred_row_offset += num_grid;
        }
    } else {
        // Fallback for static/already-decoded models ([4 + C, N] or [N, 4 + C])
        int num_anchors = 0;
        int num_classes = 0;
        bool transpose_layout = false; // true if [4 + C, N]

        if (out0.dims == 2) {
            if (out0.w > out0.h) {
                num_anchors = out0.w;
                num_classes = out0.h - 4;
                transpose_layout = true;
            } else {
                num_anchors = out0.h;
                num_classes = out0.w - 4;
                transpose_layout = false;
            }
        } else if (out0.dims == 3) {
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

        candidates.reserve(num_anchors / 4);

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

            float x1 = cx - w * 0.5f;
            float y1 = cy - h * 0.5f;
            float x2 = cx + w * 0.5f;
            float y2 = cy + h * 0.5f;

            float orig_x1 = (x1 - info.pad_w) / info.scale;
            float orig_y1 = (y1 - info.pad_h) / info.scale;
            float orig_x2 = (x2 - info.pad_w) / info.scale;
            float orig_y2 = (y2 - info.pad_h) / info.scale;

            orig_x1 = std::clamp(orig_x1, 0.0f, static_cast<float>(frame.width));
            orig_y1 = std::clamp(orig_y1, 0.0f, static_cast<float>(frame.height));
            orig_x2 = std::clamp(orig_x2, 0.0f, static_cast<float>(frame.width));
            orig_y2 = std::clamp(orig_y2, 0.0f, static_cast<float>(frame.height));

            if (orig_x2 - orig_x1 < 2.0f || orig_y2 - orig_y1 < 2.0f) continue;

            candidates.emplace_back(orig_x1, orig_y1, orig_x2, orig_y2, max_score, best_class, -1);
        }
    }

    auto inf_end = std::chrono::high_resolution_clock::now();
    if (timings) {
        timings->infer_ms = std::chrono::duration<float, std::milli>(inf_end - inf_start).count();
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
