#include "bytetrack_wrapper.h"
#include <iostream>
#include <cassert>
#include <vector>
#include <cmath>

using namespace shelfpulse;

void test_bytetrack_tracking() {
    std::cout << "=== Running ByteTrack Native Regression Tests ===" << std::endl;

    ByteTrackWrapper tracker(30, 60);

    // Frame 1: 5 synthetic retail items on shelf (normalized [0..1] coordinates)
    // Box 2 is a small product (w=0.03, h=0.04) to specifically test small item retention
    std::vector<Detection> frame1 = {
        Detection(0.10f, 0.20f, 0.18f, 0.35f, 0.85f, 0),
        Detection(0.25f, 0.20f, 0.33f, 0.35f, 0.80f, 0),
        Detection(0.40f, 0.22f, 0.43f, 0.26f, 0.75f, 0), // small box: 0.03 x 0.04
        Detection(0.55f, 0.20f, 0.65f, 0.38f, 0.90f, 0),
        Detection(0.70f, 0.20f, 0.82f, 0.38f, 0.88f, 0)
    };

    auto tracked1 = tracker.update(frame1);
    std::cout << "[Frame 1] Input: " << frame1.size() << " | Tracked: " << tracked1.size() << std::endl;
    
    if (tracked1.size() != frame1.size()) {
        std::cerr << "FAIL: Expected " << frame1.size() << " tracks on Frame 1, got " << tracked1.size() << std::endl;
        std::exit(1);
    }

    std::vector<int> initial_track_ids;
    for (size_t i = 0; i < tracked1.size(); ++i) {
        const auto& tr = tracked1[i];
        if (tr.track_id < 1) {
            std::cerr << "FAIL: Track " << i << " has invalid track_id: " << tr.track_id << std::endl;
            std::exit(1);
        }
        if (tr.x1 < 0.0f || tr.x2 > 1.0f || tr.y1 < 0.0f || tr.y2 > 1.0f) {
            std::cerr << "FAIL: Track " << i << " out of bounds: [" 
                      << tr.x1 << ", " << tr.y1 << ", " << tr.x2 << ", " << tr.y2 << "]" << std::endl;
            std::exit(1);
        }
        initial_track_ids.push_back(tr.track_id);
    }

    // Frame 2: same 5 products with camera slight pan (+0.005 in x)
    std::vector<Detection> frame2 = {
        Detection(0.105f, 0.20f, 0.185f, 0.35f, 0.86f, 0),
        Detection(0.255f, 0.20f, 0.335f, 0.35f, 0.82f, 0),
        Detection(0.405f, 0.22f, 0.435f, 0.26f, 0.77f, 0),
        Detection(0.555f, 0.20f, 0.655f, 0.38f, 0.89f, 0),
        Detection(0.705f, 0.20f, 0.825f, 0.38f, 0.87f, 0)
    };

    auto tracked2 = tracker.update(frame2);
    std::cout << "[Frame 2] Input: " << frame2.size() << " | Tracked: " << tracked2.size() << std::endl;

    if (tracked2.size() != frame2.size()) {
        std::cerr << "FAIL: Expected " << frame2.size() << " tracks on Frame 2, got " << tracked2.size() << std::endl;
        std::exit(1);
    }

    for (const auto& tr : tracked2) {
        if (tr.track_id < 1) {
            std::cerr << "FAIL: Track in Frame 2 has invalid track_id: " << tr.track_id << std::endl;
            std::exit(1);
        }
    }

    // Frame 3: same 5 products with another slight shift (+0.005 in x)
    std::vector<Detection> frame3 = {
        Detection(0.110f, 0.20f, 0.190f, 0.35f, 0.84f, 0),
        Detection(0.260f, 0.20f, 0.340f, 0.35f, 0.81f, 0),
        Detection(0.410f, 0.22f, 0.440f, 0.26f, 0.76f, 0),
        Detection(0.560f, 0.20f, 0.660f, 0.38f, 0.91f, 0),
        Detection(0.710f, 0.20f, 0.830f, 0.38f, 0.86f, 0)
    };

    auto tracked3 = tracker.update(frame3);
    std::cout << "[Frame 3] Input: " << frame3.size() << " | Tracked: " << tracked3.size() << std::endl;

    if (tracked3.size() != frame3.size()) {
        std::cerr << "FAIL: Expected " << frame3.size() << " tracks on Frame 3, got " << tracked3.size() << std::endl;
        std::exit(1);
    }

    // Test Tracker Reset
    tracker.reset();
    auto tracked_after_reset = tracker.update(frame1);
    if (tracked_after_reset.size() != frame1.size()) {
        std::cerr << "FAIL: Expected " << frame1.size() << " tracks after reset, got " 
                  << tracked_after_reset.size() << std::endl;
        std::exit(1);
    }

    std::cout << "SUCCESS: All ByteTrack regression tests passed 100%!" << std::endl;
}

int main() {
    test_bytetrack_tracking();
    return 0;
}
