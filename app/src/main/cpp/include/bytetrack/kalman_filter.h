#ifndef SHELFPULSE_KALMAN_FILTER_H
#define SHELFPULSE_KALMAN_FILTER_H

#include <Eigen/Dense>

namespace shelfpulse {
namespace byte_kalman {

typedef Eigen::Matrix<float, 1, 4, Eigen::RowMajor> DETECTBOX;
typedef Eigen::Matrix<float, -1, 4, Eigen::RowMajor> DETECTBOXSS;

class KalmanFilter {
public:
    static const double chi2inv95[10];
    KalmanFilter();

    std::pair<Eigen::Matrix<float, 1, 8, Eigen::RowMajor>, Eigen::Matrix<float, 8, 8, Eigen::RowMajor>>
    initiate(const DETECTBOX& measurement);

    void predict(Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
                 Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance);

    std::pair<Eigen::Matrix<float, 1, 4, Eigen::RowMajor>, Eigen::Matrix<float, 4, 4, Eigen::RowMajor>>
    project(const Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
            const Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance);

    std::pair<Eigen::Matrix<float, 1, 8, Eigen::RowMajor>, Eigen::Matrix<float, 8, 8, Eigen::RowMajor>>
    update(const Eigen::Matrix<float, 1, 8, Eigen::RowMajor>& mean,
           const Eigen::Matrix<float, 8, 8, Eigen::RowMajor>& covariance,
           const DETECTBOX& measurement);

private:
    Eigen::Matrix<float, 8, 8, Eigen::RowMajor> _motion_mat;
    Eigen::Matrix<float, 4, 8, Eigen::RowMajor> _update_mat;
    float _std_weight_position;
    float _std_weight_velocity;
};

} // namespace byte_kalman
} // namespace shelfpulse

#endif // SHELFPULSE_KALMAN_FILTER_H
