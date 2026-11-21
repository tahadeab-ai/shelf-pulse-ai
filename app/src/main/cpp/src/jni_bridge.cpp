#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <chrono>
#include <memory>
#include <mutex>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#include "yolo11_ncnn.h"
#include "bytetrack_wrapper.h"

#define TAG "ShelfPulseNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::unique_ptr<shelfpulse::Yolo11Ncnn> g_detector;
static std::unique_ptr<shelfpulse::ByteTrackWrapper> g_tracker;
static std::mutex g_engine_mutex;

// Reusable buffers to eliminate per-frame heap allocations
static std::vector<float> s_jfloat_buffer;
static std::vector<uint8_t> s_rgba_contiguous;
static std::vector<uint8_t> s_rotated_rgba;
static std::vector<uint8_t> s_nv21_buffer;

static jfloatArray pack_detections_to_jfloat_array(
    JNIEnv* env,
    const std::vector<shelfpulse::Detection>& detections,
    const shelfpulse::StageTimings& timings,
    bool is_gpu) {

    // Output packet format:
    // [0]: detection count (N)
    // [1]: total_ms
    // [2]: is_gpu flag (1.0f or 0.0f)
    // [3]: preprocess_ms
    // [4]: infer_ms
    // [5]: track_ms
    // [6]: nms_ms
    // [7]: tile_count
    // [8]: raw_box_count
    // [9]: merged_box_count
    // Then for each of N detections (7 floats per detection):
    // [10 + i*7 + 0]: x1
    // [10 + i*7 + 1]: y1
    // [10 + i*7 + 2]: x2
    // [10 + i*7 + 3]: y2
    // [10 + i*7 + 4]: score
    // [10 + i*7 + 5]: class_id
    // [10 + i*7 + 6]: track_id

    size_t num_dets = detections.size();
    size_t total_floats = 10 + num_dets * 7;

    s_jfloat_buffer.resize(total_floats);
    s_jfloat_buffer[0] = static_cast<float>(num_dets);
    s_jfloat_buffer[1] = timings.total_ms;
    s_jfloat_buffer[2] = is_gpu ? 1.0f : 0.0f;
    s_jfloat_buffer[3] = timings.preprocess_ms;
    s_jfloat_buffer[4] = timings.infer_ms;
    s_jfloat_buffer[5] = timings.track_ms;
    s_jfloat_buffer[6] = timings.nms_ms;
    s_jfloat_buffer[7] = static_cast<float>(timings.tile_count);
    s_jfloat_buffer[8] = static_cast<float>(timings.raw_box_count);
    s_jfloat_buffer[9] = static_cast<float>(timings.merged_box_count);

    for (size_t i = 0; i < num_dets; i++) {
        size_t offset = 10 + i * 7;
        s_jfloat_buffer[offset + 0] = detections[i].x1;
        s_jfloat_buffer[offset + 1] = detections[i].y1;
        s_jfloat_buffer[offset + 2] = detections[i].x2;
        s_jfloat_buffer[offset + 3] = detections[i].y2;
        s_jfloat_buffer[offset + 4] = detections[i].score;
        s_jfloat_buffer[offset + 5] = static_cast<float>(detections[i].class_id);
        s_jfloat_buffer[offset + 6] = static_cast<float>(detections[i].track_id);
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(total_floats));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(total_floats), s_jfloat_buffer.data());
    }
    return result;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jstring param_path,
    jstring bin_path,
    jboolean use_gpu) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);

    const char* c_param = env->GetStringUTFChars(param_path, nullptr);
    const char* c_bin = env->GetStringUTFChars(bin_path, nullptr);

    g_detector = std::make_unique<shelfpulse::Yolo11Ncnn>();
    g_tracker = std::make_unique<shelfpulse::ByteTrackWrapper>(30, 30);

    bool loaded = g_detector->load_model(c_param, c_bin, use_gpu);

    env->ReleaseStringUTFChars(param_path, c_param);
    env->ReleaseStringUTFChars(bin_path, c_bin);

    LOGI("Native engine init result: %s (GPU: %s)",
         loaded ? "SUCCESS" : "FAILED",
         (g_detector && g_detector->is_gpu_active()) ? "TRUE" : "FALSE");
    return static_cast<jboolean>(loaded);
}

