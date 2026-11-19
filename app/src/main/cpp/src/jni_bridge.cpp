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

static jfloatArray pack_detections_to_jfloat_array(
    JNIEnv* env,
    const std::vector<shelfpulse::Detection>& detections,
    float latency_ms,
    bool is_gpu) {

    // Output packet format:
    // [0]: detection count (N)
    // [1]: latency in milliseconds
    // [2]: is_gpu flag (1.0f or 0.0f)
    // Then for each of N detections (7 floats per detection):
    // [3 + i*7 + 0]: x1
    // [3 + i*7 + 1]: y1
    // [3 + i*7 + 2]: x2
    // [3 + i*7 + 3]: y2
    // [3 + i*7 + 4]: score
    // [3 + i*7 + 5]: class_id
    // [3 + i*7 + 6]: track_id

    size_t num_dets = detections.size();
    size_t total_floats = 3 + num_dets * 7;

    std::vector<float> buffer(total_floats);
    buffer[0] = static_cast<float>(num_dets);
    buffer[1] = latency_ms;
    buffer[2] = is_gpu ? 1.0f : 0.0f;

    for (size_t i = 0; i < num_dets; i++) {
        size_t offset = 3 + i * 7;
        buffer[offset + 0] = detections[i].x1;
        buffer[offset + 1] = detections[i].y1;
        buffer[offset + 2] = detections[i].x2;
        buffer[offset + 3] = detections[i].y2;
        buffer[offset + 4] = detections[i].score;
        buffer[offset + 5] = static_cast<float>(detections[i].class_id);
        buffer[offset + 6] = static_cast<float>(detections[i].track_id);
    }

    jfloatArray result = env->NewFloatArray(static_cast<jsize>(total_floats));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(total_floats), buffer.data());
    }
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_shelfpulse_ai_nativebridge_ShelfPulseNativeEngine_nativeDetectBitmap(
    JNIEnv* env,
    jobject /* thiz */,
    jobject bitmap,
    jfloat conf_thresh,
    jfloat nms_thresh,
    jboolean enable_tracking) {

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_detector) {
        LOGE("nativeDetectBitmap called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, 0.0f, false);
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get AndroidBitmapInfo");
        return pack_detections_to_jfloat_array(env, {}, 0.0f, false);
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return pack_detections_to_jfloat_array(env, {}, 0.0f, false);
    }

    auto start_time = std::chrono::high_resolution_clock::now();

    shelfpulse::ImageFrame frame;
    frame.data = static_cast<const uint8_t*>(pixels);
    frame.width = static_cast<int>(info.width);
    frame.height = static_cast<int>(info.height);
    frame.channels = 4; // RGBA_8888
    frame.step = static_cast<int>(info.stride);
    frame.rotation_degrees = 0;

    std::vector<shelfpulse::Detection> detections = g_detector->detect(frame, conf_thresh, nms_thresh);

    if (enable_tracking && g_tracker) {
        detections = g_tracker->update(detections);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    float latency_ms = std::chrono::duration<float, std::milli>(end_time - start_time).count();

    AndroidBitmap_unlockPixels(env, bitmap);

    return pack_detections_to_jfloat_array(env, detections, latency_ms, g_detector->is_gpu_active());
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

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_detector) {
        LOGE("nativeDetectYuv called before nativeInit()");
        return pack_detections_to_jfloat_array(env, {}, 0.0f, false);
    }

    auto* y_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(y_buffer));
    auto* u_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(u_buffer));
    auto* v_ptr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(v_buffer));

    if (!y_ptr || !u_ptr || !v_ptr) {
        LOGE("DirectBuffer address is null");
        return pack_detections_to_jfloat_array(env, {}, 0.0f, false);
    }

    auto start_time = std::chrono::high_resolution_clock::now();

    // Assemble contiguous NV21 buffer handling row strides
    int y_size = width * height;
    std::vector<uint8_t> nv21(y_size + y_size / 2);

    // Copy Y plane
    for (int r = 0; r < height; r++) {
        memcpy(nv21.data() + r * width, y_ptr + r * y_row_stride, width);
    }

    // Interleave UV into NV21 (V then U)
    uint8_t* uv_dst = nv21.data() + y_size;
    for (int r = 0; r < height / 2; r++) {
        const uint8_t* u_row = u_ptr + r * uv_row_stride;
        const uint8_t* v_row = v_ptr + r * uv_row_stride;
        for (int c = 0; c < width / 2; c++) {
            *uv_dst++ = v_row[c * uv_pixel_stride];
            *uv_dst++ = u_row[c * uv_pixel_stride];
        }
    }

    // Convert NV21 to RGB
    cv::Mat yuv_mat(height + height / 2, width, CV_8UC1, nv21.data());
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

    std::vector<shelfpulse::Detection> detections = g_detector->detect(frame, conf_thresh, nms_thresh);

    if (enable_tracking && g_tracker) {
        detections = g_tracker->update(detections);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    float latency_ms = std::chrono::duration<float, std::milli>(end_time - start_time).count();

    return pack_detections_to_jfloat_array(env, detections, latency_ms, g_detector->is_gpu_active());
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
