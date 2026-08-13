#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <new>

namespace {

constexpr float kMinimumPressure = 0.05F;
// Preserve the device trajectory at every zoom. A normalized threshold grows to many physical
// pixels when the canvas is magnified and visibly cuts corners. Only collapse sub-pixel duplicates.
constexpr float kMinimumDistancePx = 0.25F;
constexpr float kVelocityFullScalePxPerMs = 2.0F;
constexpr float kVelocitySmoothing = 0.65F;
constexpr jint kIncreaseWhenSlow = 0;
constexpr float kPi = 3.14159265358979323846F;
constexpr float kFullTurn = kPi * 2.0F;
constexpr std::size_t kInputStride = 32;
constexpr std::size_t kOutputStride = 40;
constexpr std::uint32_t kInputFlagForce = 1U;
constexpr std::size_t kStabilizationHistory = 32;

struct StrokeInputSession {
    float x = 0.0F;
    float y = 0.0F;
    float pressure = 1.0F;
    std::int64_t time_ms = 0;
    float tilt = 0.0F;
    float orientation = 0.0F;
    float velocity = 0.0F;
    float raw_x = 0.0F;
    float raw_y = 0.0F;
    std::int64_t raw_time_ms = 0;
    float motion_x = 0.0F;
    float motion_y = 0.0F;
    float motion_velocity_x = 0.0F;
    float motion_velocity_y = 0.0F;
    std::array<float, kStabilizationHistory> stabilization_x{};
    std::array<float, kStabilizationHistory> stabilization_y{};
    std::size_t stabilization_cursor = 0;
    std::size_t stabilization_count = 0;
    bool has_sample = false;
    bool has_raw_sample = false;