JNIEXPORT jboolean JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeSetBackend(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jboolean use_gpu) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_detector) {
        LOGE("nativeSetBackend called before nativeInit()");
        return JNI_FALSE;
    }
    bool success = g_detector->set_backend(use_gpu);
    LOGI("nativeSetBackend toggled: use_gpu=%d, success=%d (GPU active: %s)",
         use_gpu ? 1 : 0, success ? 1 : 0, g_detector->is_gpu_active() ? "YES" : "NO");
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeDetectRgba(
    JNIEnv* env,
    jobject /* thiz */,
    jobject rgba_buffer,
    jint width,
    jint height,
    jint row_stride,
    jint rotation_degrees,
    jfloat conf_thresh,
    jfloat nms_thresh,
    jboolean enable_tracking,
    jint target_size) {

    auto total_start = std::chrono::high_resolution_clock::now();

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    shelfpulse::StageTimings timings;
    if (!g_detector) {
        LOGE("nativeDetectRgba called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    const auto* rgba_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(rgba_buffer));
    if (!rgba_ptr) {
        LOGE("rgba_buffer direct address is null");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    auto prep_start = std::chrono::high_resolution_clock::now();

    // 1. Stride compensation using reusable buffer
    const uint8_t* src_rgba = rgba_ptr;
    int expected_stride = width * 4;
    if (row_stride != expected_stride) {
        s_rgba_contiguous.resize(width * height * 4);
        for (int r = 0; r < height; r++) {
            memcpy(s_rgba_contiguous.data() + r * expected_stride,
                   rgba_ptr + r * row_stride,
                   expected_stride);
        }
        src_rgba = s_rgba_contiguous.data();
    }

    // 2. Fused Upright Rotation via ncnn::kanna_rotate_c4
    int cur_w = width;
    int cur_h = height;
    if (rotation_degrees == 90) {
        cur_w = height;
        cur_h = width;
        s_rotated_rgba.resize(cur_w * cur_h * 4);
        ncnn::kanna_rotate_c4(src_rgba, width, height, s_rotated_rgba.data(), cur_w, cur_h, 1);
        src_rgba = s_rotated_rgba.data();
    } else if (rotation_degrees == 180) {
        s_rotated_rgba.resize(cur_w * cur_h * 4);
        ncnn::kanna_rotate_c4(src_rgba, width, height, s_rotated_rgba.data(), cur_w, cur_h, 2);
        src_rgba = s_rotated_rgba.data();
    } else if (rotation_degrees == 270) {
        cur_w = height;
        cur_h = width;
        s_rotated_rgba.resize(cur_w * cur_h * 4);
        ncnn::kanna_rotate_c4(src_rgba, width, height, s_rotated_rgba.data(), cur_w, cur_h, 3);
        src_rgba = s_rotated_rgba.data();
    }

    shelfpulse::ImageFrame frame;
    frame.data = src_rgba;
    frame.width = cur_w;
    frame.height = cur_h;
    frame.channels = 4; // RGBA_8888
    frame.step = cur_w * 4;
    frame.rotation_degrees = 0;

    auto prep_end = std::chrono::high_resolution_clock::now();
    float native_prep_ms = std::chrono::duration<float, std::milli>(prep_end - prep_start).count();

    std::vector<shelfpulse::Detection> detections = g_detector->detect(
        frame, conf_thresh, nms_thresh, target_size, &timings
    );
    timings.preprocess_ms += native_prep_ms;

    if (enable_tracking && g_tracker) {
        auto trk_start = std::chrono::high_resolution_clock::now();
        detections = g_tracker->update(detections);
        auto trk_end = std::chrono::high_resolution_clock::now();
        timings.track_ms = std::chrono::duration<float, std::milli>(trk_end - trk_start).count();
    }

    auto total_end = std::chrono::high_resolution_clock::now();
    timings.total_ms = std::chrono::duration<float, std::milli>(total_end - total_start).count();

    LOGI("[ShelfPulseNative] Stages: Pre=%.2fms, Inf=%.2fms, Trk=%.2fms, Total=%.2fms (GPU: %d, Dets: %zu)",
         timings.preprocess_ms, timings.infer_ms, timings.track_ms, timings.total_ms,
         g_detector->is_gpu_active() ? 1 : 0, detections.size());

    return pack_detections_to_jfloat_array(env, detections, timings, g_detector->is_gpu_active());
}

JNIEXPORT jfloatArray JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeDetectBitmap(
    JNIEnv* env,
    jobject /* thiz */,
    jobject bitmap,
    jfloat conf_thresh,
    jfloat nms_thresh,
    jboolean enable_tracking) {

    auto start_time = std::chrono::high_resolution_clock::now();

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    shelfpulse::StageTimings timings;
    if (!g_detector) {
        LOGE("nativeDetectBitmap called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get AndroidBitmapInfo");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    shelfpulse::ImageFrame frame;
    frame.data = static_cast<const uint8_t*>(pixels);
    frame.width = static_cast<int>(info.width);
    frame.height = static_cast<int>(info.height);
    frame.channels = 4; // RGBA_8888
    frame.step = static_cast<int>(info.stride);
    frame.rotation_degrees = 0;

    std::vector<shelfpulse::Detection> detections = g_detector->detect(
        frame, conf_thresh, nms_thresh, 640, &timings
    );

    if (enable_tracking && g_tracker) {
        auto trk_start = std::chrono::high_resolution_clock::now();
        detections = g_tracker->update(detections);
        auto trk_end = std::chrono::high_resolution_clock::now();
        timings.track_ms = std::chrono::duration<float, std::milli>(trk_end - trk_start).count();
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    timings.total_ms = std::chrono::duration<float, std::milli>(end_time - start_time).count();

    AndroidBitmap_unlockPixels(env, bitmap);

    return pack_detections_to_jfloat_array(env, detections, timings, g_detector->is_gpu_active());
}

JNIEXPORT jfloatArray JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeDetectBitmapTiled(
    JNIEnv* env,
    jobject /* thiz */,
    jobject bitmap,
    jfloat conf_thresh,
    jfloat nms_thresh,
    jfloat overlap_ratio) {

    auto start_time = std::chrono::high_resolution_clock::now();

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    shelfpulse::StageTimings timings;
    if (!g_detector) {
        LOGE("nativeDetectBitmapTiled called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get AndroidBitmapInfo in nativeDetectBitmapTiled");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels in nativeDetectBitmapTiled");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    shelfpulse::ImageFrame frame;
    frame.data = static_cast<const uint8_t*>(pixels);
    frame.width = static_cast<int>(info.width);
    frame.height = static_cast<int>(info.height);
    frame.channels = 4; // RGBA_8888
    frame.step = static_cast<int>(info.stride);
    frame.rotation_degrees = 0;

    std::vector<shelfpulse::Detection> detections = g_detector->detect_tiled(
        frame, conf_thresh, nms_thresh, overlap_ratio, &timings
    );

    auto end_time = std::chrono::high_resolution_clock::now();
    timings.total_ms = std::chrono::duration<float, std::milli>(end_time - start_time).count();

    AndroidBitmap_unlockPixels(env, bitmap);

    return pack_detections_to_jfloat_array(env, detections, timings, g_detector->is_gpu_active());
}

JNIEXPORT jfloatArray JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeDetectYuv(
    JNIEnv* env,
    jobject /* thiz */,
    jobject y_buffer,
    jobject u_buffer,
    jobject v_buffer,
    jint width,
    jint height,
    jint y_row_stride,
    jint uv_row_stride,
    jint uv_pixel_stride,
    jint rotation_degrees,
    jfloat conf_thresh,
    jfloat nms_thresh,
    jboolean enable_tracking) {

    auto start_time = std::chrono::high_resolution_clock::now();

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    shelfpulse::StageTimings timings;
    if (!g_detector) {
        LOGE("nativeDetectYuv called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    auto* y_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(y_buffer));
    auto* u_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(u_buffer));
    auto* v_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(v_buffer));

    if (!y_ptr || !u_ptr || !v_ptr) {
        LOGE("DirectBuffer address is null");
        return pack_detections_to_jfloat_array(env, {}, timings, false);
    }

    auto prep_start = std::chrono::high_resolution_clock::now();

    // Assemble contiguous NV21 buffer using reusable member
    int y_size = width * height;
    s_nv21_buffer.resize(y_size + y_size / 2);

    // Copy Y plane
    for (int r = 0; r < height; r++) {
        memcpy(s_nv21_buffer.data() + r * width, y_ptr + r * y_row_stride, width);
    }

    // Interleave UV into NV21 (V then U)
    uint8_t* uv_dst = s_nv21_buffer.data() + y_size;
    for (int r = 0; r < height / 2; r++) {
        const uint8_t* u_row = u_ptr + r * uv_row_stride;
        const uint8_t* v_row = v_ptr + r * uv_row_stride;
        for (int c = 0; c < width / 2; c++) {
            *uv_dst++ = v_row[c * uv_pixel_stride];
            *uv_dst++ = u_row[c * uv_pixel_stride];
        }
    }

    // Convert NV21 to RGB
    cv::Mat yuv_mat(height + height / 2, width, CV_8UC1, s_nv21_buffer.data());
    cv::Mat rgb_mat;
    cv::cvtColor(yuv_mat, rgb_mat, cv::COLOR_YUV2RGB_NV21);

    // Apply upright rotation if required
    cv::Mat oriented_mat;
    if (rotation_degrees == 90) {
        cv::rotate(rgb_mat, oriented_mat, cv::ROTATE_90_CLOCKWISE);
    } else if (rotation_degrees == 180) {
        cv::rotate(rgb_mat, oriented_mat, cv::ROTATE_180);
    } else if (rotation_degrees == 270) {
        cv::rotate(rgb_mat, oriented_mat, cv::ROTATE_90_COUNTERCLOCKWISE);
    } else {
        oriented_mat = rgb_mat;
    }

    shelfpulse::ImageFrame frame;
    frame.data = oriented_mat.data;
    frame.width = oriented_mat.cols;
    frame.height = oriented_mat.rows;
    frame.channels = 3;
    frame.step = static_cast<int>(oriented_mat.step);
    frame.rotation_degrees = 0;

    auto prep_end = std::chrono::high_resolution_clock::now();
    float native_prep_ms = std::chrono::duration<float, std::milli>(prep_end - prep_start).count();

    std::vector<shelfpulse::Detection> detections = g_detector->detect(
        frame, conf_thresh, nms_thresh, -1, &timings
    );
    timings.preprocess_ms += native_prep_ms;

    if (enable_tracking && g_tracker) {
        auto trk_start = std::chrono::high_resolution_clock::now();
        detections = g_tracker->update(detections);
        auto trk_end = std::chrono::high_resolution_clock::now();
        timings.track_ms = std::chrono::duration<float, std::milli>(trk_end - trk_start).count();
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    timings.total_ms = std::chrono::duration<float, std::milli>(end_time - start_time).count();

    return pack_detections_to_jfloat_array(env, detections, timings, g_detector->is_gpu_active());
}

JNIEXPORT void JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeResetTracker(
    JNIEnv* /* env */,
    jobject /* thiz */) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_tracker) {
        g_tracker->reset();
        LOGI("ByteTrack tracker reset.");
    }
}

JNIEXPORT void JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    g_detector.reset();
    g_tracker.reset();
    LOGI("Native vision engine released.");
}

} // extern "C"
