#ifndef SHELFPULSE_BYTETRACK_WRAPPER_H
#define SHELFPULSE_BYTETRACK_WRAPPER_H

#include "interfaces.h"
#include "bytetrack/bytetrack.h"
#include <mutex>
#include <memory>

namespace shelfpulse {

class ByteTrackWrapper : public ITracker {
public:
    ByteTrackWrapper(int frame_rate = 30, int track_buffer = 60);
    ~ByteTrackWrapper() override = default;

    std::vector<Detection> update(const std::vector<Detection>& detections) override;
    void reset() override;

private:
    std::unique_ptr<BYTETracker> tracker_;
    mutable std::mutex tracker_mutex_;
};

} // namespace shelfpulse

#endif // SHELFPULSE_BYTETRACK_WRAPPER_H
