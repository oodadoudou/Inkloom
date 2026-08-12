#include <jni.h>

#include <algorithm>
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

struct StrokeInputSession {
    float x = 0.0F;
    float y = 0.0F;
    float pressure = 1.0F;
    std::int64_t time_ms = 0;
    float tilt = 0.0F;
    float orientation = 0.0F;
    float velocity = 0.0F;
    bool has_sample = false;

    void reset() noexcept {
        x = 0.0F;
        y = 0.0F;
        pressure = 1.0F;
        time_ms = 0;
        tilt = 0.0F;
        orientation = 0.0F;
        velocity = 0.0F;
        has_sample = false;
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
    bool force
) noexcept {
    const std::int64_t normalized_time_ms = session.has_sample
        ? std::max(sample_time_ms, session.time_ms + 1)
        : std::max<std::int64_t>(sample_time_ms, 0);
    const float normalized_x = clamp01(raw_x);
    const float normalized_y = clamp01(raw_y);
    const std::int64_t elapsed = session.has_sample
        ? normalized_time_ms - session.time_ms
        : 1;
    const float input_velocity = session.has_sample
        ? normalized_velocity(
            normalized_x - session.x,
            normalized_y - session.y,
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
    const float modeled_x = force
        ? normalized_x
        : session.has_sample
        ? session.x * filter + normalized_x * (1.0F - filter)
        : normalized_x;
    const float modeled_y = force
        ? normalized_y
        : session.has_sample
        ? session.y * filter + normalized_y * (1.0F - filter)
        : normalized_y;
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
    session.pressure = std::clamp(
        std::isfinite(adjusted_pressure) ? adjusted_pressure : kMinimumPressure,
        kMinimumPressure,
        1.0F
    );
    session.time_ms = normalized_time_ms;
    session.tilt = std::isfinite(sample_tilt) ? sample_tilt : 0.0F;
    session.orientation = unwrap_orientation(sample_orientation, session.orientation, session.has_sample);
    session.velocity = modeled_velocity;
    session.has_sample = true;
    return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeCreate(JNIEnv*, jobject) {
    auto* session = new (std::nothrow) StrokeInputSession();
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(session));
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeDestroy(
    JNIEnv*, jobject, jlong handle
) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeReset(
    JNIEnv*, jobject, jlong handle
) {
    if (auto* session = from_handle(handle); session != nullptr) session->reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeAppendBatch(
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
            (read_value<std::uint32_t>(input, offset + 12) & kInputFlagForce) != 0U
        );
        if (!accepted) continue;
        write_output(output, static_cast<std::size_t>(accepted_count) * kOutputStride, *session);
        ++accepted_count;
    }
    return accepted_count;
}