    void reset() noexcept {
        x = 0.0F;
        y = 0.0F;
        pressure = 1.0F;
        time_ms = 0;
        tilt = 0.0F;
        orientation = 0.0F;
        velocity = 0.0F;
        raw_x = 0.0F;
        raw_y = 0.0F;
        raw_time_ms = 0;
        motion_x = 0.0F;
        motion_y = 0.0F;
        motion_velocity_x = 0.0F;
        motion_velocity_y = 0.0F;
        stabilization_x.fill(0.0F);
        stabilization_y.fill(0.0F);
        stabilization_cursor = 0;
        stabilization_count = 0;
        has_sample = false;
        has_raw_sample = false;
    }
};

float clamp01(float value) noexcept {
    return std::clamp(std::isfinite(value) ? value : 0.0F, 0.0F, 1.0F);
}

float unwrap_orientation(float value, float previous, bool has_previous) noexcept {
    if (!std::isfinite(value)) return has_previous ? previous : 0.0F;
    if (!has_previous) return value;
    while (value - previous > kPi) value -= kFullTurn;
    while (value - previous < -kPi) value += kFullTurn;
    return value;
}

float normalized_velocity(
    float delta_x,
    float delta_y,
    jint viewport_width,
    jint viewport_height,
    std::int64_t elapsed_ms
) noexcept {
    const float width = static_cast<float>(std::max(viewport_width, 1));
    const float height = static_cast<float>(std::max(viewport_height, 1));
    const float distance = std::hypot(delta_x * width, delta_y * height);
    const float elapsed = static_cast<float>(std::max<std::int64_t>(elapsed_ms, 1));
    return std::clamp(distance / elapsed / kVelocityFullScalePxPerMs, 0.0F, 1.0F);
}

float smoothing(
    float base,
    bool adjust_by_speed,
    jint mode,
    float velocity
) noexcept {
    const float value = std::clamp(std::isfinite(base) ? base : 0.0F, 0.0F, 0.9F);
    if (!adjust_by_speed || value <= 0.0F) return value;
    const float speed = clamp01(velocity);
    if (mode == kIncreaseWhenSlow) {
        return std::clamp(value + (1.0F - speed) * value * 0.6F, 0.0F, 0.9F);
    }
    return std::clamp(value * (1.0F - speed * 0.75F), 0.0F, 0.9F);
}

void motion_filter(
    StrokeInputSession& session,
    float raw_x,
    float raw_y,
    float amount,
    float expression,
    float& filtered_x,
    float& filtered_y
) noexcept {
    const float strength = clamp01(amount);
    if (!session.has_raw_sample || strength <= 0.0F) {
        filtered_x = raw_x;
        filtered_y = raw_y;
        session.motion_x = raw_x;
        session.motion_y = raw_y;
        return;
    }
    const float delta_x = raw_x - session.raw_x;
    const float delta_y = raw_y - session.raw_y;
    session.motion_velocity_x = session.motion_velocity_x * 0.70F + delta_x * 0.30F;
    session.motion_velocity_y = session.motion_velocity_y * 0.70F + delta_y * 0.30F;
    const float predicted_x = session.motion_x + session.motion_velocity_x;
    const float predicted_y = session.motion_y + session.motion_velocity_y;
    // Expression restores raw movement, but its influence diminishes as Amount approaches 100%.
    const float effective = strength * (1.0F - clamp01(expression) * (1.0F - strength));
    filtered_x = raw_x * (1.0F - effective) + predicted_x * effective;
    filtered_y = raw_y * (1.0F - effective) + predicted_y * effective;
    session.motion_x = filtered_x;
    session.motion_y = filtered_y;
}

void moving_average_stabilization(
    StrokeInputSession& session,
    float input_x,
    float input_y,
    float amount,
    float velocity,
    float& output_x,
    float& output_y
) noexcept {
    session.stabilization_x[session.stabilization_cursor] = input_x;
    session.stabilization_y[session.stabilization_cursor] = input_y;
    session.stabilization_cursor = (session.stabilization_cursor + 1U) % kStabilizationHistory;
    session.stabilization_count = std::min(session.stabilization_count + 1U, kStabilizationHistory);
    const float strength = clamp01(amount);
    if (strength <= 0.0F || session.stabilization_count <= 1U) {
        output_x = input_x;
        output_y = input_y;
        return;
    }
    // Procreate documents that Stabilization uses a moving average and becomes stronger at speed.
    const float speed_weight = 0.25F + clamp01(velocity) * 0.75F;
    const std::size_t requested = 1U + static_cast<std::size_t>(std::lround(strength * speed_weight * 23.0F));
    const std::size_t window = std::clamp<std::size_t>(requested, 1U, session.stabilization_count);
    float sum_x = 0.0F;
    float sum_y = 0.0F;
    for (std::size_t offset = 0; offset < window; ++offset) {
        const std::size_t index = (session.stabilization_cursor + kStabilizationHistory - 1U - offset) % kStabilizationHistory;
        sum_x += session.stabilization_x[index];
        sum_y += session.stabilization_y[index];
    }
    output_x = sum_x / static_cast<float>(window);
    output_y = sum_y / static_cast<float>(window);
}

template <typename T>
void write_value(std::byte* output, std::size_t offset, const T& value) noexcept {
    std::memcpy(output + offset, &value, sizeof(T));
}

template <typename T>
T read_value(const std::byte* input, std::size_t offset) noexcept {
    T value{};
    std::memcpy(&value, input + offset, sizeof(T));
    return value;
}

void write_output(std::byte* output, std::size_t offset, const StrokeInputSession& session) noexcept {
    write_value(output, offset + 0, session.x);
    write_value(output, offset + 4, session.y);
    write_value(output, offset + 8, session.pressure);
    write_value(output, offset + 16, session.time_ms);
    write_value(output, offset + 24, session.tilt);
    write_value(output, offset + 28, session.orientation);
    write_value(output, offset + 32, session.velocity);
}

StrokeInputSession* from_handle(jlong handle) noexcept {
    return reinterpret_cast<StrokeInputSession*>(static_cast<std::uintptr_t>(handle));
}

bool append_sample(
    StrokeInputSession& session,
    float raw_x,
    float raw_y,
    float adjusted_pressure,
    std::int64_t sample_time_ms,
    float sample_tilt,
    float sample_orientation,
    jint viewport_width,
    jint viewport_height,
    float stabilization,
    bool stabilization_by_speed,
    jint stabilization_by_speed_mode,
    float stream_line,
    float stream_line_pressure,
    float procreate_stabilization,
    float motion_filtering,
    float motion_filtering_expression,
    bool force
) noexcept {
    const std::int64_t normalized_time_ms = session.has_raw_sample
        ? std::max(sample_time_ms, session.raw_time_ms + 1)
        : std::max<std::int64_t>(sample_time_ms, 0);
    const float normalized_x = clamp01(raw_x);
    const float normalized_y = clamp01(raw_y);
    const std::int64_t elapsed = session.has_raw_sample
        ? normalized_time_ms - session.raw_time_ms
        : 1;
    const float input_velocity = session.has_raw_sample
        ? normalized_velocity(
            normalized_x - session.raw_x,
            normalized_y - session.raw_y,
            viewport_width,
            viewport_height,
            elapsed
        )
        : 0.0F;
    const float filter = smoothing(
        stabilization,
        stabilization_by_speed,
        stabilization_by_speed_mode,
        input_velocity
    );
    float motion_x = normalized_x;
    float motion_y = normalized_y;
    motion_filter(session, normalized_x, normalized_y, motion_filtering, motion_filtering_expression, motion_x, motion_y);
    float averaged_x = motion_x;
    float averaged_y = motion_y;
    moving_average_stabilization(session, motion_x, motion_y, procreate_stabilization, input_velocity, averaged_x, averaged_y);
    const float stream_filter = clamp01(stream_line) * 0.90F;
    const float combined_filter = 1.0F - (1.0F - filter) * (1.0F - stream_filter);
    const float modeled_x = force
        ? normalized_x
        : session.has_sample
        ? session.x * combined_filter + averaged_x * (1.0F - combined_filter)
        : averaged_x;
    const float modeled_y = force
        ? normalized_y
        : session.has_sample
        ? session.y * combined_filter + averaged_y * (1.0F - combined_filter)
        : averaged_y;
    session.raw_x = normalized_x;
    session.raw_y = normalized_y;
    session.raw_time_ms = normalized_time_ms;
    session.has_raw_sample = true;
    const float modeled_distance_px = std::hypot(
        (modeled_x - session.x) * static_cast<float>(std::max(viewport_width, 1)),
        (modeled_y - session.y) * static_cast<float>(std::max(viewport_height, 1))
    );
    if (!force && session.has_sample && modeled_distance_px <= kMinimumDistancePx) {
        return false;
    }

    const float raw_velocity = session.has_sample
        ? normalized_velocity(
            modeled_x - session.x,
            modeled_y - session.y,
            viewport_width,
            viewport_height,
            elapsed
        )
        : 0.0F;
    const float modeled_velocity = session.has_sample
        ? session.velocity * kVelocitySmoothing + raw_velocity * (1.0F - kVelocitySmoothing)
        : raw_velocity;
    session.x = clamp01(modeled_x);
    session.y = clamp01(modeled_y);
    const float raw_pressure = std::clamp(std::isfinite(adjusted_pressure) ? adjusted_pressure : kMinimumPressure, kMinimumPressure, 1.0F);
    const float pressure_filter = clamp01(stream_line_pressure) * 0.90F;
    session.pressure = force || !session.has_sample ? raw_pressure : session.pressure * pressure_filter + raw_pressure * (1.0F - pressure_filter);
    session.time_ms = normalized_time_ms;
    session.tilt = std::isfinite(sample_tilt) ? sample_tilt : 0.0F;
    session.orientation = unwrap_orientation(sample_orientation, session.orientation, session.has_sample);
    session.velocity = modeled_velocity;
    session.has_sample = true;
    return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_inkloom_engine_v3_input_NativeStrokeSession_nativeCreate(JNIEnv*, jobject) {
    auto* session = new (std::nothrow) StrokeInputSession();
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(session));
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v3_input_NativeStrokeSession_nativeDestroy(
    JNIEnv*, jobject, jlong handle
) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v3_input_NativeStrokeSession_nativeReset(
    JNIEnv*, jobject, jlong handle
) {
    if (auto* session = from_handle(handle); session != nullptr) session->reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v3_input_NativeStrokeSession_nativeAppendBatch(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject input_buffer,
    jint sample_count,
    jint viewport_width,
    jint viewport_height,
    jfloat stabilization,
    jboolean stabilization_by_speed,
    jint stabilization_by_speed_mode,
    jfloat stream_line,
    jfloat stream_line_pressure,
    jfloat procreate_stabilization,
    jfloat motion_filtering,
    jfloat motion_filtering_expression,
    jobject output_buffer
) {
    auto* session = from_handle(handle);
    if (session == nullptr || sample_count <= 0) return 0;
    const auto* input = static_cast<const std::byte*>(env->GetDirectBufferAddress(input_buffer));
    auto* output = static_cast<std::byte*>(env->GetDirectBufferAddress(output_buffer));
    const jlong input_capacity = env->GetDirectBufferCapacity(input_buffer);
    const jlong output_capacity = env->GetDirectBufferCapacity(output_buffer);
    if (input == nullptr || output == nullptr ||
        input_capacity < static_cast<jlong>(sample_count) * static_cast<jlong>(kInputStride) ||
        output_capacity < static_cast<jlong>(sample_count) * static_cast<jlong>(kOutputStride)) {
        return 0;
    }

    jint accepted_count = 0;
    for (jint index = 0; index < sample_count; ++index) {
        const std::size_t offset = static_cast<std::size_t>(index) * kInputStride;
        const bool accepted = append_sample(
            *session,
            read_value<float>(input, offset + 0),
            read_value<float>(input, offset + 4),
            read_value<float>(input, offset + 8),
            read_value<std::int64_t>(input, offset + 16),
            read_value<float>(input, offset + 24),
            read_value<float>(input, offset + 28),
            viewport_width,
            viewport_height,
            stabilization,
            stabilization_by_speed == JNI_TRUE,
            stabilization_by_speed_mode,
            stream_line,
            stream_line_pressure,
            procreate_stabilization,
            motion_filtering,
            motion_filtering_expression,
            (read_value<std::uint32_t>(input, offset + 12) & kInputFlagForce) != 0U
        );
        if (!accepted) continue;
        write_output(output, static_cast<std::size_t>(accepted_count) * kOutputStride, *session);
        ++accepted_count;
    }
    return accepted_count;
}
